package usecase

import (
	"context"
	"errors"
	"testing"

	"app.duinch/adb-device-manager/internal/domain"
)

type usbLinkAdb struct {
	AdbRunner
	devices    []RawAdbDevice
	serial     string
	shellErr   error
	shellCalls int
}

func (a *usbLinkAdb) ListRawDevices(context.Context) ([]RawAdbDevice, error) { return a.devices, nil }

func (a *usbLinkAdb) RunShell(context.Context, string, string) (domain.CommandResult, error) {
	a.shellCalls++
	return domain.CommandResult{Success: a.shellErr == nil, Stdout: a.serial + "\n"}, a.shellErr
}

type usbLinkTunnels []domain.TunnelInfo

func (t usbLinkTunnels) ListActiveTunnels() []domain.TunnelInfo { return t }

func TestUsbLink(t *testing.T) {
	phone := usbLinkTunnels{{Alias: "phone", MappedAddr: "127.0.0.1:15555"}}
	cases := []struct {
		name          string
		adb           *usbLinkAdb
		tunnels       usbLinkTunnels
		linked, known bool
	}{
		{
			name:    "same serial on a USB port",
			adb:     &usbLinkAdb{serial: "SERIAL123", devices: []RawAdbDevice{{Serial: "SERIAL123", OverUSB: true}}},
			tunnels: phone, linked: true, known: true,
		},
		{
			name:    "cable to this machine is another phone",
			adb:     &usbLinkAdb{serial: "SERIAL123", devices: []RawAdbDevice{{Serial: "OTHER", OverUSB: true}}},
			tunnels: phone, known: true,
		},
		{
			name:    "same serial over the network is not a cable",
			adb:     &usbLinkAdb{serial: "SERIAL123", devices: []RawAdbDevice{{Serial: "SERIAL123"}}},
			tunnels: phone, known: true,
		},
		{
			name:    "no tunnel to ask",
			adb:     &usbLinkAdb{serial: "SERIAL123", devices: []RawAdbDevice{{Serial: "SERIAL123", OverUSB: true}}},
			tunnels: nil,
		},
		{
			name:    "device did not answer",
			adb:     &usbLinkAdb{shellErr: errors.New("offline"), devices: []RawAdbDevice{{Serial: "SERIAL123", OverUSB: true}}},
			tunnels: phone,
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			svc := NewDeviceService(tc.adb, tc.tunnels, nil)
			linked, known := svc.UsbLink(context.Background(), "phone")
			if linked != tc.linked || known != tc.known {
				t.Fatalf("linked=%v known=%v, want %v %v", linked, known, tc.linked, tc.known)
			}
		})
	}
}

func TestUsbLink_AsksTheDeviceOnlyOnce(t *testing.T) {
	adb := &usbLinkAdb{serial: "SERIAL123"}
	svc := NewDeviceService(adb, usbLinkTunnels{{Alias: "phone", MappedAddr: "127.0.0.1:15555"}}, nil)
	svc.UsbLink(context.Background(), "phone")
	svc.UsbLink(context.Background(), "phone")
	if adb.shellCalls != 1 {
		t.Fatalf("shell calls = %d, want 1: each one is traffic on the connection", adb.shellCalls)
	}
}
