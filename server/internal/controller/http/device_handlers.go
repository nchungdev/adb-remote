package httpctrl

import (
	"encoding/json"
	"io"
	"net/http"
	"strconv"

	"github.com/nchungdev/remote-tunnel/go/pairing"
)

const maxJSONBodyBytes = 64 * 1024

func decodeJSONBody(r *http.Request, dst any) error {
	defer func() { _ = r.Body.Close() }()
	limited := io.LimitReader(r.Body, maxJSONBodyBytes)
	raw, err := io.ReadAll(limited)
	if err != nil {
		return err
	}
	if len(raw) == 0 {
		return nil
	}
	return json.Unmarshal(raw, dst)
}

func (s *Server) handleListDevices(w http.ResponseWriter, r *http.Request) {
	devices, activeTunnels, err := s.devices.ListDevices(r.Context())
	if err != nil {
		writeDomainError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"devices": devices,
		"tunnels": activeTunnels,
		"history": s.tunnels.ListHistory(),
	})
}

func (s *Server) handleScreenshot(w http.ResponseWriter, r *http.Request) {
	serial := r.URL.Query().Get("serial")
	pngData, err := s.devices.Screenshot(r.Context(), serial)
	if err != nil {
		writeDomainError(w, err)
		return
	}
	w.Header().Set("Content-Type", "image/png")
	w.Header().Set("Content-Length", strconv.Itoa(len(pngData)))
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(pngData)
}

func (s *Server) handleLogcat(w http.ResponseWriter, r *http.Request) {
	serial := r.URL.Query().Get("serial")
	linesStr := r.URL.Query().Get("lines")
	filter := r.URL.Query().Get("filter")
	lines := 200
	if linesStr != "" {
		if val, err := strconv.Atoi(linesStr); err == nil && val > 0 {
			lines = val
		}
	}
	logs, err := s.devices.Logcat(r.Context(), serial, lines, filter)
	if err != nil {
		writeDomainError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"serial": serial,
		"lines":  lines,
		"filter": filter,
		"logs":   logs,
	})
}

func (s *Server) handleConnect(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Addr string `json:"addr"`
	}
	_ = decodeJSONBody(r, &req)
	res, err := s.devices.Connect(r.Context(), req.Addr)
	if err != nil {
		writeDomainError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, res)
}

func (s *Server) handleDisconnect(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Addr string `json:"addr"`
	}
	_ = decodeJSONBody(r, &req)
	res, err := s.devices.Disconnect(r.Context(), req.Addr)
	if err != nil {
		writeDomainError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, res)
}

func (s *Server) handleTcpip(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Serial      string `json:"serial"`
		Port        int    `json:"port"`
		AutoConnect bool   `json:"auto_connect"`
	}
	_ = decodeJSONBody(r, &req)
	if req.Port == 0 {
		req.Port = 5555
	}
	res, err := s.devices.EnableTcpip(r.Context(), req.Serial, req.Port, req.AutoConnect)
	if err != nil {
		writeDomainError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, res)
}

func (s *Server) handleMdnsScan(w http.ResponseWriter, r *http.Request) {
	services, err := s.devices.ScanMdns(r.Context())
	if err != nil {
		writeDomainError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"services": services,
	})
}

func (s *Server) handlePair(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Addr string `json:"addr"`
		Code string `json:"code"`
	}
	_ = decodeJSONBody(r, &req)
	res, err := s.devices.Pair(r.Context(), req.Addr, req.Code)
	if err != nil {
		writeDomainError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, res)
}

func (s *Server) handleReboot(w http.ResponseWriter, r *http.Request) {
	if !pairing.IsLoopbackRequest(r) {
		http.Error(w, "Forbidden: host loopback required", http.StatusForbidden)
		return
	}
	var req struct {
		Serial string `json:"serial"`
	}
	_ = decodeJSONBody(r, &req)
	res, err := s.devices.Reboot(r.Context(), req.Serial)
	if err != nil {
		writeDomainError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, res)
}

func (s *Server) handleShell(w http.ResponseWriter, r *http.Request) {
	if !pairing.IsLoopbackRequest(r) {
		http.Error(w, "Forbidden: host loopback required", http.StatusForbidden)
		return
	}
	var req struct {
		Serial string `json:"serial"`
		Cmd    string `json:"cmd"`
	}
	_ = decodeJSONBody(r, &req)
	res, err := s.devices.RunShell(r.Context(), req.Serial, req.Cmd)
	if err != nil {
		writeDomainError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, res)
}

func (s *Server) handleRestartServer(w http.ResponseWriter, r *http.Request) {
	if !pairing.IsLoopbackRequest(r) {
		http.Error(w, "Forbidden: host loopback required", http.StatusForbidden)
		return
	}
	if err := s.devices.RestartServer(r.Context()); err != nil {
		writeDomainError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"success": true,
		"message": "Đã khởi động lại ADB Server thành công.",
	})
}
