package tunnel

import (
	"crypto/rand"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"errors"
	"net"
	"os"
	"sync"
	"sync/atomic"
	"time"
)

const (
	FrameOpen  byte = 0x01
	FrameData  byte = 0x02
	FrameClose byte = 0x03
)

type Session struct {
	SessionID    string
	Alias        string
	MappedPort   int
	ConnectedAt  int64

	transport    Transport
	path         PathKind
	identity     ServerIdentity
	connectionID string
	standby      atomic.Bool
	localAdbPort atomic.Int32
	bytesBridged atomic.Int64
	nextStreamID atomic.Uint32
	alive        atomic.Bool

	streamsMu sync.Mutex
	streams   map[uint32]net.Conn
}

func NewSession(transport Transport, alias string, localPort, mappedPort int) *Session {
	var rawID [6]byte
	_, _ = rand.Read(rawID[:])
	s := &Session{
		SessionID:   hex.EncodeToString(rawID[:]),
		Alias:       alias,
		MappedPort:  mappedPort,
		ConnectedAt: time.Now().Unix(),
		transport:   transport,
		streams:     make(map[uint32]net.Conn),
	}
	s.localAdbPort.Store(int32(localPort))
	s.alive.Store(true)
	return s
}

func (s *Session) Alive() bool {
	return s.alive.Load()
}

func (s *Session) LocalPort() int {
	return int(s.localAdbPort.Load())
}

func (s *Session) SetLocalPort(port int) {
	if port >= 1 && port <= 65535 {
		s.localAdbPort.Store(int32(port))
	}
}

func (s *Session) BytesBridged() int64 {
	return s.bytesBridged.Load()
}

func (s *Session) SendWelcome(serverLanIP, serverSSID string) error {
	payload, err := json.Marshal(map[string]any{
		"type":          "welcome",
		"mapped_port":   s.MappedPort,
		"server_lan_ip": serverLanIP,
		"server_ssid":   serverSSID,
		"server_id":     s.identity.ID,
		"server_name":   s.identity.Name,
	})
	if err != nil {
		return err
	}
	return s.transport.WriteText(payload)
}

func (s *Session) sendRole(standby bool) {
	role := "active"
	if standby {
		role = "standby"
	}
	_ = s.transport.WriteText([]byte(`{"type":"path_role","role":"` + role + `"}`))
}

func (s *Session) WriteText(payload []byte) error {
	return s.transport.WriteText(payload)
}

func (s *Session) AttachLocalClient(client net.Conn) {
	if !s.Alive() {
		_ = client.Close()
		return
	}
	if tcp, ok := client.(*net.TCPConn); ok {
		_ = tcp.SetNoDelay(true)
	}

	streamID := s.nextStreamID.Add(1)
	s.streamsMu.Lock()
	s.streams[streamID] = client
	s.streamsMu.Unlock()

	var openFrame [5]byte
	openFrame[0] = FrameOpen
	binary.BigEndian.PutUint32(openFrame[1:5], streamID)
	if err := s.transport.WriteBinary(openFrame[:]); err != nil {
		s.CloseStream(streamID)
		return
	}

	go s.pumpTCPToWS(streamID, client)
}

func (s *Session) pumpTCPToWS(streamID uint32, client net.Conn) {
	defer func() {
		s.CloseStream(streamID)
		if s.Alive() {
			var closeFrame [5]byte
			closeFrame[0] = FrameClose
			binary.BigEndian.PutUint32(closeFrame[1:5], streamID)
			_ = s.transport.WriteBinary(closeFrame[:])
		}
	}()

	const maxChunk = 32 * 1024
	frameBuf := make([]byte, 5+maxChunk)
	frameBuf[0] = FrameData
	binary.BigEndian.PutUint32(frameBuf[1:5], streamID)

	var lastSend time.Time
	for s.Alive() {
		_ = client.SetReadDeadline(time.Time{})
		n, err := client.Read(frameBuf[5 : 5+16384])
		if n <= 0 || err != nil {
			return
		}
		total := n
		// Coalesce immediately-adjacent ADB header + payload writes within 3ms
		for total < maxChunk {
			_ = client.SetReadDeadline(time.Now().Add(3 * time.Millisecond))
			more, rerr := client.Read(frameBuf[5+total : 5+maxChunk])
			if more > 0 {
				total += more
			}
			if rerr != nil {
				if errors.Is(rerr, os.ErrDeadlineExceeded) {
					break
				}
				break
			}
			if more == 0 {
				break
			}
		}
		if !lastSend.IsZero() {
			if gap := 6*time.Millisecond - time.Since(lastSend); gap > 0 {
				time.Sleep(gap)
			}
		}
		if err := s.transport.WriteBinary(frameBuf[:5+total]); err != nil {
			return
		}
		lastSend = time.Now()
		s.bytesBridged.Add(int64(total))
	}
}

func (s *Session) HandleTextJSON(payload []byte, serverLanIP, serverSSID string) {
	var msg struct {
		Type         string `json:"type"`
		LocalAdbPort int    `json:"local_adb_port"`
		LocalWifiIP  string `json:"local_wifi_ip"`
		WifiSSID     string `json:"wifi_ssid"`
	}
	if json.Unmarshal(payload, &msg) == nil && msg.Type == "hello" {
		if msg.LocalAdbPort > 0 {
			s.SetLocalPort(msg.LocalAdbPort)
		}
		_ = s.SendWelcome(serverLanIP, serverSSID)
	}
}

func (s *Session) HandleWSBinary(data []byte) {
	if len(data) < 5 {
		return
	}
	ftype := data[0]
	streamID := binary.BigEndian.Uint32(data[1:5])

	switch ftype {
	case FrameData:
		payload := data[5:]
		s.streamsMu.Lock()
		conn := s.streams[streamID]
		s.streamsMu.Unlock()
		if conn != nil {
			if _, err := conn.Write(payload); err != nil {
				s.CloseStream(streamID)
				return
			}
			s.bytesBridged.Add(int64(len(payload)))
		}
	case FrameClose:
		s.CloseStream(streamID)
	}
}

func (s *Session) CloseStream(streamID uint32) {
	s.streamsMu.Lock()
	conn, ok := s.streams[streamID]
	if ok {
		delete(s.streams, streamID)
	}
	s.streamsMu.Unlock()
	if ok && conn != nil {
		_ = conn.Close()
	}
}

func (s *Session) Shutdown() {
	if !s.alive.CompareAndSwap(true, false) {
		return
	}
	s.streamsMu.Lock()
	conns := make([]net.Conn, 0, len(s.streams))
	for id, c := range s.streams {
		conns = append(conns, c)
		delete(s.streams, id)
	}
	s.streamsMu.Unlock()

	for _, c := range conns {
		_ = c.Close()
	}
	_ = s.transport.Close()
}
