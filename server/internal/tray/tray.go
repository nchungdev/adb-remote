package tray

import (
	_ "embed"
	"fmt"
	"os"

	"github.com/getlantern/systray"
)

//go:embed icon.png
var trayIconBytes []byte

type ServerController interface {
	GetPort() int
	GetRoomCode() string
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

	mWeb := systray.AddMenuItem("Open Dashboard", "Open management dashboard in browser")
	mRemote := systray.AddMenuItem("Turn on remote ADB", "Enable P2P remote connection and show QR code")

	systray.AddSeparator()

	mRestartAdb := systray.AddMenuItem("Restart ADB", "Restart local adb server process")
	mQuit := systray.AddMenuItem("Exit", "Quit application")

	go func() {
		remoteURL := fmt.Sprintf("http://127.0.0.1:%d/remote", port)
		dashboardURL := fmt.Sprintf("http://127.0.0.1:%d", port)

		for {
			select {
			case <-mWeb.ClickedCh:
				openBrowser(dashboardURL)

			case <-mRemote.ClickedCh:
				if !t.ctrl.IsRemoteActive() {
					_ = t.ctrl.StartRemote()
					mRemote.SetTitle("Turn off remote ADB")
					openBrowser(remoteURL)
					notify("ADB Device Manager", "Remote ADB Enabled", "Room: "+roomCode)
				} else {
					// Open the remote pairing dialog
					openBrowser(remoteURL)
				}

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
