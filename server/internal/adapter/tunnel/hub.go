package tunnel

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net"
	"sync"
	"time"

	"app.duinch/adb-device-manager/internal/domain"
	"app.duinch/adb-device-manager/internal/usecase"
)

type AutoConnectFunc func(ctx context.Context, mappedAddr string)
type AutoDisconnectFunc func(ctx context.Context, mappedAddr string)

type deviceSlot struct {
	alias      string
	mappedPort int
	listener   net.Listener
	mu         sync.RWMutex
	session    *Session
}

func newDeviceSlot(alias string, ln net.Listener, mappedPort int) *deviceSlot {
	slot := &deviceSlot{
		alias:      alias,
		mappedPort: mappedPort,
		listener:   ln,
	}
	go slot.acceptLoop()
	return slot
}

func (d *deviceSlot) acceptLoop() {
	for {
		conn, err := d.listener.Accept()
		if err != nil {
			if errorsIsClosed(err) {
				return
			}
			time.Sleep(150 * time.Millisecond)
			continue
		}
		d.mu.RLock()
		sess := d.session
		d.mu.RUnlock()
		if sess != nil && sess.Alive() {
			sess.AttachLocalClient(conn)
		} else {
			_ = conn.Close()
		}
	}
}

type Hub struct {
	mu             sync.Mutex
	slots          map[string]*deviceSlot
	nextPort       int
	history        usecase.HistoryRepository
	autoConnect    AutoConnectFunc
	autoDisconnect AutoDisconnectFunc
	logger         *slog.Logger
}

func NewHub(basePort int, history usecase.HistoryRepository, autoConnect AutoConnectFunc, logger *slog.Logger) *Hub {
	return &Hub{
		slots:       make(map[string]*deviceSlot),
		nextPort:    basePort,
		history:     history,
		autoConnect: autoConnect,
		logger:      logger,
	}
}

func (h *Hub) SetAutoDisconnect(fn AutoDisconnectFunc) {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.autoDisconnect = fn
}

func (h *Hub) ServeWebSocket(rawConn net.Conn, rw *bufio.ReadWriter, alias string, localPort int) {
	ws := NewWSConn(rawConn, rw)
	session, err := h.RegisterSession(ws, alias, localPort)
	if err != nil {
		h.logger.Error("failed to allocate loopback slot", "alias", alias, "err", err)
		_ = ws.Close()
		return
	}
	defer h.UnregisterSession(session)

	serverIP := findPrimaryLanIP()
	serverSSID := detectServerSSID()
	_ = session.SendWelcome(serverIP, serverSSID)
	for session.Alive() {
		_ = rawConn.SetReadDeadline(time.Now().Add(90 * time.Second))
		opcode, payload, err := ws.ReadMessage()
		if err != nil || opcode == OpcodeClose {
			return
		}
		switch opcode {
		case OpcodeText:
			var msg struct {
				Type         string `json:"type"`
				LocalAdbPort int    `json:"local_adb_port"`
				LocalWifiIP  string `json:"local_wifi_ip"`
				WifiSSID     string `json:"wifi_ssid"`
			}
			if json.Unmarshal(payload, &msg) == nil && msg.Type == "hello" {
				if msg.LocalAdbPort > 0 {
					session.SetLocalPort(msg.LocalAdbPort)
				}
				_ = session.SendWelcome(serverIP, serverSSID)

				// If phone reported a local Wi-Fi IP and server can reach it on LAN:
				if msg.LocalWifiIP != "" && h.autoConnect != nil {
					targetPort := msg.LocalAdbPort
					if targetPort <= 0 {
						targetPort = session.LocalPort()
					}
					lanTarget := fmt.Sprintf("%s:%d", msg.LocalWifiIP, targetPort)
					go func(target string) {
						// Quick probe within 400ms to verify same LAN
						conn, dialErr := net.DialTimeout("tcp", target, 400*time.Millisecond)
						if dialErr == nil {
							_ = conn.Close()
							h.logger.Info("same LAN detected, auto-connecting direct Wi-Fi", "addr", target)
							ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
							defer cancel()
							h.autoConnect(ctx, target)
						}
					}(lanTarget)
				}
			}
		case OpcodeBinary:
			session.HandleWSBinary(payload)
		}
	}
}

func (h *Hub) BroadcastCommand(serial string, record domain.DeviceCommandRecord) {
	payload, err := json.Marshal(map[string]any{
		"type":  "command_event",
		"event": record,
	})
	if err != nil {
		return
	}
	h.mu.Lock()
	slots := make([]*deviceSlot, 0, len(h.slots))
	for _, s := range h.slots {
		slots = append(slots, s)
	}
	h.mu.Unlock()

	for _, slot := range slots {
		slot.mu.RLock()
		sess := slot.session
		mappedAddr := fmt.Sprintf("127.0.0.1:%d", slot.mappedPort)
		alias := slot.alias
		slot.mu.RUnlock()

		if sess != nil && sess.Alive() {
			if serial == "global" || serial == mappedAddr || serial == alias {
				_ = sess.WriteText(payload)
			}
		}
	}
}

