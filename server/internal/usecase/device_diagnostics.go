package usecase

import (
	"context"
	"fmt"
	"strings"
	"time"

	"app.duinch/adb-device-manager/internal/domain"
)

func (s *DeviceService) Screenshot(ctx context.Context, serial string) ([]byte, error) {
	cleanSerial := strings.TrimSpace(serial)
	if cleanSerial == "" {
		return nil, domain.NewValidationError("missing serial")
	}
	var done func(bool, string, string)
	if s.auditor != nil {
		done = s.auditor.RecordStart(cleanSerial, "adb exec-out screencap -p", "screencap")
	}
	bytes, err := s.adb.CaptureScreenshot(ctx, cleanSerial)
	if done != nil {
		if err == nil {
			done(true, fmt.Sprintf("screenshot captured (%d bytes)", len(bytes)), "")
		} else {
			done(false, "", err.Error())
		}
	}
	return bytes, err
}

func (s *DeviceService) Logcat(ctx context.Context, serial string, lines int, filter string) (string, error) {
	cleanSerial := strings.TrimSpace(serial)
	if cleanSerial == "" {
		return "", domain.NewValidationError("missing serial")
	}
	if lines <= 0 {
		lines = 200
	} else if lines > 2000 {
		lines = 2000
	}
	var done func(bool, string, string)
	if s.auditor != nil {
		done = s.auditor.RecordStart(cleanSerial, fmt.Sprintf("adb logcat -t %d", lines), "logcat")
	}
	out, err := s.adb.GetLogcat(ctx, cleanSerial, lines, filter)
	if done != nil {
		if err == nil {
			done(true, fmt.Sprintf("logcat retrieved (%d bytes)", len(out)), "")
		} else {
			done(false, "", err.Error())
		}
	}
	return out, err
}

func (s *DeviceService) RestartServer(ctx context.Context) error {
	var done func(bool, string, string)
	if s.auditor != nil {
		done = s.auditor.RecordStart("server", "adb kill-server && start-server", "system")
	}
	err := s.adb.RestartServer(ctx)
	if done != nil {
		if err == nil {
			done(true, "server restarted", "")
		} else {
			done(false, "", err.Error())
		}
	}
	return err
}

func (s *DeviceService) SetRoomCode(code string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.roomCode = code
}

func (s *DeviceService) GetRoomCode() string {
	s.mu.RLock()
	defer s.mu.RUnlock()
	if s.roomCode == "" {
		return "883-716"
	}
	return s.roomCode
}

func (s *DeviceService) SetRemoteActive(active bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.remoteActive = active
}

func (s *DeviceService) IsRemoteActive() bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.remoteActive
}

func (s *DeviceService) GetLastTunnelURL() string {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.lastTunnelURL
}

func (s *DeviceService) BroadcastRemoteTunnelState(ctx context.Context, tunnelURL, roomCode string, active bool) {
	s.mu.Lock()
	s.lastTunnelURL = tunnelURL
	s.remoteActive = active
	s.mu.Unlock()

	raw, err := s.adb.ListRawDevices(ctx)
	if err != nil {
		return
	}

	for _, d := range raw {
		if strings.Contains(d.Serial, ":") || strings.Contains(d.Serial, ".") || d.State != "device" {
			continue
		}
		go func(serial string) {
			bctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer cancel()
			activeFlag := "false"
			if active {
				activeFlag = "true"
			}
			cmd := fmt.Sprintf("am broadcast -a app.duinch.adbagent.ACTION_REMOTE_TUNNEL_STATE --es url %q --es room %q --ez active %s",
				tunnelURL, roomCode, activeFlag)
			_, _ = s.adb.RunShell(bctx, serial, cmd)
		}(d.Serial)
	}
}

func (s *DeviceService) SetActiveRemoteDevice(dev string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.activeRemoteDev = dev
}

func (s *DeviceService) GetActiveRemoteDevice() string {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.activeRemoteDev
}
