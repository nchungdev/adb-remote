package usecase

import "app.duinch/adb-device-manager/internal/domain"

type TunnelService struct {
	inspector TunnelInspector
	history   HistoryRepository
}

func NewTunnelService(inspector TunnelInspector, history HistoryRepository) *TunnelService {
	return &TunnelService{inspector: inspector, history: history}
}

func (s *TunnelService) ListActive() []domain.TunnelInfo {
	return s.inspector.ListActiveTunnels()
}

func (s *TunnelService) ListHistory() []domain.TunnelHistoryEntry {
	active := s.inspector.ListActiveTunnels()
	closed, _ := s.history.ListClosed()

	merged := make([]domain.TunnelHistoryEntry, 0, len(active)+len(closed))
	for _, t := range active {
		merged = append(merged, domain.TunnelHistoryEntry{
			SessionID:    t.SessionID,
			Alias:        t.Alias,
			LocalAdbPort: t.LocalAdbPort,
			MappedAddr:   t.MappedAddr,
			ConnectedAt:  t.ConnectedAt,
			EndedAt:      nil,
			BytesBridged: t.BytesBridged,
			Active:       true,
			Reason:       "Đang trực tuyến",
		})
	}
	merged = append(merged, closed...)
	if len(merged) > domain.MaxHistoryEntries {
		merged = merged[:domain.MaxHistoryEntries]
	}
	return merged
}

func (s *TunnelService) ClearHistory() error {
	return s.history.ClearClosed()
}
