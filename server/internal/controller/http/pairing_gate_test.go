package httpctrl_test

import (
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	httpctrl "app.duinch/adb-device-manager/internal/controller/http"
	"app.duinch/adb-device-manager/internal/usecase"
)

const lanAddr = "192.168.1.50:40000"
const phoneSecret = "phone-secret-0123456789"

func newPairingRouter(t *testing.T, storePath string) http.Handler {
	t.Helper()
	st := stubTunnel{}
	return httpctrl.NewRouter(usecase.NewDeviceService(stubAdb{}, st, nil),
		usecase.NewTunnelService(st, st), usecase.NewOtaService(stubOta{}), st, nil, nil,
		slog.New(slog.NewTextHandler(io.Discard, nil)), httpctrl.WithPairedDevicesFile(storePath))
}

func call(h http.Handler, method, path, remote, secret, body string, headers ...string) *httptest.ResponseRecorder {
	req := httptest.NewRequest(method, path, strings.NewReader(body))
	req.RemoteAddr = remote
	if secret != "" {
		req.Header.Set("Authorization", "Bearer "+secret)
	}
	for i := 0; i+1 < len(headers); i += 2 {
		req.Header.Set(headers[i], headers[i+1])
	}
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	return rec
}

func checkStatus(t *testing.T, h http.Handler, secret string) string {
	t.Helper()
	var out struct {
		Status string `json:"status"`
	}
	if err := json.Unmarshal(call(h, "GET", "/api/pair/check", lanAddr, secret, "").Body.Bytes(), &out); err != nil {
		t.Fatal(err)
	}
	return out.Status
}

func approveFirstPending(t *testing.T, h http.Handler, approved bool) {
	t.Helper()
	var pending []struct {
		DeviceID string `json:"device_id"`
	}
	if err := json.Unmarshal(call(h, "GET", "/api/pair/pending", "127.0.0.1:1", "", "").Body.Bytes(), &pending); err != nil || len(pending) != 1 {
		t.Fatalf("pending = %+v, err %v", pending, err)
	}
	body, _ := json.Marshal(map[string]any{"device_id": pending[0].DeviceID, "approved": approved})
	if rec := call(h, "POST", "/api/pair/confirm", "127.0.0.1:1", "", string(body)); rec.Code != 200 {
		t.Fatalf("confirm = %d", rec.Code)
	}
}

func TestTunnel_RejectedUntilHostApproves(t *testing.T) {
	h := newPairingRouter(t, filepath.Join(t.TempDir(), "paired.json"))

	if rec := call(h, "GET", "/api/tunnel", lanAddr, phoneSecret, ""); rec.Code != 401 {
		t.Fatalf("unapproved tunnel = %d, want 401", rec.Code)
	}
	if rec := call(h, "POST", "/api/webrtc/signal?room=r&peer=p", lanAddr, phoneSecret, "{}"); rec.Code != 401 {
		t.Fatalf("unapproved signal = %d, want 401", rec.Code)
	}
	if rec := call(h, "POST", "/api/pair/request", lanAddr, phoneSecret, `{"device_name":"phone","code":"123-456"}`); rec.Code != 200 {
		t.Fatalf("pair request = %d", rec.Code)
	}
	if got := checkStatus(t, h, phoneSecret); got != "pending" {
		t.Fatalf("status = %q, want pending", got)
	}

	approveFirstPending(t, h, true)

	if got := checkStatus(t, h, phoneSecret); got != "approved" {
		t.Fatalf("status = %q, want approved", got)
	}
	if rec := call(h, "GET", "/api/tunnel", lanAddr, phoneSecret, ""); rec.Code != 200 {
		t.Fatalf("approved tunnel = %d, want 200", rec.Code)
	}
	if rec := call(h, "GET", "/api/tunnel", lanAddr, "other-secret-0123456789", ""); rec.Code != 401 {
		t.Fatalf("other device = %d, want 401", rec.Code)
	}
}

func TestTunnel_RejectedRequestGrantsNothing(t *testing.T) {
	h := newPairingRouter(t, "")
	call(h, "POST", "/api/pair/request", lanAddr, phoneSecret, `{"device_name":"phone","code":"123-456"}`)
	approveFirstPending(t, h, false)

	if got := checkStatus(t, h, phoneSecret); got != "rejected" {
		t.Fatalf("status = %q, want rejected", got)
	}
	if rec := call(h, "GET", "/api/tunnel", lanAddr, phoneSecret, ""); rec.Code != 401 {
		t.Fatalf("rejected tunnel = %d, want 401", rec.Code)
	}
}

func TestPairing_ApprovalSurvivesRestartWithoutStoringSecret(t *testing.T) {
	path := filepath.Join(t.TempDir(), "paired.json")
	h := newPairingRouter(t, path)
	call(h, "POST", "/api/pair/request", lanAddr, phoneSecret, `{"device_name":"phone","code":"123-456"}`)
	approveFirstPending(t, h, true)

	restarted := newPairingRouter(t, path)
	if got := checkStatus(t, restarted, phoneSecret); got != "approved" {
		t.Fatalf("after restart status = %q", got)
	}
	raw := readFile(t, path)
	if strings.Contains(raw, phoneSecret) {
		t.Fatal("secret stored in plain text")
	}
}

func TestPairing_ClientsCannotApproveThemselvesOrListPending(t *testing.T) {
	h := newPairingRouter(t, "")
	call(h, "POST", "/api/pair/request", lanAddr, phoneSecret, `{"device_name":"phone","code":"123-456"}`)

	if rec := call(h, "GET", "/api/pair/pending", lanAddr, phoneSecret, ""); rec.Code != 403 {
		t.Fatalf("pending from LAN = %d, want 403", rec.Code)
	}
	if rec := call(h, "GET", "/api/pair/pending", "127.0.0.1:1", "", "", "Cf-Connecting-Ip", "1.2.3.4"); rec.Code != 403 {
		t.Fatalf("pending via tunnel = %d, want 403", rec.Code)
	}
	if rec := call(h, "POST", "/api/pair/confirm", "127.0.0.1:1", phoneSecret, `{"device_id":"x","approved":true}`, "Cf-Connecting-Ip", "1.2.3.4"); rec.Code != 403 {
		t.Fatalf("confirm via tunnel = %d, want 403", rec.Code)
	}
}

func TestPublicTunnel_BlocksDeviceControlButNotLan(t *testing.T) {
	h := newPairingRouter(t, "")
	for _, path := range []string{"/api/devices", "/api/screenshot?serial=x", "/api/logcat?serial=x"} {
		if rec := call(h, "GET", path, "127.0.0.1:1", "", "", "Cf-Connecting-Ip", "1.2.3.4"); rec.Code != 403 {
			t.Errorf("%s via tunnel = %d, want 403", path, rec.Code)
		}
		if rec := call(h, "GET", path, lanAddr, "", ""); rec.Code == 403 {
			t.Errorf("%s from LAN must stay available", path)
		}
	}
	if rec := call(h, "GET", "/api/tunnel", "127.0.0.1:1", "", "", "Cf-Connecting-Ip", "1.2.3.4"); rec.Code != 401 {
		t.Fatalf("anonymous tunnel via Cloudflare = %d, want 401", rec.Code)
	}
}

func readFile(t *testing.T, path string) string {
	t.Helper()
	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	return string(raw)
}
