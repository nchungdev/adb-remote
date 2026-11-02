package tunnel

// Transport abstracts the bidirectional tunnel communication (WebSocket or WebRTC DataChannel).
type Transport interface {
	WriteBinary(payload []byte) error
	WriteText(payload []byte) error
	Close() error
}
