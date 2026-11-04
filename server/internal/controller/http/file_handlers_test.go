package httpctrl_test

import (
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"

	httpctrl "app.duinch/adb-device-manager/internal/controller/http"
	"app.duinch/adb-device-manager/internal/domain"
	"app.duinch/adb-device-manager/internal/usecase"
)

type stubFileAdb struct{}

func (stubFileAdb) RunShell(_ context.Context, _, command string) (domain.CommandResult, error) {
	if strings.HasPrefix(command, "ls ") {
		return domain.CommandResult{Success: true, Stdout: "-rw-rw---- 1 a b 4 2026-11-04 08:28 note.txt"}, nil
	}
	return domain.CommandResult{Success: !strings.HasPrefix(command, "test -e")}, nil
}

func (stubFileAdb) Push(context.Context, string, string, string) (domain.CommandResult, error) {
	return domain.CommandResult{Success: true}, nil
}

func (stubFileAdb) Pull(_ context.Context, _, _, local string) (domain.CommandResult, error) {
	return domain.CommandResult{Success: true}, os.WriteFile(local, []byte("data"), 0o600)
}

func fileRouter(t *testing.T) http.Handler {
	t.Helper()
	st := stubTunnel{}
	return httpctrl.NewRouter(usecase.NewDeviceService(stubAdb{}, st, nil),
		usecase.NewTunnelService(st, st), usecase.NewOtaService(stubOta{}), st, nil, nil,
		slog.New(slog.NewTextHandler(io.Discard, nil)),
		httpctrl.WithPairedDevicesFile(t.TempDir()+"/paired.json"),
		httpctrl.WithFileService(usecase.NewFileService(stubFileAdb{})))
}

func hostRequest(h http.Handler, method, target, body string, headers ...string) *httptest.ResponseRecorder {
	req := httptest.NewRequest(method, target, strings.NewReader(body))
	req.RemoteAddr = "127.0.0.1:50000"
	req.Host = "localhost:8080"
	for i := 0; i+1 < len(headers); i += 2 {
		req.Header.Set(headers[i], headers[i+1])
	}
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	return rec
}

func TestFiles_WorkFromTheHostBrowser(t *testing.T) {
	h := fileRouter(t)
	rec := hostRequest(h, "GET", "/api/files?serial=S1&path=/sdcard", "")
	var listing struct {
		Files []usecase.DeviceFile `json:"files"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &listing); err != nil || len(listing.Files) != 1 || listing.Files[0].Name != "note.txt" {
		t.Fatalf("list: %d %s", rec.Code, rec.Body.String())
	}
	rec = hostRequest(h, "POST", "/api/files/upload?serial=S1&dir=/sdcard&name=a.txt", "hello", "Origin", "http://localhost:8080")
	if rec.Code != http.StatusOK {
		t.Fatalf("upload: %d %s", rec.Code, rec.Body.String())
	}
	rec = hostRequest(h, "GET", "/api/files/download?serial=S1&path=/sdcard/b%20c.txt", "")
	if rec.Code != http.StatusOK || rec.Body.String() != "data" ||
		rec.Header().Get("Content-Disposition") != "attachment; filename*=UTF-8''b%20c.txt" {
		t.Fatalf("download: %d %q %v", rec.Code, rec.Body.String(), rec.Header())
	}
}

func TestFiles_ClosedToEveryoneElse(t *testing.T) {
	h := fileRouter(t)
	target := "/api/files?serial=S1&path=/sdcard"
	cases := []struct {
		name  string
		build func(*http.Request)
	}{
		{"another machine on the network", func(r *http.Request) { r.RemoteAddr = lanAddr }},
		{"the public tunnel", func(r *http.Request) { r.Header.Set("Cf-Connecting-Ip", "203.0.113.5") }},
		{"a domain pointed at loopback", func(r *http.Request) { r.Host = "evil.example:8080" }},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			req := httptest.NewRequest("GET", target, nil)
			req.RemoteAddr = "127.0.0.1:50000"
			req.Host = "localhost:8080"
			tc.build(req)
			rec := httptest.NewRecorder()
			h.ServeHTTP(rec, req)
			if rec.Code != http.StatusForbidden {
				t.Fatalf("status %d", rec.Code)
			}
		})
	}
}

func TestCrossSitePagesCannotDriveTheServer(t *testing.T) {
	h := fileRouter(t)
	for _, target := range []string{"/api/shell", "/api/reboot", "/api/files/upload?serial=S1&dir=/sdcard&name=a"} {
		rec := hostRequest(h, "POST", target, `{"serial":"S1","cmd":"id"}`, "Origin", "https://evil.example")
		if rec.Code != http.StatusForbidden {
			t.Fatalf("%s: status %d", target, rec.Code)
		}
	}
	rec := hostRequest(h, "POST", "/api/shell", `{"serial":"S1","cmd":"id"}`)
	if rec.Code == http.StatusForbidden {
		t.Fatal("a client with no Origin, like the app or curl, must still pass this check")
	}
}
