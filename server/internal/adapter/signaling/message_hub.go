package signaling

import (
	"encoding/json"
	"errors"
	"sync"
	"time"
)

type SignalMessage struct {
	Type      string          `json:"type"` // "offer", "answer", "candidate", "ping"
	Sender    string          `json:"sender"`
	Recipient string          `json:"recipient,omitempty"`
	Payload   json.RawMessage `json:"payload"`
	CreatedAt int64           `json:"created_at"`
}

type MessageHub struct {
	mu          sync.RWMutex
	rooms       map[string]map[string]chan SignalMessage
	maxQueueLen int
}

func NewMessageHub() *MessageHub {
	return &MessageHub{
		rooms:       make(map[string]map[string]chan SignalMessage),
		maxQueueLen: 64,
	}
}

func (h *MessageHub) Register(room, peerID string) (<-chan SignalMessage, error) {
	if room == "" || peerID == "" {
		return nil, errors.New("empty room or peerID")
	}

	h.mu.Lock()
	defer h.mu.Unlock()

	peers, exists := h.rooms[room]
	if !exists {
		peers = make(map[string]chan SignalMessage)
		h.rooms[room] = peers
	}

	if oldCh, ok := peers[peerID]; ok {
		close(oldCh)
	}

	ch := make(chan SignalMessage, h.maxQueueLen)
	peers[peerID] = ch
	return ch, nil
}

func (h *MessageHub) Unregister(room, peerID string) {
	h.mu.Lock()
	defer h.mu.Unlock()

	peers, exists := h.rooms[room]
	if !exists {
		return
	}

	if ch, ok := peers[peerID]; ok {
		delete(peers, peerID)
		close(ch)
	}

	if len(peers) == 0 {
		delete(h.rooms, room)
	}
}

func (h *MessageHub) Broadcast(room string, msg SignalMessage) int {
	h.mu.RLock()
	defer h.mu.RUnlock()

	peers, exists := h.rooms[room]
	if !exists {
		return 0
	}

	msg.CreatedAt = time.Now().UnixMilli()
	sent := 0

	for id, ch := range peers {
		if id == msg.Sender {
			continue // Do not echo back to sender
		}
		if msg.Recipient != "" && msg.Recipient != id {
			continue // Targeted message
		}
		select {
		case ch <- msg:
			sent++
		default:
			// Buffer full, drop oldest or drop msg to prevent blocking
		}
	}
	return sent
}
