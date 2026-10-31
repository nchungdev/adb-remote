package storage_test

import (
	"fmt"
	"testing"

	"app.duinch/adb-device-manager/internal/adapter/storage"
	"app.duinch/adb-device-manager/internal/domain"
)

func TestHistoryFileStore_AtomicPersistCapAndDedupe(t *testing.T) {
	dir := t.TempDir()
	store, err := storage.NewHistoryFileStore(dir)
	if err != nil {
		t.Fatalf("create store: %v", err)
	}

	for i := range 35 {
		ended := int64(1700000100 + i)
		err := store.AppendClosed(domain.TunnelHistoryEntry{
			SessionID:    fmt.Sprintf("sess-%02d", i),
			Alias:        "samsung-SM-S926B",
			LocalAdbPort: 35503,
			MappedAddr:   "127.0.0.1:15555",
			ConnectedAt:  int64(1700000000 + i),
			EndedAt:      &ended,
			BytesBridged: int64(i * 1024),
			Active:       true, // store must force false on closed entries
			Reason:       "Đã ngắt kết nối",
		})
		if err != nil {
			t.Fatalf("append %d: %v", i, err)
		}
	}

	// Duplicate SessionID must be ignored
	if err := store.AppendClosed(domain.TunnelHistoryEntry{SessionID: "sess-34"}); err != nil {
		t.Fatalf("duplicate append: %v", err)
	}

	reloaded, err := storage.NewHistoryFileStore(dir)
	if err != nil {
		t.Fatalf("reload store: %v", err)
	}
	list, err := reloaded.ListClosed()
	if err != nil {
		t.Fatalf("list closed: %v", err)
	}
	if len(list) != domain.MaxHistoryEntries {
		t.Fatalf("expected %d capped entries, got %d", domain.MaxHistoryEntries, len(list))
	}
	if list[0].SessionID != "sess-34" || list[0].Active {
		t.Fatalf("expected newest non-active entry first, got %+v", list[0])
	}

	if err := reloaded.ClearClosed(); err != nil {
		t.Fatalf("clear closed: %v", err)
	}
	afterClear, _ := reloaded.ListClosed()
	if len(afterClear) != 0 {
		t.Fatalf("expected 0 entries after clear, got %d", len(afterClear))
	}
}
