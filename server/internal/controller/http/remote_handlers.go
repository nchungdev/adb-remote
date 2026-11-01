package httpctrl

import (
	"context"
	"encoding/json"
	"net/http"
	"time"

	"app.duinch/adb-device-manager/internal/controller/http/ui"
	"github.com/nchungdev/remote-tunnel/go/pairing"
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
	dev := s.devices.GetActiveRemoteDevice()

	active := false
	url := ""
	if s.qt != nil {
		active = s.qt.IsActive()
		url = s.qt.GetURL()
	}

	_ = json.NewEncoder(w).Encode(map[string]any{
		"active":           active,
		"url":              url,
		"room":             room,
		"connected_device": dev,
	})
}

func (s *Server) handleRemoteToggle(w http.ResponseWriter, r *http.Request) {
	activeStr := r.URL.Query().Get("active")
	active := activeStr == "true" || activeStr == "1"

	var url string
	var err error
	if active {
		if s.qt != nil {
			ctx, cancel := context.WithTimeout(r.Context(), 25*time.Second)
			defer cancel()
			url, err = s.qt.Start(ctx)
		}
		s.devices.SetRemoteActive(true)
	} else {
		if s.qt != nil {
			_ = s.qt.Stop()
		}
		s.devices.SetRemoteActive(false)
	}

	w.Header().Set("Content-Type", "application/json")
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		_ = json.NewEncoder(w).Encode(map[string]any{
			"status": "error",
			"error":  err.Error(),
			"active": false,
		})
		return
	}

	_ = json.NewEncoder(w).Encode(map[string]any{
		"status": "ok",
		"active": active,
		"url":    url,
	})
}

func (s *Server) handleRemoteQrPng(w http.ResponseWriter, r *http.Request) {
	room := s.devices.GetRoomCode()
	url := ""
	if s.qt != nil {
		url = s.qt.GetURL()
	}

	payload, err := pairing.BuildPairingJSON("adb-remote", url, "", room)
	if err != nil {
		http.Error(w, "Failed to encode QR payload", http.StatusInternalServerError)
		return
	}

	png, err := pairing.GenerateQRPNG(payload, 256)
	if err != nil {
		http.Error(w, "Failed to generate QR code", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "image/png")
	w.Header().Set("Cache-Control", "no-cache, no-store, must-revalidate")
	_, _ = w.Write(png)
}
