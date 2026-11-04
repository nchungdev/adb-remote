package usecase

import (
	"context"
	"io"

	"app.duinch/adb-device-manager/internal/domain"
)

type RawAdbDevice struct {
	Serial      string
	State       string
	Product     string
	Model       string
	DeviceCode  string
	TransportID string
	// OverUSB is set only when adb names a USB port, so TCP and emulator devices never count.
	OverUSB bool
}

type AdbRunner interface {
	ListRawDevices(ctx context.Context) ([]RawAdbDevice, error)
	QuerySpecsCached(ctx context.Context, serial string) (domain.DeviceSpecs, error)
	Connect(ctx context.Context, addr string) (domain.CommandResult, error)
	Disconnect(ctx context.Context, addr string) (domain.CommandResult, error)
	Pair(ctx context.Context, addr, code string) (domain.CommandResult, error)
	EnableTcpip(ctx context.Context, serial string, port int) (domain.CommandResult, error)
	Reboot(ctx context.Context, serial string) (domain.CommandResult, error)
	RunShell(ctx context.Context, serial, command string) (domain.CommandResult, error)
	CaptureScreenshot(ctx context.Context, serial string) ([]byte, error)
	GetLogcat(ctx context.Context, serial string, lines int, filter string) (string, error)
	ScanMdns(ctx context.Context) ([]string, error)
	RestartServer(ctx context.Context) error
}

type TunnelInspector interface {
	ListActiveTunnels() []domain.TunnelInfo
}

type HistoryRepository interface {
	ListClosed() ([]domain.TunnelHistoryEntry, error)
	AppendClosed(entry domain.TunnelHistoryEntry) error
	ClearClosed() error
}

type OtaRepository interface {
	LoadManifest() (domain.AppVersionManifest, error)
	OpenApk() (io.ReadCloser, int64, domain.AppVersionManifest, error)
}

type CommandAuditor interface {
	RecordStart(serial, command, source string) func(success bool, output, errText string)
	GetRecent(serial string) []domain.DeviceCommandRecord
}
