package httpctrl

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"os"
	"path/filepath"
	"sync"

	"github.com/nchungdev/remote-tunnel/go/pairing"
)

// pairedDevices remembers which device secrets the host owner approved. Only a
// hash is kept, so the file and the pending list never expose a usable credential.
type pairedDevices struct {
	mu    sync.RWMutex
	path  string
	names map[string]string // device ID -> display name
}

func newPairedDevices(path string) *pairedDevices {
	p := &pairedDevices{path: path, names: map[string]string{}}
	if path == "" {
		return p
	}
	if raw, err := os.ReadFile(path); err == nil {
		_ = json.Unmarshal(raw, &p.names)
	}
	return p
}

func deviceIDFromSecret(secret string) string {
	sum := sha256.Sum256([]byte(secret))
	return hex.EncodeToString(sum[:16])
}

func (p *pairedDevices) isApproved(deviceID string) bool {
	p.mu.RLock()
	defer p.mu.RUnlock()
	_, ok := p.names[deviceID]
	return ok
}

func (p *pairedDevices) approve(deviceID, name string) error {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.names[deviceID] = name
	if p.path == "" {
		return nil
	}
	raw, err := json.Marshal(p.names)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(p.path), 0o755); err != nil {
		return err
	}
	return os.WriteFile(p.path, raw, 0o600)
}

func requestDeviceID(r *http.Request) string {
	secret := pairing.ExtractBearerOrQueryToken(r)
	if secret == "" {
		return ""
	}
	return deviceIDFromSecret(secret)
}

func (s *Server) isApprovedDevice(r *http.Request) bool {
	id := requestDeviceID(r)
	return id != "" && s.paired.isApproved(id)
}

// requireApprovedDevice guards the tunnel path: anyone reaching it can drive ADB
// on the device, so the host itself or an approved device only.
func (s *Server) requireApprovedDevice(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if pairing.IsLoopbackRequest(r) || s.isApprovedDevice(r) {
			next(w, r)
			return
		}
		http.Error(w, "Device not approved on this server", http.StatusUnauthorized)
	}
}

// blockUnapprovedTunnelClients keeps device control off the public Quick Tunnel URL
// while the dashboard stays usable from the local network.
func (s *Server) blockUnapprovedTunnelClients(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		viaTunnel := r.Header.Get("Cf-Connecting-Ip") != "" || r.Header.Get("CF-Ray") != "" ||
			r.Header.Get("X-Forwarded-For") != ""
		if viaTunnel && !s.isApprovedDevice(r) {
			http.Error(w, "Not available through the public tunnel", http.StatusForbidden)
			return
		}
		next(w, r)
	}
}
