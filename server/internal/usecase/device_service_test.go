package usecase_test

import (
	"bytes"
	"context"
	"errors"
	"io"
	"testing"

	"app.duinch/adb-device-manager/internal/domain"
	"app.duinch/adb-device-manager/internal/usecase"
)

type fakeAdbRunner struct {
	rawDevices   []usecase.RawAdbDevice
	specsBySer   map[string]domain.DeviceSpecs
	connectCalls []string
	discCalls    []string
}

func (f *fakeAdbRunner) ListRawDevices(_ context.Context) ([]usecase.RawAdbDevice, error) {
	return f.rawDevices, nil
}

func (f *fakeAdbRunner) QuerySpecsCached(_ context.Context, serial string) (domain.DeviceSpecs, error) {
	if s, ok := f.specsBySer[serial]; ok {
		return s, nil
	}
	return domain.DeviceSpecs{}, nil
}

func (f *fakeAdbRunner) Connect(_ context.Context, addr string) (domain.CommandResult, error) {
	f.connectCalls = append(f.connectCalls, addr)
	f.rawDevices = append(f.rawDevices, usecase.RawAdbDevice{
		Serial: addr,
		State:  "device",
		Model:  "SM_S926B",
	})
	return domain.CommandResult{Success: true, Stdout: "connected to " + addr}, nil
}

func (f *fakeAdbRunner) Disconnect(_ context.Context, addr string) (domain.CommandResult, error) {
	f.discCalls = append(f.discCalls, addr)
	return domain.CommandResult{Success: true}, nil
}

func (f *fakeAdbRunner) Pair(_ context.Context, addr, code string) (domain.CommandResult, error) {
	return domain.CommandResult{Success: true, Stdout: "paired " + addr + ":" + code}, nil
}

func (f *fakeAdbRunner) EnableTcpip(_ context.Context, _ string, port int) (domain.CommandResult, error) {
	return domain.CommandResult{Success: true, Stdout: "restarting in TCP mode", ReturnCode: port}, nil
}

func (f *fakeAdbRunner) Reboot(_ context.Context, _ string) (domain.CommandResult, error) {
	return domain.CommandResult{Success: true}, nil
}

func (f *fakeAdbRunner) RunShell(_ context.Context, _, cmd string) (domain.CommandResult, error) {
	return domain.CommandResult{Success: true, Stdout: "exec:" + cmd}, nil
}

func (f *fakeAdbRunner) CaptureScreenshot(_ context.Context, _ string) ([]byte, error) {
	return []byte{0x89, 'P', 'N', 'G'}, nil
}

func (f *fakeAdbRunner) GetLogcat(_ context.Context, _ string, _ int, _ string) (string, error) {
	return "sample logcat output", nil
}

func (f *fakeAdbRunner) ScanMdns(_ context.Context) ([]string, error) {
	return []string{"device1._adb-tls-connect._tcp 192.168.1.100:5555"}, nil
}

func (f *fakeAdbRunner) RestartServer(_ context.Context) error { return nil }

type fakeTunnelInspector struct {
	active []domain.TunnelInfo
}

func (f *fakeTunnelInspector) ListActiveTunnels() []domain.TunnelInfo { return f.active }

type fakeHistoryRepo struct {
	closed []domain.TunnelHistoryEntry
}

func (f *fakeHistoryRepo) ListClosed() ([]domain.TunnelHistoryEntry, error) { return f.closed, nil }
func (f *fakeHistoryRepo) AppendClosed(e domain.TunnelHistoryEntry) error {
	f.closed = append([]domain.TunnelHistoryEntry{e}, f.closed...)
	return nil
}
func (f *fakeHistoryRepo) ClearClosed() error {
	f.closed = nil
	return nil
}

type fakeOtaRepo struct {
	manifest domain.AppVersionManifest
	apkBytes []byte
}

func (f *fakeOtaRepo) LoadManifest() (domain.AppVersionManifest, error) { return f.manifest, nil }
func (f *fakeOtaRepo) OpenApk() (io.ReadCloser, int64, domain.AppVersionManifest, error) {
	if len(f.apkBytes) == 0 {
		return nil, 0, f.manifest, domain.NewNotFoundError("apk missing")
	}
	return io.NopCloser(bytes.NewReader(f.apkBytes)), int64(len(f.apkBytes)), f.manifest, nil
}

