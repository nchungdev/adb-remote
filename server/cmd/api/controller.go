package main

import (
	"context"
	"log/slog"
	"net/http"
	"sync"
	"time"

	"app.duinch/adb-device-manager/internal/adapter/adb"
	"app.duinch/adb-device-manager/internal/adapter/tunnel"
	"app.duinch/adb-device-manager/internal/usecase"
	"github.com/nchungdev/remote-tunnel/go/quicktunnel"
)

type serverController struct {
	port       int
	roomCode   string
	adbRunner  *adb.CLIRunner
	handler    http.Handler
	listenAddr string
	rtcHub     *tunnel.WebRtcHub
	hub        *tunnel.Hub
	deviceSvc  *usecase.DeviceService
	logger     *slog.Logger
	qt         *quicktunnel.QuickTunnel

	mu      sync.Mutex
	srv     *http.Server
	running bool
	cancel  context.CancelFunc
}

func newServerController(
	port int,
	listenAddr string,
	roomCode string,
	adbRunner *adb.CLIRunner,
	handler http.Handler,
	rtcHub *tunnel.WebRtcHub,
	hub *tunnel.Hub,
	deviceSvc *usecase.DeviceService,
	logger *slog.Logger,
	qt *quicktunnel.QuickTunnel,
	cancel context.CancelFunc,
) *serverController {
	return &serverController{
		port:       port,
		listenAddr: listenAddr,
		roomCode:   roomCode,
		adbRunner:  adbRunner,
		handler:    handler,
		rtcHub:     rtcHub,
		hub:        hub,
		deviceSvc:  deviceSvc,
		logger:     logger,
		qt:         qt,
		cancel:     cancel,
		running:    true,
	}
}

func (s *serverController) GetTunnelURL() string {
	if s.qt != nil && s.qt.IsActive() {
		return s.qt.GetURL()
	}
	return ""
}

func (s *serverController) GetPort() int {
	return s.port
}

func (s *serverController) GetRoomCode() string {
	return s.roomCode
}

func (s *serverController) IsRemoteActive() bool {
	return s.deviceSvc.IsRemoteActive()
}

func (s *serverController) StartRemote() error {
	s.deviceSvc.SetRemoteActive(true)
	return nil
}

func (s *serverController) StopRemote() error {
	s.deviceSvc.SetRemoteActive(false)
	if s.rtcHub != nil {
		s.rtcHub.CloseAll()
	}
	return nil
}

func (s *serverController) RestartAdb() error {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	return s.adbRunner.RestartServer(ctx)
}

func (s *serverController) SetServer(srv *http.Server) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.srv = srv
	s.running = true
}

func (s *serverController) Shutdown() {
	s.mu.Lock()
	s.running = false
	s.cancel()
	if s.rtcHub != nil {
		s.rtcHub.CloseAll()
	}
	if s.hub != nil {
		s.hub.CloseAll()
	}
	if s.srv != nil {
		ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
		defer cancel()
		_ = s.srv.Shutdown(ctx)
	}
	s.mu.Unlock()
}