func (h *Hub) ListActiveTunnels() []domain.TunnelInfo {
	h.mu.Lock()
	slots := make([]*deviceSlot, 0, len(h.slots))
	for _, s := range h.slots {
		slots = append(slots, s)
	}
	h.mu.Unlock()

	var out []domain.TunnelInfo
	for _, slot := range slots {
		slot.mu.RLock()
		sess := slot.session
		slot.mu.RUnlock()
		if sess != nil && sess.Alive() {
			out = append(out, domain.TunnelInfo{
				SessionID:    sess.SessionID,
				Alias:        sess.Alias,
				LocalAdbPort: sess.LocalPort(),
				MappedAddr:   fmt.Sprintf("127.0.0.1:%d", slot.mappedPort),
				ConnectedAt:  sess.ConnectedAt,
				BytesBridged: sess.BytesBridged(),
			})
		}
	}
	return out
}

func (h *Hub) CloseAll() {
	h.mu.Lock()
	slots := make([]*deviceSlot, 0, len(h.slots))
	for _, s := range h.slots {
		slots = append(slots, s)
	}
	h.mu.Unlock()

	for _, slot := range slots {
		slot.mu.Lock()
		sess := slot.session
		slot.session = nil
		slot.mu.Unlock()
		if sess != nil && sess.Alive() {
			sess.Shutdown()
			h.recordClosed(sess, "Dịch vụ khởi động lại")
		}
		_ = slot.listener.Close()
	}
}

func (h *Hub) RegisterSession(transport Transport, rawAlias string, rawPort int) (*Session, error) {
	alias := domain.SanitizeAlias(rawAlias)
	localPort := domain.SanitizePort(rawPort)

	h.mu.Lock()
	slot, ok := h.slots[alias]
	if !ok {
		ln, port, err := h.bindNextLoopbackPortLocked()
		if err != nil {
			h.mu.Unlock()
			return nil, err
		}
		slot = newDeviceSlot(alias, ln, port)
		h.slots[alias] = slot
	}
	h.mu.Unlock()

	newSess := NewSession(transport, alias, localPort, slot.mappedPort)

	slot.mu.Lock()
	oldSess := slot.session
	slot.session = newSess
	slot.mu.Unlock()

	if oldSess != nil && oldSess != newSess && oldSess.Alive() {
		oldSess.Shutdown()
		h.recordClosed(oldSess, "Phiên mới thay thế")
	}

	h.logger.Info("tunnel connected", "alias", alias, "local_port", localPort, "mapped_port", slot.mappedPort)

	if h.autoConnect != nil {
		addr := fmt.Sprintf("127.0.0.1:%d", slot.mappedPort)
		go func() {
			time.Sleep(350 * time.Millisecond)
			ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
			defer cancel()
			h.autoConnect(ctx, addr)
		}()
	}
	return newSess, nil
}

func (h *Hub) UnregisterSession(sess *Session) {
	wasAlive := sess.Alive()
	sess.Shutdown()

	h.mu.Lock()
	slot := h.slots[sess.Alias]
	autoDis := h.autoDisconnect
	h.mu.Unlock()
	if slot != nil {
		slot.mu.Lock()
		if slot.session == sess {
			slot.session = nil
		}
		slot.mu.Unlock()
		if autoDis != nil {
			addr := fmt.Sprintf("127.0.0.1:%d", slot.mappedPort)
			go func() {
				ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
				defer cancel()
				autoDis(ctx, addr)
			}()
		}
	}
	if wasAlive {
		h.recordClosed(sess, "Đã ngắt kết nối")
		h.logger.Info("tunnel disconnected", "alias", sess.Alias, "bytes_bridged", sess.BytesBridged())
	}
}

func (h *Hub) recordClosed(sess *Session, reason string) {
	if h.history == nil {
		return
	}
	ended := max(time.Now().Unix(), sess.ConnectedAt)
	_ = h.history.AppendClosed(domain.TunnelHistoryEntry{
		SessionID:    sess.SessionID,
		Alias:        sess.Alias,
		LocalAdbPort: sess.LocalPort(),
		MappedAddr:   fmt.Sprintf("127.0.0.1:%d", sess.MappedPort),
		ConnectedAt:  sess.ConnectedAt,
		EndedAt:      &ended,
		BytesBridged: sess.BytesBridged(),
		Active:       false,
		Reason:       reason,
	})
}

func (h *Hub) bindNextLoopbackPortLocked() (net.Listener, int, error) {
	for range 500 {
		candidate := h.nextPort
		h.nextPort++
		addr := fmt.Sprintf("127.0.0.1:%d", candidate)
		ln, err := net.Listen("tcp4", addr)
		if err == nil {
			return ln, candidate, nil
		}
	}
	return nil, 0, fmt.Errorf("unable to bind loopback port starting near %d", h.nextPort)
}

func errorsIsClosed(err error) bool {
	return err != nil && (err == net.ErrClosed || len(err.Error()) > 0 && containsClosed(err.Error()))
}

func containsClosed(s string) bool {
	for i := 0; i+6 <= len(s); i++ {
		if s[i:i+6] == "closed" {
			return true
		}
	}
	return false
}
