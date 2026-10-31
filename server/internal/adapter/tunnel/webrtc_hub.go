package tunnel

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"sync"

	"github.com/pion/webrtc/v4"

	"app.duinch/adb-device-manager/internal/adapter/signaling"
)

type DataChannelTransport struct {
	dc *webrtc.DataChannel
}

func NewDataChannelTransport(dc *webrtc.DataChannel) *DataChannelTransport {
	return &DataChannelTransport{dc: dc}
}

func (t *DataChannelTransport) WriteBinary(payload []byte) error {
	return t.dc.Send(payload)
}

func (t *DataChannelTransport) WriteText(payload []byte) error {
	return t.dc.SendText(string(payload))
}

func (t *DataChannelTransport) Close() error {
	return t.dc.Close()
}

type WebRtcHub struct {
	hub         *Hub
	signalHub   *signaling.MessageHub
	stunServers []string
	logger      *slog.Logger

	mu    sync.Mutex
	peers map[string]*webrtc.PeerConnection
}

func NewWebRtcHub(
	hub *Hub,
	signalHub *signaling.MessageHub,
	stunServers []string,
	logger *slog.Logger,
) *WebRtcHub {
	if len(stunServers) == 0 {
		stunServers = []string{"stun:stun.l.google.com:19302"}
	}
	return &WebRtcHub{
		hub:         hub,
		signalHub:   signalHub,
		stunServers: stunServers,
		logger:      logger,
		peers:       make(map[string]*webrtc.PeerConnection),
	}
}

func (w *WebRtcHub) Start(ctx context.Context, room string) error {
	signalCh, err := w.signalHub.Register(room, "host")
	if err != nil {
		return fmt.Errorf("register host signal peer: %w", err)
	}

	go func() {
		defer w.signalHub.Unregister(room, "host")
		for {
			select {
			case <-ctx.Done():
				w.CloseAll()
				return
			case msg, ok := <-signalCh:
				if !ok {
					return
				}
				w.handleSignalMessage(ctx, room, msg)
			}
		}
	}()
	return nil
}

func (w *WebRtcHub) handleSignalMessage(_ context.Context, room string, msg signaling.SignalMessage) {
	switch msg.Type {
	case "offer":
		w.handleOffer(room, msg)
	case "candidate":
		w.handleCandidate(msg)
	case "ping":
		w.signalHub.Broadcast(room, signaling.SignalMessage{
			Type:      "pong",
			Sender:    "host",
			Recipient: msg.Sender,
		})
	}
}

func (w *WebRtcHub) handleOffer(room string, msg signaling.SignalMessage) {
	peerID := msg.Sender
	if peerID == "" {
		peerID = "android-device"
	}

	w.mu.Lock()
	if oldPC, exists := w.peers[peerID]; exists && oldPC != nil {
		_ = oldPC.Close()
		delete(w.peers, peerID)
	}
	w.mu.Unlock()

	var iceServers []webrtc.ICEServer
	for _, u := range w.stunServers {
		iceServers = append(iceServers, webrtc.ICEServer{URLs: []string{u}})
	}

	config := webrtc.Configuration{
		ICEServers: iceServers,
	}

	pc, err := webrtc.NewPeerConnection(config)
	if err != nil {
		w.logger.Error("failed to create webrtc peer connection", "peer", peerID, "err", err)
		return
	}

	w.mu.Lock()
	w.peers[peerID] = pc
	w.mu.Unlock()

	pc.OnICECandidate(func(c *webrtc.ICECandidate) {
		if c == nil {
			return
		}
		candJSON := c.ToJSON()
		payload, _ := json.Marshal(candJSON)
		w.signalHub.Broadcast(room, signaling.SignalMessage{
			Type:      "candidate",
			Sender:    "host",
			Recipient: peerID,
			Payload:   payload,
		})
	})

	pc.OnConnectionStateChange(func(s webrtc.PeerConnectionState) {
		w.logger.Info("webrtc connection state changed", "peer", peerID, "state", s.String())
		if s == webrtc.PeerConnectionStateFailed || s == webrtc.PeerConnectionStateClosed {
			w.mu.Lock()
			delete(w.peers, peerID)
			w.mu.Unlock()
			_ = pc.Close()
		}
	})

	pc.OnDataChannel(func(d *webrtc.DataChannel) {
		if d.Label() != "adb-stream" {
			w.logger.Warn("ignoring unknown datachannel label", "label", d.Label(), "peer", peerID)
			return
		}

		d.OnOpen(func() {
			w.logger.Info("webrtc adb datachannel opened", "peer", peerID)
			transport := NewDataChannelTransport(d)
			sess, err := w.hub.RegisterSession(transport, peerID, 5555)
			if err != nil {
				w.logger.Error("failed to register webrtc tunnel session", "peer", peerID, "err", err)
				_ = d.Close()
				return
			}

			serverIP := findPrimaryLanIP()
			serverSSID := detectServerSSID()
			_ = sess.SendWelcome(serverIP, serverSSID)

			d.OnMessage(func(m webrtc.DataChannelMessage) {
				if m.IsString {
					sess.HandleTextJSON(m.Data, serverIP, serverSSID)
				} else {
					sess.HandleWSBinary(m.Data)
				}
			})

			d.OnClose(func() {
				w.logger.Info("webrtc adb datachannel closed", "peer", peerID)
				w.hub.UnregisterSession(sess)
			})
		})
	})

	var offerSDP struct {
		SDP string `json:"sdp"`
	}
	if err := json.Unmarshal(msg.Payload, &offerSDP); err != nil {
		w.logger.Error("invalid offer payload", "peer", peerID, "err", err)
		return
	}

	offer := webrtc.SessionDescription{
		Type: webrtc.SDPTypeOffer,
		SDP:  offerSDP.SDP,
	}

	if err := pc.SetRemoteDescription(offer); err != nil {
		w.logger.Error("failed to set remote description", "peer", peerID, "err", err)
		return
	}

	answer, err := pc.CreateAnswer(nil)
	if err != nil {
		w.logger.Error("failed to create answer", "peer", peerID, "err", err)
		return
	}

	if err := pc.SetLocalDescription(answer); err != nil {
		w.logger.Error("failed to set local description", "peer", peerID, "err", err)
		return
	}

	answerPayload, _ := json.Marshal(map[string]string{
		"sdp": answer.SDP,
	})
	w.signalHub.Broadcast(room, signaling.SignalMessage{
		Type:      "answer",
		Sender:    "host",
		Recipient: peerID,
		Payload:   answerPayload,
	})
}

func (w *WebRtcHub) handleCandidate(msg signaling.SignalMessage) {
	peerID := msg.Sender
	w.mu.Lock()
	pc := w.peers[peerID]
	w.mu.Unlock()

	if pc == nil {
		return
	}

	var candInit webrtc.ICECandidateInit
	if err := json.Unmarshal(msg.Payload, &candInit); err != nil {
		w.logger.Warn("invalid ice candidate json", "peer", peerID, "err", err)
		return
	}

	if err := pc.AddICECandidate(candInit); err != nil {
		w.logger.Warn("failed to add ice candidate", "peer", peerID, "err", err)
	}
}

func (w *WebRtcHub) CloseAll() {
	w.mu.Lock()
	defer w.mu.Unlock()
	for id, pc := range w.peers {
		if pc != nil {
			_ = pc.Close()
		}
		delete(w.peers, id)
	}
}
