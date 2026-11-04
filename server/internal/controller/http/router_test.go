package httpctrl_test

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"app.duinch/adb-device-manager/internal/adapter/tunnel"
	httpctrl "app.duinch/adb-device-manager/internal/controller/http"
	"app.duinch/adb-device-manager/internal/domain"
	"app.duinch/adb-device-manager/internal/usecase"
)

type stubAdb struct{}

func (stubAdb) ListRawDevices(_ context.Context) ([]usecase.RawAdbDevice, error) {
	return []usecase.RawAdbDevice{{Serial: "127.0.0.1:15555", State: "device", Model: "SM_S926B"}}, nil
}
func (stubAdb) QuerySpecsCached(_ context.Context, _ string) (domain.DeviceSpecs, error) {
	return domain.DeviceSpecs{}, nil
}
func (stubAdb) Connect(_ context.Context, addr string) (domain.CommandResult, error) {
	return domain.CommandResult{Success: true, Stdout: "connected to " + addr}, nil
}
func (stubAdb) Disconnect(_ context.Context, _ string) (domain.CommandResult, error) {
	return domain.CommandResult{Success: true}, nil
}
func (stubAdb) Pair(_ context.Context, _, _ string) (domain.CommandResult, error) {
	return domain.CommandResult{Success: true}, nil
}
func (stubAdb) EnableTcpip(_ context.Context, _ string, _ int) (domain.CommandResult, error) {
	return domain.CommandResult{Success: true}, nil
}
func (stubAdb) Reboot(_ context.Context, _ string) (domain.CommandResult, error) {
	return domain.CommandResult{Success: true}, nil
}
func (stubAdb) RunShell(_ context.Context, _, _ string) (domain.CommandResult, error) {
	return domain.CommandResult{Success: true, Stdout: "ok"}, nil
}
func (stubAdb) CaptureScreenshot(_ context.Context, _ string) ([]byte, error) {
	return []byte{0x89, 'P', 'N', 'G'}, nil
}
func (stubAdb) GetLogcat(_ context.Context, _ string, _ int, _ string) (string, error) {
	return "sample logcat logs", nil
}
func (stubAdb) ScanMdns(_ context.Context) ([]string, error) {
	return []string{"device1 192.168.1.100:5555"}, nil
}
func (stubAdb) RestartServer(_ context.Context) error { return nil }

type stubTunnel struct {
	policy tunnel.PathPolicy
}

func (stubTunnel) ListActiveTunnels() []domain.TunnelInfo                                           { return nil }
func (stubTunnel) ServeWebSocket(net.Conn, *bufio.ReadWriter, string, int, tunnel.PathKind, string) {}
func (stubTunnel) ListClosed() ([]domain.TunnelHistoryEntry, error)                                 { return nil, nil }
func (stubTunnel) AppendClosed(domain.TunnelHistoryEntry) error                                     { return nil }
func (stubTunnel) ClearClosed() error                                                               { return nil }
func (s *stubTunnel) Policy() tunnel.PathPolicy {
	if s.policy == "" {
		return tunnel.PolicyAuto
	}
	return s.policy
}
func (s *stubTunnel) SetPolicy(p tunnel.PathPolicy) { s.policy = p }

type stubOta struct{}

func (stubOta) LoadManifest() (domain.AppVersionManifest, error) {
	return domain.AppVersionManifest{VersionCode: 6, VersionName: "0.5.0"}, nil
}
func (stubOta) OpenApk() (io.ReadCloser, int64, domain.AppVersionManifest, error) {
	return nil, 0, domain.AppVersionManifest{}, domain.NewNotFoundError("no apk")
}

func TestRouterEndpoints(t *testing.T) {
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	st := &stubTunnel{}
	router := httpctrl.NewRouter(
		usecase.NewDeviceService(stubAdb{}, st, nil),
		usecase.NewTunnelService(st, st),
		usecase.NewOtaService(stubOta{}),
		st,
		nil,
		nil,
		logger,
	)

	// 1. GET /healthz
	rec := httptest.NewRecorder()
	router.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/healthz", nil))
	if rec.Code != http.StatusOK || !strings.Contains(rec.Body.String(), `"runtime":"go"`) {
		t.Fatalf("unexpected /healthz: %d %s", rec.Code, rec.Body.String())
	}
	if rec.Header().Get("X-Content-Type-Options") != "nosniff" {
		t.Fatalf("missing security header X-Content-Type-Options")
	}

	// 2. GET / (Embedded Web UI)
	rec = httptest.NewRecorder()
	router.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/", nil))
	if rec.Code != http.StatusOK || !strings.Contains(rec.Body.String(), "ADB Device Manager") {
		t.Fatalf("unexpected / index response: %d", rec.Code)
	}

	// 3. GET /api/devices
	rec = httptest.NewRecorder()
	router.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/api/devices", nil))
	var devResp struct {
		Devices []domain.Device `json:"devices"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &devResp); err != nil || len(devResp.Devices) != 1 {
		t.Fatalf("unexpected /api/devices: err=%v body=%s", err, rec.Body.String())
	}

	// 4. POST /api/connect validation error -> 400
	rec = httptest.NewRecorder()
	router.ServeHTTP(rec, httptest.NewRequest(http.MethodPost, "/api/connect", bytes.NewBufferString(`{}`)))
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 on empty connect, got %d", rec.Code)
	}

	// 5. POST /api/connect valid -> 200
	rec = httptest.NewRecorder()
	router.ServeHTTP(rec, httptest.NewRequest(http.MethodPost, "/api/connect", bytes.NewBufferString(`{"addr":"127.0.0.1:15555"}`)))
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 on valid connect, got %d: %s", rec.Code, rec.Body.String())
	}

	// 6. GET /api/tunnel/policy
	rec = httptest.NewRecorder()
	router.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/api/tunnel/policy", nil))
	if rec.Code != http.StatusOK || !strings.Contains(rec.Body.String(), `"policy":"auto"`) {
		t.Fatalf("unexpected /api/tunnel/policy GET: %d %s", rec.Code, rec.Body.String())
	}

	// 7. POST /api/tunnel/policy invalid -> 400
	rec = httptest.NewRecorder()
	router.ServeHTTP(rec, httptest.NewRequest(http.MethodPost, "/api/tunnel/policy", bytes.NewBufferString(`{"policy":"unknown"}`)))
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 for invalid policy, got %d", rec.Code)
	}

	// 8. POST /api/tunnel/policy valid -> 200
	rec = httptest.NewRecorder()
	router.ServeHTTP(rec, httptest.NewRequest(http.MethodPost, "/api/tunnel/policy", bytes.NewBufferString(`{"policy":"remote"}`)))
	if rec.Code != http.StatusOK || !strings.Contains(rec.Body.String(), `"policy":"remote"`) {
		t.Fatalf("expected 200 for valid policy, got %d %s", rec.Code, rec.Body.String())
	}
	if st.Policy() != tunnel.PolicyRemote {
		t.Fatalf("policy was not updated in policy manager")
	}
}
