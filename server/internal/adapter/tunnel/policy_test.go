package tunnel_test

import (
	"context"
	"io"
	"log/slog"
	"testing"

	"app.duinch/adb-device-manager/internal/adapter/tunnel"
)

func TestParsePathPolicy(t *testing.T) {
	cases := []struct {
		input    string
		expected tunnel.PathPolicy
	}{
		{"", tunnel.PolicyAuto},
		{"auto", tunnel.PolicyAuto},
		{"AUTO", tunnel.PolicyAuto},
		{"lan", tunnel.PolicyLAN},
		{"LAN", tunnel.PolicyLAN},
		{"local", tunnel.PolicyLAN},
		{"remote", tunnel.PolicyRemote},
		{"REMOTE", tunnel.PolicyRemote},
		{"cloud", tunnel.PolicyRemote},
		{"unknown", tunnel.PolicyAuto},
	}
	for _, tc := range cases {
		got := tunnel.ParsePathPolicy(tc.input)
		if got != tc.expected {
			t.Errorf("ParsePathPolicy(%q) = %q; want %q", tc.input, got, tc.expected)
		}
	}
}

func TestHub_ForceRemotePolicy(t *testing.T) {
	var c counters
	hub := tunnel.NewHub(18100, nil, func(_ context.Context, _ string) {
		c.connects.Add(1)
	}, slog.New(slog.NewTextHandler(io.Discard, nil)))
	hub.SetAutoDisconnect(func(_ context.Context, _ string) {
		c.disconnects.Add(1)
	})
	t.Cleanup(hub.CloseAll)
	hub.SetPolicy(tunnel.PolicyRemote)

	lanTr := &fakeTransport{}
	sessLAN, err := hub.RegisterSession(lanTr, "Pixel-Policy", 5555, tunnel.PathLAN, "conn-lan")
	if err != nil {
		t.Fatalf("register LAN failed: %v", err)
	}

	// Under PolicyRemote, LAN should be demoted to standby
	eventually(t, "lan standby under remote policy", func() bool {
		return lanTr.has(standbyRole)
	})
	eventually(t, "no active tunnels yet", func() bool {
		return len(hub.ListActiveTunnels()) == 0
	})

	remTr := &fakeTransport{}
	sessRemote, err := hub.RegisterSession(remTr, "Pixel-Policy", 5555, tunnel.PathRemote, "conn-rem")
	if err != nil {
		t.Fatalf("register Remote failed: %v", err)
	}

	// Under PolicyRemote, remote is the active tunnel
	eventually(t, "remote is active tunnel", func() bool {
		tuns := hub.ListActiveTunnels()
		return len(tuns) == 1 && tuns[0].SessionID == sessRemote.SessionID
	})

	// Switch dynamically to PolicyLAN
	hub.SetPolicy(tunnel.PolicyLAN)
	eventually(t, "lan promoted to active", func() bool {
		return lanTr.has(activeRole)
	})
	eventually(t, "remote demoted to standby", func() bool {
		return remTr.has(standbyRole)
	})
	eventually(t, "lan is active tunnel", func() bool {
		tuns := hub.ListActiveTunnels()
		return len(tuns) == 1 && tuns[0].SessionID == sessLAN.SessionID
	})

	// Revert to PolicyAuto (LAN remains active)
	hub.SetPolicy(tunnel.PolicyAuto)
	if hub.Policy() != tunnel.PolicyAuto {
		t.Fatalf("expected PolicyAuto, got %v", hub.Policy())
	}
}

func TestHub_ForceLanPolicy(t *testing.T) {
	hub := tunnel.NewHub(18150, nil, nil, slog.New(slog.NewTextHandler(io.Discard, nil)))
	t.Cleanup(hub.CloseAll)
	hub.SetPolicy(tunnel.PolicyLAN)

	remTr := &fakeTransport{}
	if _, err := hub.RegisterSession(remTr, "Pixel-LanOnly", 5555, tunnel.PathRemote, "conn-rem"); err != nil {
		t.Fatalf("register Remote failed: %v", err)
	}

	eventually(t, "remote standby under lan policy", func() bool {
		return remTr.has(standbyRole)
	})
	eventually(t, "no active tunnels yet", func() bool {
		return len(hub.ListActiveTunnels()) == 0
	})

	lanTr := &fakeTransport{}
	sessLAN, err := hub.RegisterSession(lanTr, "Pixel-LanOnly", 5555, tunnel.PathLAN, "conn-lan")
	if err != nil {
		t.Fatalf("register LAN failed: %v", err)
	}

	eventually(t, "lan is active tunnel", func() bool {
		tuns := hub.ListActiveTunnels()
		return len(tuns) == 1 && tuns[0].SessionID == sessLAN.SessionID
	})
}
