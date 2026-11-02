package httpctrl

import (
	"context"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"os"
	"strings"
	"time"

	"app.duinch/adb-device-manager/internal/controller/http/ui"
	"github.com/nchungdev/remote-tunnel/go/pairing"
)

func getLANIPAddress() string {
	conn, err := net.Dial("udp", "8.8.8.8:80")
	if err == nil {
		defer conn.Close()
		if localAddr, ok := conn.LocalAddr().(*net.UDPAddr); ok {
			return localAddr.IP.String()
		}
	}
	return "127.0.0.1"
}

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

func extractPort(r *http.Request) string {
	host := r.Host
	if strings.Contains(host, ":") {
		return strings.Split(host, ":")[1]
	}
	return "8080"
}

func (s *Server) handleRemoteStatus(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	room := s.devices.GetRoomCode()
	dev := s.devices.GetActiveRemoteDevice()
	lan := fmt.Sprintf("%s:%s", getLANIPAddress(), extractPort(r))

	active := false
	url := ""
	if s.qt != nil {
		active = s.qt.IsActive()
		url = s.qt.GetURL()
	}
	hostName, _ := os.Hostname()
	hostName = strings.TrimSuffix(hostName, ".local")

	_ = json.NewEncoder(w).Encode(map[string]any{
		"active":           active,
		"url":              url,
		"lan":              lan,
		"room":             room,
		"hostname":         hostName,
		"connected_device": dev,
	})
}

func (s *Server) handleRemoteToggle(w http.ResponseWriter, r *http.Request) {
	if !pairing.IsLoopbackRequest(r) {
		http.Error(w, "Forbidden: host loopback required", http.StatusForbidden)
		return
	}
	activeStr := r.URL.Query().Get("active")
	active := activeStr == "true" || activeStr == "1"

	var url string
	var err error
	if active {
		if s.qt != nil {
			ctx, cancel := context.WithTimeout(context.Background(), 25*time.Second)
			defer cancel()
			url, err = s.qt.Start(ctx)
		}
		s.devices.SetRemoteActive(true)
		if err == nil {
			s.devices.BroadcastRemoteTunnelState(r.Context(), url, s.devices.GetRoomCode(), true)
		}
	} else {
		if s.qt != nil {
			_ = s.qt.Stop()
		}
		s.devices.SetRemoteActive(false)
		s.devices.BroadcastRemoteTunnelState(r.Context(), "", s.devices.GetRoomCode(), false)
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
	lan := fmt.Sprintf("%s:%s", getLANIPAddress(), extractPort(r))

	payload, err := pairing.BuildPairingJSON("adb-remote", url, "", room, lan)
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

func (s *Server) handlePairRequest(w http.ResponseWriter, r *http.Request) {
	var req struct {
		DeviceID   string `json:"device_id"`
		DeviceName string `json:"device_name"`
		Code       string `json:"code"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	if req.DeviceID == "" {
		req.DeviceID = "android-device"
	}
	s.pairHub.SubmitRequest(req.DeviceID, req.DeviceName, req.Code)
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"status": "pending"})
}

func (s *Server) handlePairPending(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	pending := s.pairHub.GetPending()
	_ = json.NewEncoder(w).Encode(pending)
}

func (s *Server) handlePairConfirm(w http.ResponseWriter, r *http.Request) {
	if !pairing.IsLoopbackRequest(r) {
		http.Error(w, "Forbidden: pairing confirmation must originate on host machine", http.StatusForbidden)
		return
	}
	var req struct {
		DeviceID string `json:"device_id"`
		Approved bool   `json:"approved"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	s.pairHub.Confirm(req.DeviceID, req.Approved)
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"status": "ok"})
}

func (s *Server) handlePairCheck(w http.ResponseWriter, r *http.Request) {
	deviceID := r.URL.Query().Get("device_id")
	status := s.pairHub.CheckStatus(deviceID)
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"status": status})
}

func (s *Server) handleRemoteRequestEnable(w http.ResponseWriter, r *http.Request) {
	var req struct {
		DeviceID   string `json:"device_id"`
		DeviceName string `json:"device_name"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	if req.DeviceID == "" {
		req.DeviceID = "android-device"
	}
	s.pairHub.SubmitRemoteTunnelRequest(req.DeviceID, req.DeviceName)
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"status": "pending"})
}

func (s *Server) handleRemotePendingRequests(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	pending := s.pairHub.GetPendingRemoteTunnelRequests()
	_ = json.NewEncoder(w).Encode(pending)
}

func (s *Server) handleRemoteApproveRequest(w http.ResponseWriter, r *http.Request) {
	if !pairing.IsLoopbackRequest(r) {
		http.Error(w, "Forbidden: remote tunnel approval must originate on host machine", http.StatusForbidden)
		return
	}
	var req struct {
		DeviceID string `json:"device_id"`
		Approved bool   `json:"approved"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}

	url := ""
	room := s.devices.GetRoomCode()
	if req.Approved {
		if s.qt != nil && !s.qt.IsActive() {
			ctx, cancel := context.WithTimeout(context.Background(), 25*time.Second)
			defer cancel()
			url, _ = s.qt.Start(ctx)
		} else if s.qt != nil {
			url = s.qt.GetURL()
		}
		s.devices.SetRemoteActive(true)
		s.devices.BroadcastRemoteTunnelState(r.Context(), url, room, true)
	}

	s.pairHub.ConfirmRemoteTunnelRequest(req.DeviceID, req.Approved, url, room)
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{
		"status": "ok",
		"active": req.Approved,
		"url":    url,
		"room":   room,
	})
}

func (s *Server) handleRemoteRequestStatus(w http.ResponseWriter, r *http.Request) {
	deviceID := r.URL.Query().Get("device_id")
	status, url, room := s.pairHub.CheckRemoteTunnelStatus(deviceID)
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{
		"status": status,
		"url":    url,
		"room":   room,
	})
}
