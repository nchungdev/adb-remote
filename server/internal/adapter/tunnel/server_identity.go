package tunnel

import (
	"crypto/rand"
	"encoding/hex"
	"os"
	"path/filepath"
	"strings"
)

// ServerIdentity lets a client see that two addresses, say a LAN IP and a Quick Tunnel
// URL, lead to one server. The ID survives restarts because tunnel URLs do not.
type ServerIdentity struct {
	ID   string
	Name string
}

// LoadServerIdentity reads the ID kept in dataDir, creating it on first use.
func LoadServerIdentity(dataDir string) (ServerIdentity, error) {
	host, _ := os.Hostname()
	identity := ServerIdentity{Name: strings.TrimSuffix(host, ".local")}
	path := filepath.Join(dataDir, "server_id")
	if raw, err := os.ReadFile(path); err == nil && strings.TrimSpace(string(raw)) != "" {
		identity.ID = strings.TrimSpace(string(raw))
		return identity, nil
	}
	buf := make([]byte, 16)
	if _, err := rand.Read(buf); err != nil {
		return identity, err
	}
	identity.ID = hex.EncodeToString(buf)
	if err := os.MkdirAll(dataDir, 0o700); err != nil {
		return identity, err
	}
	return identity, os.WriteFile(path, []byte(identity.ID), 0o600)
}
