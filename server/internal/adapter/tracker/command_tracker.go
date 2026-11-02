package tracker

import (
	"crypto/rand"
	"encoding/hex"
	"sync"
	"time"

	"app.duinch/adb-device-manager/internal/domain"
)

const maxRecordsPerDevice = 25

type BroadcastFunc func(serial string, record domain.DeviceCommandRecord)

type CommandTracker struct {
	mu        sync.RWMutex
	records   map[string][]domain.DeviceCommandRecord
	broadcast BroadcastFunc
}

func NewCommandTracker() *CommandTracker {
	return &CommandTracker{
		records: make(map[string][]domain.DeviceCommandRecord),
	}
}

func (t *CommandTracker) SetBroadcast(fn BroadcastFunc) {
	t.mu.Lock()
	defer t.mu.Unlock()
	t.broadcast = fn
}

func (t *CommandTracker) RecordStart(serial, command, source string) func(success bool, output, errText string) {
	if serial == "" {
		serial = "global"
	}
	var idBytes [4]byte
	_, _ = rand.Read(idBytes[:])
	id := hex.EncodeToString(idBytes[:])
	start := time.Now().UnixMilli()

	rec := domain.DeviceCommandRecord{
		ID:         id,
		Device:     serial,
		Command:    command,
		Source:     source,
		Status:     "running",
		StartTime:  start,
		DurationMs: 0,
	}

	t.mu.Lock()
	list := t.records[serial]
	updated := append([]domain.DeviceCommandRecord{rec}, list...)
	if len(updated) > maxRecordsPerDevice {
		updated = updated[:maxRecordsPerDevice]
	}
	t.records[serial] = updated
	bc := t.broadcast
	t.mu.Unlock()

	if bc != nil {
		bc(serial, rec)
	}

	return func(success bool, output, errText string) {
		end := time.Now().UnixMilli()
		duration := end - start
		status := "success"
		if !success {
			status = "failed"
		}
		if len(output) > 2048 {
			output = output[:2048] + "\n...(truncated)"
		}
		if len(errText) > 1024 {
			errText = errText[:1024] + "\n...(truncated)"
		}

		t.mu.Lock()
		curList := t.records[serial]
		for i := range curList {
			if curList[i].ID == id {
				curList[i].Status = status
				curList[i].DurationMs = duration
				curList[i].Output = output
				curList[i].Error = errText
				rec = curList[i]
				break
			}
		}
		bc = t.broadcast
		t.mu.Unlock()

		if bc != nil {
			bc(serial, rec)
		}
	}
}

func (t *CommandTracker) GetRecent(serial string) []domain.DeviceCommandRecord {
	t.mu.RLock()
	defer t.mu.RUnlock()
	list := t.records[serial]
	if len(list) == 0 {
		return nil
	}
	cp := make([]domain.DeviceCommandRecord, len(list))
	copy(cp, list)
	return cp
}
