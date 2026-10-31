package signaling

import (
	"encoding/json"
	"net/http"
	"strings"
	"time"
)

type HTTPHandler struct {
	hub   *MessageHub
	token string
}

func NewHTTPHandler(hub *MessageHub, authToken string) *HTTPHandler {
	return &HTTPHandler{
		hub:   hub,
		token: authToken,
	}
}

func (h *HTTPHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if h.token != "" {
		auth := strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
		if auth == "" {
			auth = r.URL.Query().Get("token")
		}
		if auth != h.token {
			http.Error(w, "Unauthorized signal access", http.StatusUnauthorized)
			return
		}
	}

	room := r.URL.Query().Get("room")
	peer := r.URL.Query().Get("peer")
	if room == "" || peer == "" {
		http.Error(w, "Missing room or peer query parameter", http.StatusBadRequest)
		return
	}

	switch r.Method {
	case http.MethodPost:
		var msg SignalMessage
		if err := json.NewDecoder(r.Body).Decode(&msg); err != nil {
			http.Error(w, "Invalid signal JSON", http.StatusBadRequest)
			return
		}
		msg.Sender = peer
		count := h.hub.Broadcast(room, msg)
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]any{
			"status": "delivered",
			"peers":  count,
		})

	case http.MethodGet:
		// Long-polling pull endpoint: wait up to 25s for incoming signal
		ch, err := h.hub.Register(room, peer)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		defer h.hub.Unregister(room, peer)

		select {
		case msg, ok := <-ch:
			if !ok {
				http.Error(w, "Session terminated", http.StatusGone)
				return
			}
			w.Header().Set("Content-Type", "application/json")
			_ = json.NewEncoder(w).Encode(msg)

		case <-time.After(25 * time.Second):
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusNoContent)

		case <-r.Context().Done():
			return
		}

	default:
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
	}
}
