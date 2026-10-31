package storage

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sync"

	"app.duinch/adb-device-manager/internal/domain"
)

type HistoryFileStore struct {
	filePath string
	mu       sync.Mutex
	entries  []domain.TunnelHistoryEntry
}

func NewHistoryFileStore(dataDir string) (*HistoryFileStore, error) {
	if err := os.MkdirAll(dataDir, 0o755); err != nil {
		return nil, fmt.Errorf("create data dir: %w", err)
	}
	s := &HistoryFileStore{
		filePath: filepath.Join(dataDir, "tunnel_history.json"),
	}
	s.entries = s.loadFromDisk()
	return s, nil
}

func (s *HistoryFileStore) ListClosed() ([]domain.TunnelHistoryEntry, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	cp := make([]domain.TunnelHistoryEntry, len(s.entries))
	copy(cp, s.entries)
	return cp, nil
}

func (s *HistoryFileStore) AppendClosed(entry domain.TunnelHistoryEntry) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	for _, existing := range s.entries {
		if existing.SessionID != "" && existing.SessionID == entry.SessionID {
			return nil
		}
	}
	entry.Active = false
	updated := make([]domain.TunnelHistoryEntry, 0, len(s.entries)+1)
	updated = append(updated, entry)
	updated = append(updated, s.entries...)
	if len(updated) > domain.MaxHistoryEntries {
		updated = updated[:domain.MaxHistoryEntries]
	}
	s.entries = updated
	return s.persistLocked()
}

func (s *HistoryFileStore) ClearClosed() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.entries = []domain.TunnelHistoryEntry{}
	return s.persistLocked()
}

func (s *HistoryFileStore) loadFromDisk() []domain.TunnelHistoryEntry {
	raw, err := os.ReadFile(s.filePath)
	if err != nil {
		return nil
	}
	var list []domain.TunnelHistoryEntry
	if err := json.Unmarshal(raw, &list); err != nil {
		return nil
	}
	if len(list) > domain.MaxHistoryEntries {
		list = list[:domain.MaxHistoryEntries]
	}
	return list
}

func (s *HistoryFileStore) persistLocked() error {
	payload, err := json.MarshalIndent(s.entries, "", "  ")
	if err != nil {
		return fmt.Errorf("marshal history: %w", err)
	}
	dir := filepath.Dir(s.filePath)
	tmp, err := os.CreateTemp(dir, ".tunnel_history_*.tmp")
	if err != nil {
		return fmt.Errorf("create temp history file: %w", err)
	}
	tmpName := tmp.Name()
	if _, err := tmp.Write(payload); err != nil {
		_ = tmp.Close()
		_ = os.Remove(tmpName)
		return fmt.Errorf("write temp history: %w", err)
	}
	if err := tmp.Close(); err != nil {
		_ = os.Remove(tmpName)
		return fmt.Errorf("close temp history: %w", err)
	}
	if err := os.Rename(tmpName, s.filePath); err != nil {
		_ = os.Remove(tmpName)
		return fmt.Errorf("atomic rename history: %w", err)
	}
	return nil
}
