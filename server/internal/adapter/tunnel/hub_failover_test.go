package tunnel_test

import (
	"context"
	"io"
	"log/slog"
	"net"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"app.duinch/adb-device-manager/internal/adapter/tunnel"
)

type fakeTransport struct {
	mu     sync.Mutex
	events []string
}

func (f *fakeTransport) record(event string) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.events = append(f.events, event)
}

func (f *fakeTransport) WriteBinary([]byte) error { return nil }
func (f *fakeTransport) WriteText(p []byte) error { f.record(string(p)); return nil }
func (f *fakeTransport) Close() error             { f.record("closed"); return nil }

func (f *fakeTransport) has(event string) bool {
	f.mu.Lock()
	defer f.mu.Unlock()
	return strings.Contains(strings.Join(f.events, ","), event)
}

func eventually(t *testing.T, what string, cond func() bool) {
	t.Helper()
	deadline := time.Now().Add(3 * time.Second)
	for !cond() {
		if time.Now().After(deadline) {
			t.Fatalf("timed out waiting for %s", what)
		}
		time.Sleep(10 * time.Millisecond)
	}
}

type counters struct{ connects, disconnects atomic.Int32 }

func newTestHub(t *testing.T, basePort int) (*tunnel.Hub, *counters) {
	t.Helper()
	c := &counters{}
	hub := tunnel.NewHub(basePort, nil, func(context.Context, string) { c.connects.Add(1) },
		slog.New(slog.NewTextHandler(io.Discard, nil)))
	hub.SetAutoDisconnect(func(context.Context, string) { c.disconnects.Add(1) })
	t.Cleanup(hub.CloseAll)
	return hub, c
}

const standbyRole = `"role":"standby"`
const activeRole = `"role":"active"`

func TestRegisterSession_RemoteJoiningLanStaysStandbyWithoutTouchingAdb(t *testing.T) {
	hub, c := newTestHub(t, 26555)
	lan, remote := &fakeTransport{}, &fakeTransport{}

	if _, err := hub.RegisterSession(lan, "phone", 5555, tunnel.PathLAN, ""); err != nil {
		t.Fatal(err)
	}
	eventually(t, "initial adb connect", func() bool { return c.connects.Load() == 1 })

	if _, err := hub.RegisterSession(remote, "phone", 5555, tunnel.PathRemote, ""); err != nil {
		t.Fatal(err)
	}
	eventually(t, "standby notice", func() bool { return remote.has(standbyRole) })
	time.Sleep(450 * time.Millisecond)

	if lan.has("closed") || remote.has("closed") {
		t.Fatal("both paths must stay open")
	}
	if n := c.connects.Load(); n != 1 {
		t.Fatalf("standby path must not reconnect adb, connects=%d", n)
	}
}

func TestRegisterSession_LanTakesOverFromRemote(t *testing.T) {
	hub, c := newTestHub(t, 26655)
	remote, lan := &fakeTransport{}, &fakeTransport{}

	if _, err := hub.RegisterSession(remote, "phone", 5555, tunnel.PathRemote, ""); err != nil {
		t.Fatal(err)
	}
	if _, err := hub.RegisterSession(lan, "phone", 5555, tunnel.PathLAN, ""); err != nil {
		t.Fatal(err)
	}
	eventually(t, "remote demoted", func() bool { return remote.has(standbyRole) })
	eventually(t, "adb moved to lan", func() bool { return c.connects.Load() == 2 })
	if remote.has("closed") {
		t.Fatal("remote must stay as standby")
	}
}

func TestUnregisterSession_FailsOverThenDisconnectsAdb(t *testing.T) {
	hub, c := newTestHub(t, 26755)
	lanT, remoteT := &fakeTransport{}, &fakeTransport{}

	lan, err := hub.RegisterSession(lanT, "phone", 5555, tunnel.PathLAN, "")
	if err != nil {
		t.Fatal(err)
	}
	remote, err := hub.RegisterSession(remoteT, "phone", 5555, tunnel.PathRemote, "")
	if err != nil {
		t.Fatal(err)
	}
	eventually(t, "initial adb connect", func() bool { return c.connects.Load() == 1 })

	hub.UnregisterSession(lan)
	eventually(t, "remote promoted", func() bool { return remoteT.has(activeRole) })
	eventually(t, "adb reconnected over remote", func() bool { return c.connects.Load() == 2 })
	if n := c.disconnects.Load(); n != 0 {
		t.Fatalf("failover must not leave adb disconnected, disconnects=%d", n)
	}
	if got := hub.ListActiveTunnels(); len(got) != 1 {
		t.Fatalf("expected remote to serve the slot, got %d tunnels", len(got))
	}

	hub.UnregisterSession(remote)
	eventually(t, "adb disconnected", func() bool { return c.disconnects.Load() == 1 })
}

