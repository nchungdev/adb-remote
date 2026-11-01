package httpctrl

import (
	"encoding/json"
	"fmt"
	"net/http"

	"github.com/skip2/go-qrcode"

	"app.duinch/adb-device-manager/internal/controller/http/ui"
)

type RemoteStateProvider interface {
	GetRoomCode() string
	IsRemoteActive() bool
	SetRemoteActive(active bool)
	GetActiveDevice() string
	GetPort() int
}

func (s *Server) handleRemotePage(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	data, err := ui.DistFS.ReadFile("remote.html")
	if err != nil {
		http.Error(w, "Remote page not found", http.StatusNotFound)
		return
	}
	_, _ = w.Write(data)
}

func (s *Server) handleRemoteStatus(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	room := s.devices.GetRoomCode()
	active := s.devices.IsRemoteActive()
	dev := s.devices.GetActiveRemoteDevice()

	_ = json.NewEncoder(w).Encode(map[string]any{
		"active":           active,
		"room":             room,
		"connected_device": dev,
	})
}

func (s *Server) handleRemoteToggle(w http.ResponseWriter, r *http.Request) {
	activeStr := r.URL.Query().Get("active")
	active := activeStr == "true" || activeStr == "1"
	s.devices.SetRemoteActive(active)

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{
		"status": "ok",
		"active": active,
	})
}

func (s *Server) handleRemoteQrPng(w http.ResponseWriter, r *http.Request) {
	room := s.devices.GetRoomCode()
	active := s.devices.IsRemoteActive()

	payload := map[string]string{
		"type":   "adb-remote",
		"room":   room,
		"broker": "broker.hivemq.com",
		"active": fmt.Sprintf("%t", active),
	}
	data, err := json.Marshal(payload)
	if err != nil {
		http.Error(w, "Failed to encode QR payload", http.StatusInternalServerError)
		return
	}

	png, err := qrcode.Encode(string(data), qrcode.Medium, 256)
	if err != nil {
		http.Error(w, "Failed to generate QR code", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "image/png")
	w.Header().Set("Cache-Control", "no-cache, no-store, must-revalidate")
	_, _ = w.Write(png)
}
