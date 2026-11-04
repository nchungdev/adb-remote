package tunnel_test

import (
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"testing"
	"time"

	"app.duinch/adb-device-manager/internal/adapter/signaling"
	"app.duinch/adb-device-manager/internal/adapter/tunnel"
	"github.com/pion/webrtc/v4"
)

// dialPeer plays one app slot: its own signaling peer ID, sharing the device alias.
func dialPeer(t *testing.T, sig *signaling.MessageHub, peer, room string) <-chan string {
	t.Helper()
	return dialPeerFrom(t, sig, peer, room, "")
}

// dialPeerFrom signals from senderHost: loopback is how a Quick Tunnel client arrives.
func dialPeerFrom(t *testing.T, sig *signaling.MessageHub, peer, room, senderHost string) <-chan string {
	t.Helper()
	signals, err := sig.Register(room, peer)
	if err != nil {
		t.Fatal(err)
	}
	pc, err := webrtc.NewPeerConnection(webrtc.Configuration{})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = pc.Close() })
	dc, err := pc.CreateDataChannel("adb-stream", nil)
	if err != nil {
		t.Fatal(err)
	}
	events := make(chan string, 8)
	dc.OnOpen(func() { events <- "open" })
	dc.OnMessage(func(m webrtc.DataChannelMessage) { events <- string(m.Data) })
	dc.OnClose(func() { events <- "closed" })

	offer, err := pc.CreateOffer(nil)
	if err != nil {
		t.Fatal(err)
	}
	gathered := webrtc.GatheringCompletePromise(pc)
	if err := pc.SetLocalDescription(offer); err != nil {
		t.Fatal(err)
	}
	<-gathered
	payload, _ := json.Marshal(map[string]string{"sdp": pc.LocalDescription().SDP})
	sig.Broadcast(room, signaling.SignalMessage{Type: "offer", Sender: peer, SenderHost: senderHost, Recipient: "host", Payload: payload})

	deadline := time.After(10 * time.Second)
	for {
		select {
		case msg := <-signals:
			if msg.Type != "answer" {
				continue
			}
			var answer struct {
				SDP string `json:"sdp"`
			}
			if err := json.Unmarshal(msg.Payload, &answer); err != nil {
				t.Fatal(err)
			}
			if err := pc.SetRemoteDescription(webrtc.SessionDescription{Type: webrtc.SDPTypeAnswer, SDP: answer.SDP}); err != nil {
				t.Fatal(err)
			}
			return events
		case <-deadline:
			t.Fatal("no answer from host")
		}
	}
}

func waitFor(t *testing.T, events <-chan string, want string) []string {
	t.Helper()
	var seen []string
	deadline := time.After(20 * time.Second)
	for {
		select {
		case ev := <-events:
			seen = append(seen, ev)
			if ev == want {
				return seen
			}
		case <-deadline:
			t.Fatalf("did not see %q, got %v", want, seen)
		}
	}
}

func TestWebRtcHub_TwoSlotsOfOneDeviceShareAMappedPortAndStayUp(t *testing.T) {
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	sig := signaling.NewMessageHub()
	hub := tunnel.NewHub(26855, nil, nil, logger)
	t.Cleanup(hub.CloseAll)
	rtc := tunnel.NewWebRtcHub(hub, sig, nil, logger)
	t.Cleanup(rtc.CloseAll)

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	const room = "two-slots"
	if err := rtc.Start(ctx, room); err != nil {
		t.Fatal(err)
	}

	first := dialPeer(t, sig, "phone~slot1", room)
	waitFor(t, first, "open")
	second := dialPeer(t, sig, "phone~slot2", room)
	waitFor(t, second, "open")

	time.Sleep(500 * time.Millisecond)
	for _, events := range []<-chan string{first, second} {
		for len(events) > 0 {
			if ev := <-events; ev == "closed" {
				t.Fatal("a slot was dropped; both paths must stay up")
			}
		}
	}
	tunnels := hub.ListActiveTunnels()
	if len(tunnels) != 1 || tunnels[0].Alias != "phone" {
		t.Fatalf("expected one device slot for alias phone, got %+v", tunnels)
	}
}

// ICE picks the direct route for both cards on one network, so only the signaling route
// tells them apart: the tunnel card must not win just by connecting later.
func TestWebRtcHub_CardSignaledOverTunnelStaysBehindLAN(t *testing.T) {
	const standby = `{"type":"path_role","role":"standby"}`
	for _, tc := range []struct {
		name          string
		first, second string
		standby       int
	}{
		{"tunnel connects after LAN", "192.168.1.9", "127.0.0.1", 1},
		{"LAN connects after tunnel", "127.0.0.1", "192.168.1.9", 0},
	} {
		t.Run(tc.name, func(t *testing.T) {
			logger := slog.New(slog.NewTextHandler(io.Discard, nil))
			sig := signaling.NewMessageHub()
			hub := tunnel.NewHub(26875, nil, nil, logger)
			t.Cleanup(hub.CloseAll)
			rtc := tunnel.NewWebRtcHub(hub, sig, nil, logger)
			t.Cleanup(rtc.CloseAll)
			ctx, cancel := context.WithCancel(context.Background())
			defer cancel()
			const room = "rank"
			if err := rtc.Start(ctx, room); err != nil {
				t.Fatal(err)
			}

			first := dialPeerFrom(t, sig, "phone~a", room, tc.first)
			waitFor(t, first, "open")
			second := dialPeerFrom(t, sig, "phone~b", room, tc.second)
			waitFor(t, second, "open")

			waitFor(t, []<-chan string{first, second}[tc.standby], standby)
		})
	}
}
