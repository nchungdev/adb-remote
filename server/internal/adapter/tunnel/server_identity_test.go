package tunnel_test

import (
	"testing"

	"app.duinch/adb-device-manager/internal/adapter/tunnel"
)

func TestLoadServerIdentity_KeepsTheIDAcrossRestarts(t *testing.T) {
	dir := t.TempDir()
	first, err := tunnel.LoadServerIdentity(dir)
	if err != nil || first.ID == "" {
		t.Fatalf("first load: %+v, %v", first, err)
	}
	second, err := tunnel.LoadServerIdentity(dir)
	if err != nil || second.ID != first.ID {
		t.Fatalf("second load: %+v, %v, want ID %s", second, err, first.ID)
	}
}
