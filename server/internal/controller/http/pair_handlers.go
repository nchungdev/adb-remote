package httpctrl

import (
	"encoding/json"
	"net"
	"net/http"

	"github.com/nchungdev/remote-tunnel/go/pairing"
)

func (s *Server) handlePairRequest(w http.ResponseWriter, r *http.Request) {
	clientIP, _, _ := net.SplitHostPort(r.RemoteAddr)
	if clientIP == "" {
		clientIP = r.RemoteAddr
	}
	if !s.pairHub.CheckRateLimit(clientIP) {
		http.Error(w, "Too many pairing requests: rate limit exceeded", http.StatusTooManyRequests)
		return
	}
	deviceID := requestDeviceID(r)
	if deviceID == "" {
		http.Error(w, "Missing device secret", http.StatusUnauthorized)
		return
	}
	var req struct {
		DeviceName string `json:"device_name"`
		Code       string `json:"code"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	if len(req.Code) == 0 || len(req.Code) > 16 || len(req.DeviceName) > 64 {
		http.Error(w, "invalid pairing request", http.StatusBadRequest)
		return
	}
	if s.paired.isApproved(deviceID) {
		writeJSON(w, http.StatusOK, map[string]any{"status": "approved"})
		return
	}
	if s.pairHub.SubmitRequest(deviceID, req.DeviceName, req.Code) == nil {
		http.Error(w, "Too many pending requests in queue", http.StatusTooManyRequests)
		return
	}
	if s.notifyPair != nil {
		go s.notifyPair(req.DeviceName, req.Code)
	}
	writeJSON(w, http.StatusOK, map[string]any{"status": "pending"})
}

func (s *Server) handlePairPending(w http.ResponseWriter, r *http.Request) {
	if !pairing.IsLoopbackRequest(r) {
		http.Error(w, "Forbidden: host machine only", http.StatusForbidden)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(s.pairHub.GetPending())
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
	deviceName := ""
	for _, pending := range s.pairHub.GetPending() {
		if pending.DeviceID == req.DeviceID {
			deviceName = pending.DeviceName
		}
	}
	if !s.pairHub.Confirm(req.DeviceID, req.Approved) {
		http.Error(w, "unknown pairing request", http.StatusNotFound)
		return
	}
	if req.Approved {
		if err := s.paired.approve(req.DeviceID, deviceName); err != nil {
			s.logger.Error("failed to persist approved device", "err", err)
		}
	}
	writeJSON(w, http.StatusOK, map[string]any{"status": "ok"})
}

func (s *Server) handlePairCheck(w http.ResponseWriter, r *http.Request) {
	deviceID := requestDeviceID(r)
	status := "none"
	switch {
	case deviceID == "":
	case s.paired.isApproved(deviceID):
		status = "approved"
	default:
		status = s.pairHub.CheckStatus(deviceID)
	}
	writeJSON(w, http.StatusOK, map[string]any{"status": status})
}
