package usecase

import (
	"context"
	"strings"
	"time"
)

// UsbLink reports whether the device behind alias is also plugged into this machine by
// USB. A phone only sees that some cable carries data, so this compares the serial the
// device gives over its tunnel with adb's USB devices. known is false when there is no
// working tunnel to ask; linked false then means nothing.
func (s *DeviceService) UsbLink(ctx context.Context, alias string) (linked, known bool) {
	serial := s.tunnelDeviceSerial(ctx, alias)
	if serial == "" {
		return false, false
	}
	devices, err := s.adb.ListRawDevices(ctx)
	if err != nil {
		return false, false
	}
	for _, device := range devices {
		if device.OverUSB && device.Serial == serial {
			return true, true
		}
	}
	return false, true
}

// tunnelDeviceSerial is cached by tunnel address: serials do not change, and asking again
// on every poll would show up as traffic on the connection.
func (s *DeviceService) tunnelDeviceSerial(ctx context.Context, alias string) string {
	for _, tunnel := range s.tunnels.ListActiveTunnels() {
		if tunnel.Alias != alias {
			continue
		}
		key := alias + "@" + tunnel.MappedAddr
		if cached, ok := s.serialByTunnel.Load(key); ok {
			return cached.(string)
		}
		shellCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
		defer cancel()
		res, err := s.adb.RunShell(shellCtx, tunnel.MappedAddr, "getprop ro.serialno")
		if err != nil || !res.Success {
			return ""
		}
		serial := strings.TrimSpace(res.Stdout)
		if serial != "" {
			s.serialByTunnel.Store(key, serial)
		}
		return serial
	}
	return ""
}
