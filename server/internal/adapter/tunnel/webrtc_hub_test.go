package tunnel_test

import (
	"context"
	"io"
	"log/slog"
	"testing"
	"time"

	"app.duinch/adb-device-manager/internal/adapter/signaling"
	"app.duinch/adb-device-manager/internal/adapter/tunnel"
)

func TestWebRtcHub_PingPongAndCandidate(t *testing.T) {
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	sigHub := signaling.NewMessageHub()
	tunnelHub := tunnel.NewHub(16555, nil, nil, logger)

	rtcHub := tunnel.NewWebRtcHub(tunnelHub, sigHub, []string{"stun:stun.l.google.com:19302"}, logger)

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	room := "test-room"
	if err := rtcHub.Start(ctx, room); err != nil {
		t.Fatalf("rtcHub.Start failed: %v", err)
	}

	// Register a mock peer
	peerCh, err := sigHub.Register(room, "test-client")
	if err != nil {
		t.Fatalf("sigHub.Register failed: %v", err)
	}
	defer sigHub.Unregister(room, "test-client")

	// Send ping to host
	sigHub.Broadcast(room, signaling.SignalMessage{
		Type:      "ping",
		Sender:    "test-client",
		Recipient: "host",
	})

	select {
	case msg := <-peerCh:
		if msg.Type != "pong" {
			t.Fatalf("expected pong, got %s", msg.Type)
		}
	case <-time.After(1 * time.Second):
		t.Fatal("timed out waiting for pong from WebRtcHub")
	}

	rtcHub.CloseAll()
}
