package tunnel

import (
	"fmt"
	"net"
	"time"

	"app.duinch/adb-device-manager/internal/domain"
)

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
