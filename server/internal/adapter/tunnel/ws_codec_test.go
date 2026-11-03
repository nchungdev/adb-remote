package tunnel_test

import (
	"bytes"
	"encoding/binary"
	"io"
	"log/slog"
	"net"
	"testing"
	"time"

	"app.duinch/adb-device-manager/internal/adapter/tunnel"
)

func writeMaskedClientFrame(w io.Writer, opcode byte, payload []byte) error {
	mask := [4]byte{0x12, 0x34, 0x56, 0x78}
	var hdr [10]byte
	hdr[0] = 0x80 | opcode
	hdrLen := 2
	n := len(payload)
	if n < 126 {
		hdr[1] = 0x80 | byte(n)
	} else {
		hdr[1] = 0x80 | 126
		binary.BigEndian.PutUint16(hdr[2:4], uint16(n))
		hdrLen = 4
	}
	if _, err := w.Write(hdr[:hdrLen]); err != nil {
		return err
	}
	if _, err := w.Write(mask[:]); err != nil {
		return err
	}
	masked := make([]byte, n)
	for i := range payload {
		masked[i] = payload[i] ^ mask[i&3]
	}
	_, err := w.Write(masked)
	return err
}

func TestHub_EndToEndMultiplexedTCPStreamAndPing(t *testing.T) {
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	hub := tunnel.NewHub(25555, nil, nil, logger)
	defer hub.CloseAll()

	serverSide, phoneSide := net.Pipe()
	defer phoneSide.Close()

	done := make(chan struct{})
	go func() {
		defer close(done)
		hub.ServeWebSocket(serverSide, nil, "android-test-device", 35503, tunnel.PathLAN)
	}()

	phoneWS := tunnel.NewWSConn(phoneSide, nil)

	// 1. Expect initial JSON welcome frame
	op, welcomeBytes, err := phoneWS.ReadMessage()
	if err != nil || op != tunnel.OpcodeText || !bytes.Contains(welcomeBytes, []byte("welcome")) {
		t.Fatalf("expected welcome text frame, got op=%d err=%v payload=%s", op, err, string(welcomeBytes))
	}

	active := hub.ListActiveTunnels()
	if len(active) != 1 {
		t.Fatalf("expected 1 active tunnel, got %d", len(active))
	}
	mappedAddr := active[0].MappedAddr

	// 2. Send masked Ping from phone and expect Pong inline
	if err := writeMaskedClientFrame(phoneSide, tunnel.OpcodePing, []byte("hb")); err != nil {
		t.Fatalf("write ping: %v", err)
	}

	// 3. Connect local TCP client (simulating Mac mini `adb` daemon)
	adbClient, err := net.DialTimeout("tcp4", mappedAddr, 2*time.Second)
	if err != nil {
		t.Fatalf("dial mapped loopback %s: %v", mappedAddr, err)
	}
	defer adbClient.Close()

	// Phone receives FrameOpen (0x01 + streamID)
	op, openPayload, err := phoneWS.ReadMessage()
	if err != nil || op != tunnel.OpcodeBinary || len(openPayload) != 5 || openPayload[0] != tunnel.FrameOpen {
		t.Fatalf("expected FrameOpen, got op=%d len=%d err=%v", op, len(openPayload), err)
	}
	streamID := binary.BigEndian.Uint32(openPayload[1:5])

	// 4. ADB client writes CNXN packet -> Phone receives FrameData
	wantOut := []byte("CNXN\x00\x00\x00\x01host::adb")
	if _, err := adbClient.Write(wantOut); err != nil {
		t.Fatalf("adb write: %v", err)
	}
	op, dataPayload, err := phoneWS.ReadMessage()
	if err != nil || op != tunnel.OpcodeBinary || dataPayload[0] != tunnel.FrameData {
		t.Fatalf("expected FrameData, got op=%d err=%v", op, err)
	}
	if !bytes.Equal(dataPayload[5:], wantOut) {
		t.Fatalf("bridged payload mismatch: got %q want %q", dataPayload[5:], wantOut)
	}

	// 5. Phone replies with masked FrameData -> ADB client reads exact bytes
	replyPayload := []byte("CNXN\x00\x00\x00\x01device::SM_S926B")
	replyFrame := make([]byte, 5+len(replyPayload))
	replyFrame[0] = tunnel.FrameData
	binary.BigEndian.PutUint32(replyFrame[1:5], streamID)
	copy(replyFrame[5:], replyPayload)
	if err := writeMaskedClientFrame(phoneSide, tunnel.OpcodeBinary, replyFrame); err != nil {
		t.Fatalf("write masked reply: %v", err)
	}

	gotReply := make([]byte, len(replyPayload))
	if _, err := io.ReadFull(adbClient, gotReply); err != nil {
		t.Fatalf("read reply on adbClient: %v", err)
	}
	if !bytes.Equal(gotReply, replyPayload) {
		t.Fatalf("reply mismatch: got %q want %q", gotReply, replyPayload)
	}

	_ = writeMaskedClientFrame(phoneSide, tunnel.OpcodeClose, nil)
	<-done
}
