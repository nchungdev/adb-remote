package tray

import (
	_ "embed"
	"fmt"
	"os"
	"time"

	"github.com/getlantern/systray"
)

//go:embed icon.png
var trayIconBytes []byte

type ServerController interface {
	GetPort() int
	GetRoomCode() string
	GetTunnelURL() string
	IsRemoteActive() bool
	StartRemote() error
	StopRemote() error
	RestartAdb() error
	Shutdown()
}

type TrayManager struct {
	ctrl ServerController
}

func NewTrayManager(ctrl ServerController) *TrayManager {
	return &TrayManager{ctrl: ctrl}
}

func (t *TrayManager) Run() {
	systray.Run(t.onReady, t.onExit)
}

func (t *TrayManager) onReady() {
	if len(trayIconBytes) > 0 {
		systray.SetTemplateIcon(trayIconBytes, trayIconBytes)
	} else {
		systray.SetTitle("⚡")
	}
	systray.SetTooltip("ADB Device Manager")

	port := t.ctrl.GetPort()
	roomCode := t.ctrl.GetRoomCode()

	// Status Header
	mStatus := systray.AddMenuItem(fmt.Sprintf("● Server Running (:%d)", port), "Server Status")
	mStatus.Disable()

	systray.AddSeparator()

	// Primary actions
	mDashboard := systray.AddMenuItem("Open Dashboard...", "Open management dashboard in browser")
	mShowQR := systray.AddMenuItem("Pair Device (QR Code)...", "Open device pairing QR code")

	systray.AddSeparator()

	// Quick Copy actions (clean and compact)
	mTunnel := systray.AddMenuItem("Copy Cloudflare URL", "Copy ephemeral public URL")
	mRoom := systray.AddMenuItem(fmt.Sprintf("Copy P2P Code (%s)", roomCode), "Copy WebRTC P2P Room Code")
	lanIP := getLocalLANIP()
	mLAN := systray.AddMenuItem(fmt.Sprintf("Copy LAN Address (%s)", lanIP), "Copy local HTTP/WebSocket URL")

	systray.AddSeparator()

	// Controls & Settings
	mRestartAdb := systray.AddMenuItem("Restart ADB", "Restart local adb server process")

	mSettings := systray.AddMenuItem("Settings", "Server Configuration")
	mSetPort := mSettings.AddSubMenuItem(fmt.Sprintf("Port (:%d)...", port), "Server listening port")
	mSetPort.Disable()

	systray.AddSeparator()
	mQuit := systray.AddMenuItem("Quit ADB Device Manager", "Shut down server daemon")

	// Background ticker to update Cloudflare tunnel status
	go func() {
		ticker := time.NewTicker(2 * time.Second)
		defer ticker.Stop()
		for range ticker.C {
			url := t.ctrl.GetTunnelURL()
			if url != "" {
				mTunnel.SetTitle("Copy Cloudflare URL")
				mTunnel.SetTooltip(url)
				mTunnel.Enable()
			} else {
				mTunnel.SetTitle("Cloudflare: Off")
				mTunnel.Disable()
			}
		}
	}()

	// Event handling loop
	go func() {
		remoteURL := fmt.Sprintf("http://127.0.0.1:%d/remote", port)
		dashboardURL := fmt.Sprintf("http://127.0.0.1:%d", port)

		for {
			select {
			case <-mDashboard.ClickedCh:
				openBrowser(dashboardURL)

			case <-mShowQR.ClickedCh:
				openBrowser(remoteURL)

			case <-mTunnel.ClickedCh:
				url := t.ctrl.GetTunnelURL()
				if url != "" {
					copyToClipboard(url)
				}

			case <-mRoom.ClickedCh:
				copyToClipboard(roomCode)

			case <-mLAN.ClickedCh:
				lanURL := fmt.Sprintf("http://%s:%d", lanIP, port)
				copyToClipboard(lanURL)

			case <-mRestartAdb.ClickedCh:
				if err := t.ctrl.RestartAdb(); err != nil {
					notify("ADB Error", "Restart failed", err.Error())
				} else {
					notify("ADB Device Manager", "Success", "ADB server restarted")
				}

			case <-mQuit.ClickedCh:
				systray.Quit()
				t.ctrl.Shutdown()
				os.Exit(0)
			}
		}
	}()
}

func (t *TrayManager) onExit() {
	t.ctrl.Shutdown()
}
