package signaling

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"log/slog"
	"strings"
	"time"

	mqtt "github.com/eclipse/paho.mqtt.golang"
)

type MQTTRelay struct {
	hub       *MessageHub
	roomCode  string
	client    mqtt.Client
	logger    *slog.Logger
	stopCh    chan struct{}
}

func NewMQTTRelay(hub *MessageHub, roomCode string, logger *slog.Logger) (*MQTTRelay, error) {
	normRoom := strings.ReplaceAll(strings.TrimSpace(roomCode), "-", "")
	if normRoom == "" {
		normRoom = "default"
	}

	randBytes := make([]byte, 4)
	_, _ = rand.Read(randBytes)
	clientID := fmt.Sprintf("adb-host-%s-%s", normRoom, hex.EncodeToString(randBytes))

	opts := mqtt.NewClientOptions()
	opts.AddBroker("tcp://broker.hivemq.com:1883")
	opts.AddBroker("tcp://broker.emqx.io:1883")
	opts.SetClientID(clientID)
	opts.SetKeepAlive(30 * time.Second)
	opts.SetPingTimeout(10 * time.Second)
	opts.SetAutoReconnect(true)

	relay := &MQTTRelay{
		hub:      hub,
		roomCode: normRoom,
		logger:   logger,
		stopCh:   make(chan struct{}),
	}

	topicClient := fmt.Sprintf("adb-agent/%s/client", normRoom)
	opts.SetDefaultPublishHandler(func(_ mqtt.Client, m mqtt.Message) {
		var msg SignalMessage
		if err := json.Unmarshal(m.Payload(), &msg); err != nil {
			logger.Warn("invalid mqtt signal json", "err", err)
			return
		}
		if msg.Sender == "" {
			msg.Sender = "android-device"
		}
		logger.Debug("received mqtt signal from client", "type", msg.Type, "sender", msg.Sender)
		hub.Broadcast(normRoom, msg)
	})

	client := mqtt.NewClient(opts)
	token := client.Connect()
	if token.Wait() && token.Error() != nil {
		return nil, fmt.Errorf("connect mqtt relay: %w", token.Error())
	}

	subToken := client.Subscribe(topicClient, 1, nil)
	if subToken.Wait() && subToken.Error() != nil {
		client.Disconnect(250)
		return nil, fmt.Errorf("subscribe client topic: %w", subToken.Error())
	}

	relay.client = client
	logger.Info("connected to p2p mqtt signaling relay", "room", normRoom, "topic", topicClient)

	// Listen for host signals from hub to publish back to MQTT
	go relay.listenHostSignals(normRoom)

	return relay, nil
}

func (r *MQTTRelay) listenHostSignals(room string) {
	ch, err := r.hub.Register(room, "relay-mqtt")
	if err != nil {
		r.logger.Warn("failed to register mqtt relay listener in message hub", "err", err)
		return
	}
	defer r.hub.Unregister(room, "relay-mqtt")

	topicHost := fmt.Sprintf("adb-agent/%s/host", room)

	for {
		select {
		case <-r.stopCh:
			return
		case msg, ok := <-ch:
			if !ok {
				return
			}
			// Only forward messages sent by host
			if msg.Sender == "host" || msg.Sender == "" {
				data, err := json.Marshal(msg)
				if err != nil {
					continue
				}
				token := r.client.Publish(topicHost, 1, false, data)
				_ = token.WaitTimeout(2 * time.Second)
			}
		}
	}
}

func (r *MQTTRelay) Close() {
	close(r.stopCh)
	if r.client != nil && r.client.IsConnected() {
		r.client.Disconnect(250)
	}
}
