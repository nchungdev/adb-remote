package signaling_test

import (
	"encoding/json"
	"testing"
	"time"

	"app.duinch/adb-device-manager/internal/adapter/signaling"
)

func TestMessageHub_ExchangeSignal(t *testing.T) {
	hub := signaling.NewMessageHub()

	room := "test-room"
	peerA := "host-mac"
	peerB := "client-phone"

	chA, err := hub.Register(room, peerA)
	if err != nil {
		t.Fatalf("failed to register peerA: %v", err)
	}
	chB, err := hub.Register(room, peerB)
	if err != nil {
		t.Fatalf("failed to register peerB: %v", err)
	}

	defer hub.Unregister(room, peerA)
	defer hub.Unregister(room, peerB)

	// Send offer from A to B
	payload := json.RawMessage(`{"sdp":"v=0..."}`)
	sent := hub.Broadcast(room, signaling.SignalMessage{
		Type:      "offer",
		Sender:    peerA,
		Recipient: peerB,
		Payload:   payload,
	})

	if sent != 1 {
		t.Fatalf("expected 1 message sent, got %d", sent)
	}

	select {
	case msg := <-chB:
		if msg.Type != "offer" || msg.Sender != peerA {
			t.Errorf("unexpected message: %+v", msg)
		}
	case <-time.After(1 * time.Second):
		t.Fatal("timeout waiting for message on peerB")
	}

	// PeerA should NOT receive its own message
	select {
	case msg := <-chA:
		t.Fatalf("peerA received unexpected message: %+v", msg)
	default:
		// OK
	}
}
