package adb_test

import (
	"testing"

	"app.duinch/adb-device-manager/internal/adapter/adb"
)

func TestParseDevicesOutput(t *testing.T) {
	sample := `List of devices attached
* daemon not running; starting now at tcp:5037
* daemon started successfully
127.0.0.1:15555        device product:e2sxxx model:SM_S926B device:e2s transport_id:4
R5CX123456A            unauthorized usb:1-1 transport_id:2

`
	devs := adb.ParseDevicesOutput(sample)
	if len(devs) != 2 {
		t.Fatalf("expected 2 parsed devices, got %d: %+v", len(devs), devs)
	}

	first := devs[0]
	if first.Serial != "127.0.0.1:15555" || first.State != "device" || first.Model != "SM_S926B" || first.TransportID != "4" {
		t.Fatalf("unexpected first device: %+v", first)
	}

	second := devs[1]
	if second.Serial != "R5CX123456A" || second.State != "unauthorized" {
		t.Fatalf("unexpected second device: %+v", second)
	}
}

func TestParseDeviceSpecs(t *testing.T) {
	probeOut := `__BRAND__=samsung
__REL__=16
__SDK__=36
  status: 2
  level: 78
    inet 192.168.1.42/24 brd 192.168.1.255 scope global wlan0
`
	specs := adb.ParseDeviceSpecs(probeOut)
	if specs.Brand == nil || *specs.Brand != "samsung" {
		t.Fatalf("expected brand samsung, got %v", specs.Brand)
	}
	if specs.AndroidVersion == nil || *specs.AndroidVersion != "16" {
		t.Fatalf("expected Android 16, got %v", specs.AndroidVersion)
	}
	if specs.SDK == nil || *specs.SDK != "36" {
		t.Fatalf("expected SDK 36, got %v", specs.SDK)
	}
	if specs.Battery == nil || specs.Battery.Level != 78 || !specs.Battery.Charging {
		t.Fatalf("expected battery 78%% charging, got %+v", specs.Battery)
	}
	if specs.WifiIP == nil || *specs.WifiIP != "192.168.1.42" {
		t.Fatalf("expected wifi IP 192.168.1.42, got %v", specs.WifiIP)
	}
}

func TestParseDevicesOutput_MarksOnlyUSBDevices(t *testing.T) {
	devices := adb.ParseDevicesOutput("List of devices attached\n" +
		"SERIAL123 device usb:34603008X product:phone model:Phone device:phone transport_id:1\n" +
		"127.0.0.1:15555 device product:phone model:Phone device:phone transport_id:2\n" +
		"emulator-5554 device product:sdk model:sdk device:emu64a transport_id:3\n")
	if len(devices) != 3 || !devices[0].OverUSB || devices[1].OverUSB || devices[2].OverUSB {
		t.Fatalf("unexpected USB marks: %+v", devices)
	}
}
