package main

import (
	"context"
	"errors"
	"net/http"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"syscall"
	"time"

	"app.duinch/adb-device-manager/internal/adapter/adb"
	zeroconfadv "app.duinch/adb-device-manager/internal/adapter/discovery"
	"app.duinch/adb-device-manager/internal/adapter/signaling"
	"app.duinch/adb-device-manager/internal/adapter/storage"
	"app.duinch/adb-device-manager/internal/adapter/tracker"
	"app.duinch/adb-device-manager/internal/adapter/tunnel"
	"app.duinch/adb-device-manager/internal/config"
	httpctrl "app.duinch/adb-device-manager/internal/controller/http"
	"app.duinch/adb-device-manager/internal/tray"
	"app.duinch/adb-device-manager/internal/usecase"
	"github.com/nchungdev/remote-tunnel/go/quicktunnel"
)

func main() {
	headless := false
	var filteredArgs []string
	for _, arg := range os.Args[1:] {
		if arg == "--headless" || arg == "-headless" {
			headless = true
		} else {
			filteredArgs = append(filteredArgs, arg)
		}
	}

	cfg, err := config.Load(filteredArgs)
	if err != nil {
		os.Stderr.WriteString("fatal config error: " + err.Error() + "\n")
		os.Exit(1)
	}

	logger := config.NewLogger(cfg.LogLevel)
	logger.Info("starting adb-device-manager (Go Hexagonal)",
		"addr", cfg.ListenAddr,
		"data_dir", cfg.DataDir,
		"adb_bin", cfg.AdbBin,
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

	_ = rtcHub.Start(appCtx, "default")

	deviceSvc := usecase.NewDeviceService(adbRunner, hub, cmdTracker)
	deviceSvc.SetHistory(historyStore)
	tunnelSvc := usecase.NewTunnelService(hub, historyStore)
	otaSvc := usecase.NewOtaService(otaStore)

	port := 8080
	if parts := strings.Split(cfg.ListenAddr, ":"); len(parts) == 2 {
		if p, err := strconv.Atoi(parts[1]); err == nil {
			port = p
		}
	}

	qt := quicktunnel.New(port)
	defer qt.Stop()

	mdnsAdv := zeroconfadv.Start("_adb-remote._tcp", port, logger)
	defer mdnsAdv.Stop()

	handler := httpctrl.NewRouter(deviceSvc, tunnelSvc, otaSvc, hub, sigHandler, qt, logger,
		httpctrl.WithPairedDevicesFile(filepath.Join(cfg.DataDir, "paired_devices.json")),
		httpctrl.WithPairRequestNotifier(notifyPairRequest))

	srv := &http.Server{
		Addr:              cfg.ListenAddr,
		Handler:           handler,
		ReadHeaderTimeout: 10 * time.Second,
	}

	ctrl := newServerController(port, cfg.ListenAddr, adbRunner, handler, rtcHub, hub, deviceSvc, logger, qt, appCancel)
	ctrl.SetServer(srv)

	// If on macOS and not in headless mode: run native menu bar tray
	if runtime.GOOS == "darwin" && !headless {
		go func() {
			if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
				logger.Error("http server terminated with error", "err", err)
			}
		}()
		trayMgr := tray.NewTrayManager(ctrl)
		trayMgr.Run()
		return
	}

	// Headless CLI mode
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		<-sigCh
		logger.Info("shutting down gracefully...")
		ctrl.Shutdown()
	}()

	if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		logger.Error("http server terminated with error", "err", err)
		os.Exit(1)
	}
	logger.Info("adb-device-manager stopped cleanly")
}

// notifyPairRequest shows a macOS notification: the phone waits on the host owner,
// who may not have the dashboard open. Values go through argv, never into the script.
func notifyPairRequest(deviceName, code string) {
	if runtime.GOOS != "darwin" {
		return
	}
	const script = `on run argv
display notification (item 2 of argv) with title (item 1 of argv)
end run`
	_ = exec.Command("osascript", "-e", script, "Yêu cầu ghép nối thiết bị",
		deviceName+" • Mã "+code+" • Mở Dashboard để duyệt").Run()
}
