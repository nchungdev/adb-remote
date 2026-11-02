package httpctrl_test

import (
	"bytes"
	"io"
	"log/slog"
	"net/http/httptest"
	"testing"

	httpctrl "app.duinch/adb-device-manager/internal/controller/http"
	"app.duinch/adb-device-manager/internal/usecase"
)

// Uses fake ADB and fake history exclusively: reboot/restart/clear never touch a host.
func TestFeatureRouteMatrix(t *testing.T) {
	st := stubTunnel{}
	router := httpctrl.NewRouter(usecase.NewDeviceService(stubAdb{}, st, nil),
		usecase.NewTunnelService(st, st), usecase.NewOtaService(stubOta{}), st, nil, nil,
		slog.New(slog.NewTextHandler(io.Discard, nil)))
	cases := []struct {
		name, method, path, body string
		status                   int
	}{
		{"health-head", "HEAD", "/healthz", "", 200},
		{"devices", "GET", "/api/devices", "", 200},
		{"screenshot", "GET", "/api/screenshot?serial=qa-emulator", "", 200},
		{"logs", "GET", "/api/logcat?serial=qa-emulator&lines=5", "", 200},
		{"mdns", "GET", "/api/mdns-scan", "", 200},
		{"connect", "POST", "/api/connect", `{"addr":"127.0.0.1:15555"}`, 200},
		{"disconnect", "POST", "/api/disconnect", `{"addr":"127.0.0.1:15555"}`, 200},
		{"pair", "POST", "/api/pair", `{"addr":"127.0.0.1:15555","code":"123456"}`, 200},
		{"pair-empty", "POST", "/api/pair", `{}`, 400},
		{"tcpip", "POST", "/api/tcpip", `{"serial":"qa-emulator","port":5555}`, 200},
		{"reboot-fake", "POST", "/api/reboot", `{"serial":"qa-emulator"}`, 200},
		{"restart-fake", "POST", "/api/restart-server", `{}`, 200},
		{"shell", "POST", "/api/shell", `{"serial":"qa-emulator","cmd":"echo qa"}`, 200},
		{"shell-empty", "POST", "/api/shell", `{"serial":"qa-emulator"}`, 400},
		{"clear-fake-history", "POST", "/api/clear-history", `{}`, 200},
		{"ota-manifest", "GET", "/api/app-version", "", 200},
		{"ota-missing", "GET", "/download/adb-agent.apk", "", 404},
		{"signaling-unavailable", "GET", "/api/webrtc/signal", "", 501},
		{"bad-method", "GET", "/api/reboot", "", 405},
		{"unknown-route", "GET", "/qa-no-route", "", 404},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			r := httptest.NewRecorder()
			req := httptest.NewRequest(c.method, c.path, bytes.NewBufferString(c.body))
			req.RemoteAddr = "127.0.0.1:54321"
			router.ServeHTTP(r, req)
			if r.Code != c.status {
				t.Fatalf("status=%d want=%d body=%s", r.Code, c.status, r.Body.String())
			}
			if r.Header().Get("X-Content-Type-Options") != "nosniff" {
				t.Fatal("missing security header")
			}
		})
	}

	t.Run("reject-remote-proxied-shell", func(t *testing.T) {
		r := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/shell", bytes.NewBufferString(`{"serial":"qa-emulator","cmd":"id"}`))
		req.RemoteAddr = "127.0.0.1:54321"
		req.Header.Set("Cf-Connecting-Ip", "203.0.113.50")
		router.ServeHTTP(r, req)
		if r.Code != 403 {
			t.Fatalf("expected 403 for proxied shell request, got %d", r.Code)
		}
	})
}
