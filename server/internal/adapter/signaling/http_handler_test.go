package signaling_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"app.duinch/adb-device-manager/internal/adapter/signaling"
)

func TestHTTPHandler_RecordsWhereAMessageCameFrom(t *testing.T) {
	hub := signaling.NewMessageHub()
	signals, err := hub.Register("room", "host")
	if err != nil {
		t.Fatal(err)
	}
	req := httptest.NewRequest(http.MethodPost, "/?room=room&peer=phone", strings.NewReader(`{"type":"offer"}`))
	req.RemoteAddr = "192.168.1.9:40000"
	signaling.NewHTTPHandler(hub, "").ServeHTTP(httptest.NewRecorder(), req)

	msg := <-signals
	if msg.SenderHost != "192.168.1.9" {
		t.Fatalf("SenderHost = %q", msg.SenderHost)
	}
	encoded, _ := json.Marshal(msg)
	if strings.Contains(string(encoded), "192.168.1.9") {
		t.Fatalf("the sender's address must not reach other peers: %s", encoded)
	}
}
