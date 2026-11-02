package domain

import "strings"

const MaxHistoryEntries = 30

type TunnelInfo struct {
	SessionID    string `json:"session_id"`
	Alias        string `json:"alias"`
	LocalAdbPort int    `json:"local_adb_port"`
	MappedAddr   string `json:"mapped_addr"`
	ConnectedAt  int64  `json:"connected_at"`
	BytesBridged int64  `json:"bytes_bridged"`
}

type TunnelHistoryEntry struct {
	SessionID    string `json:"session_id"`
	Alias        string `json:"alias"`
	LocalAdbPort int    `json:"local_adb_port"`
	MappedAddr   string `json:"mapped_addr"`
	ConnectedAt  int64  `json:"connected_at"`
	EndedAt      *int64 `json:"ended_at"`
	BytesBridged int64  `json:"bytes_bridged"`
	Active       bool   `json:"active"`
	Reason       string `json:"reason"`
}

func SanitizeAlias(raw string) string {
	trimmed := strings.TrimSpace(raw)
	if trimmed == "" {
		return "Android-Device"
	}
	if len(trimmed) > 64 {
		return trimmed[:64]
	}
	return trimmed
}

func SanitizePort(port int) int {
	if port < 1 || port > 65535 {
		return 5555
	}
	return port
}
