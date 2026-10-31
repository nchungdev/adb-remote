package main

import (
	"context"
	"errors"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"app.duinch/adb-device-manager/internal/adapter/adb"
	"app.duinch/adb-device-manager/internal/adapter/signaling"
	"app.duinch/adb-device-manager/internal/adapter/storage"
	"app.duinch/adb-device-manager/internal/adapter/tracker"
	"app.duinch/adb-device-manager/internal/adapter/tunnel"
	"app.duinch/adb-device-manager/internal/config"
	httpctrl "app.duinch/adb-device-manager/internal/controller/http"
	"app.duinch/adb-device-manager/internal/usecase"
)

func main() {
	cfg, err := config.Load(os.Args[1:])
	if err != nil {
		os.Stderr.WriteString("fatal config error: " + err.Error() + "\n")
		os.Exit(1)
	}

	logger := config.NewLogger(cfg.LogLevel)
	logger.Info("starting adb-device-manager (Go Hexagonal)",
		"addr", cfg.ListenAddr,
		"data_dir", cfg.DataDir,
		"adb_bin", cfg.AdbBin,
		"base_tunnel_port", cfg.BaseTunnelPort,
	)

	adbRunner := adb.NewCLIRunner(cfg.AdbBin)
	historyStore, err := storage.NewHistoryFileStore(cfg.DataDir)
	if err != nil {
		logger.Error("failed to initialize history store", "err", err)
		os.Exit(1)
	}
	otaStore := storage.NewOtaFileStore(cfg.DataDir)

	autoConnect := func(ctx context.Context, mappedAddr string) {
		_, _ = adbRunner.Disconnect(ctx, mappedAddr)
		time.Sleep(150 * time.Millisecond)
		_, _ = adbRunner.Connect(ctx, mappedAddr)
	}

	autoDisconnect := func(ctx context.Context, mappedAddr string) {
		_, _ = adbRunner.Disconnect(ctx, mappedAddr)
	}

	hub := tunnel.NewHub(cfg.BaseTunnelPort, historyStore, autoConnect, logger)
	hub.SetAutoDisconnect(autoDisconnect)
	cmdTracker := tracker.NewCommandTracker()
	cmdTracker.SetBroadcast(hub.BroadcastCommand)

	sigHub := signaling.NewMessageHub()
	sigHandler := signaling.NewHTTPHandler(sigHub, "")
	rtcHub := tunnel.NewWebRtcHub(hub, sigHub, []string{"stun:stun.l.google.com:19302"}, logger)

	appCtx, appCancel := context.WithCancel(context.Background())
	defer appCancel()
	if err := rtcHub.Start(appCtx, "default"); err != nil {
		logger.Warn("failed to start webrtc hub signaling listener", "err", err)
	}

	deviceSvc := usecase.NewDeviceService(adbRunner, hub, cmdTracker)
	deviceSvc.SetHistory(historyStore)
	tunnelSvc := usecase.NewTunnelService(hub, historyStore)
	otaSvc := usecase.NewOtaService(otaStore)

	handler := httpctrl.NewRouter(deviceSvc, tunnelSvc, otaSvc, hub, sigHandler, logger)
	srv := &http.Server{
		Addr:              cfg.ListenAddr,
		Handler:           handler,
		ReadHeaderTimeout: 10 * time.Second,
	}

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		<-sigCh
		logger.Info("shutting down gracefully...")
		appCancel()
		rtcHub.CloseAll()
		hub.CloseAll()
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = srv.Shutdown(ctx)
	}()

	if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		logger.Error("http server terminated with error", "err", err)
		os.Exit(1)
	}
	logger.Info("adb-device-manager stopped cleanly")
}
