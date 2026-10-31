package tunnel

import (
	"bufio"
	"crypto/sha1"
	"encoding/base64"
	"encoding/binary"
	"errors"
	"io"
	"net"
	"sync"
)

const wsGUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

const (
	OpcodeContinuation = 0x0
	OpcodeText         = 0x1
	OpcodeBinary       = 0x2
	OpcodeClose        = 0x8
	OpcodePing         = 0x9
	OpcodePong         = 0xA
)

const maxWSFramePayload = 8 * 1024 * 1024 // 8 MiB safety cap

type WSConn struct {
	conn      net.Conn
	reader    *bufio.Reader
	writeMu   sync.Mutex
	closed    bool
}

func NewWSConn(conn net.Conn, rw *bufio.ReadWriter) *WSConn {
	var r *bufio.Reader
	if rw != nil && rw.Reader != nil {
		r = rw.Reader
	} else {
		r = bufio.NewReaderSize(conn, 32*1024)
	}
	return &WSConn{conn: conn, reader: r}
}

func ComputeAcceptKey(secWebSocketKey string) string {
	sum := sha1.Sum([]byte(secWebSocketKey + wsGUID))
	return base64.StdEncoding.EncodeToString(sum[:])
}

func (w *WSConn) ReadMessage() (byte, []byte, error) {
	var msgOpcode byte
	var fragments []byte

	for {
		var hdr [2]byte
		if _, err := io.ReadFull(w.reader, hdr[:]); err != nil {
			return 0, nil, err
		}

		fin := (hdr[0] & 0x80) != 0
		opcode := hdr[0] & 0x0F
		masked := (hdr[1] & 0x80) != 0
		length := uint64(hdr[1] & 0x7F)

		switch length {
		case 126:
			var ext [2]byte
			if _, err := io.ReadFull(w.reader, ext[:]); err != nil {
				return 0, nil, err
			}
			length = uint64(binary.BigEndian.Uint16(ext[:]))
		case 127:
			var ext [8]byte
			if _, err := io.ReadFull(w.reader, ext[:]); err != nil {
				return 0, nil, err
			}
			length = binary.BigEndian.Uint64(ext[:])
		}

		if length > maxWSFramePayload {
			return 0, nil, errors.New("websocket frame exceeds 8 MiB limit")
		}

		var maskKey [4]byte
		if masked {
			if _, err := io.ReadFull(w.reader, maskKey[:]); err != nil {
				return 0, nil, err
			}
		}

		payload := make([]byte, length)
		if length > 0 {
			if _, err := io.ReadFull(w.reader, payload); err != nil {
				return 0, nil, err
			}
			if masked {
				unmaskPayload(payload, maskKey)
			}
		}

		switch opcode {
		case OpcodeClose:
			return OpcodeClose, payload, nil
		case OpcodePing:
			_ = w.WriteFrame(OpcodePong, payload)
			continue
		case OpcodePong:
			continue
		}

		if opcode != OpcodeContinuation {
			msgOpcode = opcode
		}
		fragments = append(fragments, payload...)

		if fin {
			if msgOpcode == 0 {
				msgOpcode = OpcodeBinary
			}
			return msgOpcode, fragments, nil
		}
	}
}

func (w *WSConn) WriteFrame(opcode byte, payload []byte) error {
	var hdr [10]byte
	hdr[0] = 0x80 | (opcode & 0x0F)
	n := len(payload)
	hdrLen := 2
	switch {
	case n < 126:
		hdr[1] = byte(n)
	case n < 65536:
		hdr[1] = 126
		binary.BigEndian.PutUint16(hdr[2:4], uint16(n))
		hdrLen = 4
	default:
		hdr[1] = 127
		binary.BigEndian.PutUint64(hdr[2:10], uint64(n))
		hdrLen = 10
	}

	w.writeMu.Lock()
	defer w.writeMu.Unlock()
	if w.closed {
		return net.ErrClosed
	}
	if _, err := w.conn.Write(hdr[:hdrLen]); err != nil {
		return err
	}
	if n > 0 {
		if _, err := w.conn.Write(payload); err != nil {
			return err
		}
	}
	return nil
}

func (w *WSConn) WriteBinary(payload []byte) error {
	return w.WriteFrame(OpcodeBinary, payload)
}

func (w *WSConn) WriteText(payload []byte) error {
	return w.WriteFrame(OpcodeText, payload)
}

func (w *WSConn) Close() error {
	w.writeMu.Lock()
	w.closed = true
	w.writeMu.Unlock()
	return w.conn.Close()
}

func unmaskPayload(payload []byte, mask [4]byte) {
	for i := range payload {
		payload[i] ^= mask[i&3]
	}
}
