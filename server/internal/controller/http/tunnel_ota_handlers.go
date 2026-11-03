package httpctrl

import (
	"fmt"
	"io"
	"net"
	"net/http"
	"strconv"
	"strings"

	"app.duinch/adb-device-manager/internal/adapter/tunnel"
)

func (s *Server) handleTunnel(w http.ResponseWriter, r *http.Request) {
	if !strings.Contains(strings.ToLower(r.Header.Get("Upgrade")), "websocket") {
		writeJSON(w, http.StatusOK, map[string]any{
			"status":  "tunnel_endpoint_ready",
			"tunnels": s.tunnels.ListActive(),
		})
		return
	}

	wsKey := strings.TrimSpace(r.Header.Get("Sec-WebSocket-Key"))
	if wsKey == "" {
		http.Error(w, "Missing Sec-WebSocket-Key", http.StatusBadRequest)
		return
	}

	hj, ok := w.(http.Hijacker)
	if !ok {
		http.Error(w, "WebSocket hijack unsupported", http.StatusInternalServerError)
		return
	}

	rawConn, rw, err := hj.Hijack()
	if err != nil {
		s.logger.Error("websocket hijack failed", "err", err)
		return
	}

	acceptVal := tunnel.ComputeAcceptKey(wsKey)
	handshake := fmt.Sprintf(
		"HTTP/1.1 101 Switching Protocols\r\n"+
			"Upgrade: websocket\r\n"+
			"Connection: Upgrade\r\n"+
			"Sec-WebSocket-Accept: %s\r\n\r\n",
		acceptVal,
	)
	if _, err := rawConn.Write([]byte(handshake)); err != nil {
		_ = rawConn.Close()
		return
	}

	alias := r.Header.Get("X-Device-Alias")
	localPort, err := strconv.Atoi(strings.TrimSpace(r.Header.Get("X-Local-Adb-Port")))
	if err != nil || localPort <= 0 {
		localPort = 5555
	}

	host, _, _ := net.SplitHostPort(r.RemoteAddr)
	s.wsHub.ServeWebSocket(rawConn, rw, alias, localPort, tunnel.PathForHost(host))
}

func (s *Server) handleClearHistory(w http.ResponseWriter, _ *http.Request) {
	if err := s.tunnels.ClearHistory(); err != nil {
		writeDomainError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"success": true})
}

func (s *Server) handleAppVersion(w http.ResponseWriter, _ *http.Request) {
	meta, err := s.ota.GetManifest()
	if err != nil {
		writeDomainError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, meta)
}

func (s *Server) handleHeadApk(w http.ResponseWriter, r *http.Request) {
	s.serveApk(w, r, true)
}

func (s *Server) handleDownloadApk(w http.ResponseWriter, r *http.Request) {
	s.serveApk(w, r, false)
}

func (s *Server) serveApk(w http.ResponseWriter, _ *http.Request, headOnly bool) {
	rc, size, meta, err := s.ota.OpenApk()
	if err != nil {
		writeDomainError(w, err)
		return
	}
	defer func() { _ = rc.Close() }()

	verName := meta.VersionName
	if verName == "" {
		verName = "latest"
	}

	w.Header().Set("Content-Type", "application/vnd.android.package-archive")
	w.Header().Set("Content-Disposition", fmt.Sprintf(`attachment; filename="adb-agent-v%s.apk"`, verName))
	w.Header().Set("Content-Length", strconv.FormatInt(size, 10))
	w.Header().Set("Cache-Control", "no-cache")
	w.WriteHeader(http.StatusOK)

	if !headOnly {
		_, _ = io.Copy(w, rc)
	}
}
