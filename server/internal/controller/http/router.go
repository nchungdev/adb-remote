package httpctrl

import (
	"bufio"
	"encoding/json"
	"errors"
	"log/slog"
	"net"
	"net/http"
	"strconv"
	"time"

	"app.duinch/adb-device-manager/internal/controller/http/ui"
	"app.duinch/adb-device-manager/internal/domain"
	"app.duinch/adb-device-manager/internal/usecase"
)

type WebSocketUpgrader interface {
	ServeWebSocket(rawConn net.Conn, rw *bufio.ReadWriter, alias string, localPort int)
}

type Server struct {
	devices   *usecase.DeviceService
	tunnels   *usecase.TunnelService
	ota       *usecase.OtaService
	wsHub     WebSocketUpgrader
	signaling http.Handler
	logger    *slog.Logger
}

func NewRouter(
	devices *usecase.DeviceService,
	tunnels *usecase.TunnelService,
	ota *usecase.OtaService,
	wsHub WebSocketUpgrader,
	signaling http.Handler,
	logger *slog.Logger,
) http.Handler {
	s := &Server{
		devices:   devices,
		tunnels:   tunnels,
		ota:       ota,
		wsHub:     wsHub,
		signaling: signaling,
		logger:    logger,
	}

	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", s.handleHealthz)
	mux.HandleFunc("HEAD /healthz", s.handleHealthz)

	mux.HandleFunc("GET /{$}", s.handleIndex)
	mux.HandleFunc("GET /index.html", s.handleIndex)
	mux.HandleFunc("HEAD /{$}", s.handleHeadOK)
	mux.Handle("GET /ui/", http.StripPrefix("/ui/", http.FileServer(http.FS(ui.DistFS))))

	mux.HandleFunc("GET /download/adb-agent.apk", s.handleDownloadApk)
	mux.HandleFunc("HEAD /download/adb-agent.apk", s.handleHeadApk)
	mux.HandleFunc("GET /downloads/adb-agent.apk", s.handleDownloadApk)
	mux.HandleFunc("HEAD /downloads/adb-agent.apk", s.handleHeadApk)
	mux.HandleFunc("GET /api/downloads/adb-agent.apk", s.handleDownloadApk)
	mux.HandleFunc("HEAD /api/downloads/adb-agent.apk", s.handleHeadApk)
	mux.HandleFunc("GET /adb-agent.apk", s.handleDownloadApk)
	mux.HandleFunc("HEAD /adb-agent.apk", s.handleHeadApk)
	mux.HandleFunc("GET /api/app-version", s.handleAppVersion)

	mux.HandleFunc("GET /api/tunnel", s.handleTunnel)
	mux.HandleFunc("/api/webrtc/signal", s.handleSignaling)
	mux.HandleFunc("/api/signal", s.handleSignaling)
	mux.HandleFunc("GET /api/devices", s.handleListDevices)
	mux.HandleFunc("GET /api/screenshot", s.handleScreenshot)
	mux.HandleFunc("GET /api/logcat", s.handleLogcat)
	mux.HandleFunc("GET /api/mdns-scan", s.handleMdnsScan)

	mux.HandleFunc("POST /api/connect", s.handleConnect)
	mux.HandleFunc("POST /api/disconnect", s.handleDisconnect)
	mux.HandleFunc("POST /api/tcpip", s.handleTcpip)
	mux.HandleFunc("POST /api/pair", s.handlePair)
	mux.HandleFunc("POST /api/reboot", s.handleReboot)
	mux.HandleFunc("POST /api/shell", s.handleShell)
	mux.HandleFunc("POST /api/restart-server", s.handleRestartServer)
	mux.HandleFunc("POST /api/clear-history", s.handleClearHistory)

	return s.securityHeadersMiddleware(mux)
}

func (s *Server) securityHeadersMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("X-Frame-Options", "SAMEORIGIN")
		next.ServeHTTP(w, r)
	})
}

func (s *Server) handleHealthz(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"status":    "ok",
		"timestamp": time.Now().Unix(),
		"runtime":   "go",
	})
}

func (s *Server) handleSignaling(w http.ResponseWriter, r *http.Request) {
	if s.signaling != nil {
		s.signaling.ServeHTTP(w, r)
		return
	}
	http.Error(w, "signaling service not enabled", http.StatusNotImplemented)
}

func (s *Server) handleHeadOK(w http.ResponseWriter, _ *http.Request) {
	w.WriteHeader(http.StatusOK)
}

func (s *Server) handleIndex(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Header().Set("Content-Length", strconv.Itoa(len(ui.IndexHTML)))
	w.Header().Set("Cache-Control", "no-cache")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(ui.IndexHTML)
}

func writeJSON(w http.ResponseWriter, status int, payload any) {
	body, err := json.Marshal(payload)
	if err != nil {
		http.Error(w, "internal encoding error", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Content-Length", strconv.Itoa(len(body)))
	w.Header().Set("Cache-Control", "no-store, no-cache, must-revalidate")
	w.WriteHeader(status)
	_, _ = w.Write(body)
}

func writeDomainError(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, domain.ErrInvalidInput):
		writeJSON(w, http.StatusBadRequest, map[string]any{"success": false, "message": err.Error()})
	case errors.Is(err, domain.ErrNotFound):
		writeJSON(w, http.StatusNotFound, map[string]any{"success": false, "message": err.Error()})
	default:
		writeJSON(w, http.StatusInternalServerError, map[string]any{"success": false, "message": err.Error()})
	}
}