func TestDeviceService_AutoHealTunnelAndEnrichSpecs(t *testing.T) {
	brand := "samsung"
	rel := "16"
	adb := &fakeAdbRunner{
		specsBySer: map[string]domain.DeviceSpecs{
			"127.0.0.1:15555": {
				Brand:          &brand,
				AndroidVersion: &rel,
				Battery:        &domain.BatteryInfo{Level: 85, Charging: true},
			},
		},
	}
	tunnels := &fakeTunnelInspector{
		active: []domain.TunnelInfo{{
			SessionID:    "sess-1",
			Alias:        "samsung-SM-S926B",
			LocalAdbPort: 35503,
			MappedAddr:   "127.0.0.1:15555",
			ConnectedAt:  1700000000,
			BytesBridged: 4096,
		}},
	}

	svc := usecase.NewDeviceService(adb, tunnels, nil)
	devs, active, err := svc.ListDevices(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(active) != 1 || len(devs) != 1 {
		t.Fatalf("expected 1 active tunnel and 1 device, got %d / %d", len(active), len(devs))
	}
	if len(adb.connectCalls) != 1 || adb.connectCalls[0] != "127.0.0.1:15555" {
		t.Fatalf("expected auto-heal connect to 127.0.0.1:15555, got %v", adb.connectCalls)
	}
	if !devs[0].IsTunnel || devs[0].Brand == nil || *devs[0].Brand != "samsung" {
		t.Fatalf("expected enriched tunnel device, got %+v", devs[0])
	}
}

func TestDeviceService_ValidationErrors(t *testing.T) {
	svc := usecase.NewDeviceService(&fakeAdbRunner{}, &fakeTunnelInspector{}, nil)
	ctx := context.Background()

	if _, err := svc.Connect(ctx, "   "); !errors.Is(err, domain.ErrInvalidInput) {
		t.Fatalf("expected ErrInvalidInput on empty connect, got %v", err)
	}
	if _, err := svc.Pair(ctx, "127.0.0.1:40000", ""); !errors.Is(err, domain.ErrInvalidInput) {
		t.Fatalf("expected ErrInvalidInput on empty pair code, got %v", err)
	}
	if _, err := svc.RunShell(ctx, "ser", " "); !errors.Is(err, domain.ErrInvalidInput) {
		t.Fatalf("expected ErrInvalidInput on empty shell cmd, got %v", err)
	}
	if _, err := svc.Screenshot(ctx, ""); !errors.Is(err, domain.ErrInvalidInput) {
		t.Fatalf("expected ErrInvalidInput on empty screenshot serial, got %v", err)
	}
}

func TestTunnelAndOtaServices(t *testing.T) {
	ended := int64(1700000100)
	histRepo := &fakeHistoryRepo{
		closed: []domain.TunnelHistoryEntry{{
			SessionID: "old-1", Alias: "S24", ConnectedAt: 1700000000, EndedAt: &ended,
		}},
	}
	insp := &fakeTunnelInspector{
		active: []domain.TunnelInfo{{SessionID: "live-1", Alias: "S24", MappedAddr: "127.0.0.1:15555"}},
	}
	tsvc := usecase.NewTunnelService(insp, histRepo)
	merged := tsvc.ListHistory()
	if len(merged) != 2 || !merged[0].Active || merged[1].Active {
		t.Fatalf("expected active entry followed by closed entry, got %+v", merged)
	}
	if err := tsvc.ClearHistory(); err != nil {
		t.Fatalf("clear history failed: %v", err)
	}
	if len(tsvc.ListHistory()) != 1 {
		t.Fatalf("expected only active entry remaining after clear")
	}

	otaSvc := usecase.NewOtaService(&fakeOtaRepo{manifest: domain.AppVersionManifest{VersionCode: 6}})
	if _, _, _, err := otaSvc.OpenApk(); !errors.Is(err, domain.ErrNotFound) {
		t.Fatalf("expected ErrNotFound when apk missing, got %v", err)
	}
}