func TestUnregisterSession_StandbyLossKeepsAdbUntouched(t *testing.T) {
	hub, c := newTestHub(t, 26955)
	if _, err := hub.RegisterSession(&fakeTransport{}, "phone", 5555, tunnel.PathLAN, ""); err != nil {
		t.Fatal(err)
	}
	remote, err := hub.RegisterSession(&fakeTransport{}, "phone", 5555, tunnel.PathRemote, "")
	if err != nil {
		t.Fatal(err)
	}
	eventually(t, "initial adb connect", func() bool { return c.connects.Load() == 1 })

	hub.UnregisterSession(remote)
	time.Sleep(100 * time.Millisecond)
	if c.connects.Load() != 1 || c.disconnects.Load() != 0 {
		t.Fatalf("standby loss touched adb: connects=%d disconnects=%d", c.connects.Load(), c.disconnects.Load())
	}
}

func TestServeWebSocket_PingsKeepIdleSessionAlive(t *testing.T) {
	conn, peer := net.Pipe()
	defer peer.Close()
	ws := tunnel.NewWSConn(conn, nil)
	ws.SetIdleTimeout(100 * time.Millisecond)

	done := make(chan error, 1)
	go func() {
		_, _, err := ws.ReadMessage()
		done <- err
	}()
	go func() { _, _ = io.Copy(io.Discard, peer) }()

	masked := []byte{0x89, 0x80, 0, 0, 0, 0}
	for range 5 {
		time.Sleep(50 * time.Millisecond)
		if _, err := peer.Write(masked); err != nil {
			t.Fatal(err)
		}
	}
	select {
	case err := <-done:
		t.Fatalf("pings must extend the deadline, read ended early: %v", err)
	default:
	}
	if err := <-done; err == nil {
		t.Fatal("silence must end the read")
	}
}

func TestPathForHost(t *testing.T) {
	for host, want := range map[string]tunnel.PathKind{
		"192.168.1.16": tunnel.PathLAN,
		"10.0.0.2":     tunnel.PathLAN,
		"127.0.0.1":    tunnel.PathRemote,
		"::1":          tunnel.PathRemote,
		"100.64.0.1":   tunnel.PathRemote,
		"8.8.8.8":      tunnel.PathRemote,
		"not-an-ip":    tunnel.PathRemote,
	} {
		if got := tunnel.PathForHost(host); got != want {
			t.Errorf("PathForHost(%q) = %v, want %v", host, got, want)
		}
	}
}

func TestRegisterSession_SameConnectionReplacesDeadSessionAtOnce(t *testing.T) {
	hub, c := newTestHub(t, 27055)
	dead, fresh := &fakeTransport{}, &fakeTransport{}

	// The app was killed: its LAN session looks alive, and the reconnect was classed remote.
	if _, err := hub.RegisterSession(dead, "phone", 5555, tunnel.PathLAN, "primary"); err != nil {
		t.Fatal(err)
	}
	eventually(t, "initial adb connect", func() bool { return c.connects.Load() == 1 })
	if _, err := hub.RegisterSession(fresh, "phone", 5555, tunnel.PathRemote, "primary"); err != nil {
		t.Fatal(err)
	}

	eventually(t, "dead session closed", func() bool { return dead.has("closed") })
	eventually(t, "adb moved to the new session", func() bool { return c.connects.Load() == 2 })
	if fresh.has(standbyRole) || fresh.has("closed") {
		t.Fatal("the new session must serve immediately")
	}
}

func TestRegisterSession_DifferentConnectionsStayAsStandby(t *testing.T) {
	hub, _ := newTestHub(t, 27155)
	lan, remote := &fakeTransport{}, &fakeTransport{}
	if _, err := hub.RegisterSession(lan, "phone", 5555, tunnel.PathLAN, "primary"); err != nil {
		t.Fatal(err)
	}
	if _, err := hub.RegisterSession(remote, "phone", 5555, tunnel.PathRemote, "slot-2"); err != nil {
		t.Fatal(err)
	}
	eventually(t, "standby notice", func() bool { return remote.has(standbyRole) })
	if lan.has("closed") || remote.has("closed") {
		t.Fatal("a second slot must not replace the first")
	}
}
