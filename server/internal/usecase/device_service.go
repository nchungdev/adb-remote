package usecase

import (
	"context"
	"fmt"
	"strings"
	"sync"
	"time"

	"app.duinch/adb-device-manager/internal/domain"
)

type DeviceService struct {
	adb            AdbRunner
	tunnels        TunnelInspector
	auditor        CommandAuditor
	history        HistoryRepository
	mu             sync.RWMutex
	roomCode       string
	remoteActive    bool
	activeRemoteDev string
	lastTunnelURL   string
}

func NewDeviceService(adb AdbRunner, tunnels TunnelInspector, auditor CommandAuditor) *DeviceService {
	return &DeviceService{
		adb:          adb,
		tunnels:      tunnels,
		auditor:      auditor,
		remoteActive: true,
	}
}

func (s *DeviceService) SetHistory(history HistoryRepository) {
	s.history = history
}

func (s *DeviceService) ListDevices(ctx context.Context) ([]domain.Device, []domain.TunnelInfo, error) {
	activeList := s.tunnels.ListActiveTunnels()
	tunnelByAddr := make(map[string]domain.TunnelInfo, len(activeList))
	for _, t := range activeList {
		tunnelByAddr[t.MappedAddr] = t
	}

	raw, err := s.adb.ListRawDevices(ctx)
	if err != nil {
		raw = nil
	}

	presentState := make(map[string]string, len(raw))
	for _, r := range raw {
		presentState[r.Serial] = r.State
	}

	reconnected := false
	for addr := range tunnelByAddr {
		st, exists := presentState[addr]
		if !exists || st == "offline" {
			if st == "offline" {
				discCtx, cancel := context.WithTimeout(ctx, 3*time.Second)
				_, _ = s.adb.Disconnect(discCtx, addr)
				cancel()
			}
			connCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
			_, _ = s.adb.Connect(connCtx, addr)
			cancel()
			reconnected = true
		}
	}
	if reconnected {
		if refreshed, err := s.adb.ListRawDevices(ctx); err == nil {
			raw = refreshed
		}
	}

	devices := make([]domain.Device, 0, len(raw)+len(activeList))
	seen := make(map[string]bool, len(raw))

	for _, r := range raw {
		seen[r.Serial] = true
		hostPart := r.Serial
		if idx := strings.IndexByte(r.Serial, ':'); idx >= 0 {
			hostPart = r.Serial[:idx]
		}
		isRemote := strings.Contains(r.Serial, ":") || strings.Contains(hostPart, ".")

		isTunnel := strings.HasPrefix(r.Serial, "127.0.0.1:") || strings.HasPrefix(r.Serial, "localhost:")
		dev := domain.Device{
			Serial:      r.Serial,
			State:       r.State,
			Product:     r.Product,
			Model:       r.Model,
			DeviceCode:  r.DeviceCode,
			TransportID: r.TransportID,
			IsRemote:    isRemote,
			IsTunnel:    isTunnel,
		}

		if tmeta, ok := tunnelByAddr[r.Serial]; ok {
			alias := tmeta.Alias
			localPort := tmeta.LocalAdbPort
			connAt := tmeta.ConnectedAt
			bridged := tmeta.BytesBridged
			dev.IsTunnel = true
			dev.TunnelAlias = &alias
			dev.TunnelLocalPort = &localPort
			dev.TunnelConnectedAt = &connAt
			dev.TunnelBytes = &bridged
		} else if isTunnel {
			parts := strings.Split(r.Serial, ":")
			if len(parts) == 2 {
				var port int
				if n, _ := fmt.Sscanf(parts[1], "%d", &port); n == 1 {
					dev.TunnelLocalPort = &port
				}
			}
			if s.history != nil {
				if closed, err := s.history.ListClosed(); err == nil {
					for _, c := range closed {
						if c.MappedAddr == r.Serial || (dev.TunnelLocalPort != nil && c.LocalAdbPort == *dev.TunnelLocalPort) {
							alias := c.Alias
							dev.TunnelAlias = &alias
							break
						}
					}
				}
			}
			if dev.TunnelAlias == nil {
				defaultAlias := "P2P WebRTC"
				dev.TunnelAlias = &defaultAlias
			}
		}

		if r.State == "device" {
			specCtx, cancel := context.WithTimeout(ctx, 4*time.Second)
			if specs, err := s.adb.QuerySpecsCached(specCtx, r.Serial); err == nil {
				dev.Brand = specs.Brand
				dev.AndroidVersion = specs.AndroidVersion
				dev.SDK = specs.SDK
				dev.Battery = specs.Battery
				dev.WifiIP = specs.WifiIP
			}
			cancel()
		}
		if s.auditor != nil {
			dev.RecentCommands = s.auditor.GetRecent(r.Serial)
		}
		devices = append(devices, dev)
	}

	for addr, tmeta := range tunnelByAddr {
		if seen[addr] {
			continue
		}
		alias := tmeta.Alias
		localPort := tmeta.LocalAdbPort
		connAt := tmeta.ConnectedAt
		bridged := tmeta.BytesBridged
		var recent []domain.DeviceCommandRecord
		if s.auditor != nil {
			recent = s.auditor.GetRecent(addr)
		}
		devices = append(devices, domain.Device{
			Serial:            addr,
			State:             "tunnel_only",
			Model:             tmeta.Alias,
			IsRemote:          true,
			IsTunnel:          true,
			TunnelAlias:       &alias,
			TunnelLocalPort:   &localPort,
			TunnelConnectedAt: &connAt,
			TunnelBytes:       &bridged,
			RecentCommands:    recent,
		})
	}

	return devices, activeList, nil
}

func (s *DeviceService) Connect(ctx context.Context, addr string) (domain.CommandResult, error) {
	clean := strings.TrimSpace(addr)
	if clean == "" {
		return domain.CommandResult{}, domain.NewValidationError("missing address")
	}
	var done func(bool, string, string)
	if s.auditor != nil {
		done = s.auditor.RecordStart(clean, "adb connect "+clean, "connect")
	}
	res, err := s.adb.Connect(ctx, clean)
	if done != nil {
		done(res.Success, res.Stdout, res.Stderr)
	}
	return res, err
}

func (s *DeviceService) Disconnect(ctx context.Context, addr string) (domain.CommandResult, error) {
	clean := strings.TrimSpace(addr)
	if clean == "" {
		return domain.CommandResult{}, domain.NewValidationError("missing address")
	}
	var done func(bool, string, string)
	if s.auditor != nil {
		done = s.auditor.RecordStart(clean, "adb disconnect "+clean, "disconnect")
	}
	res, err := s.adb.Disconnect(ctx, clean)
	if done != nil {
		done(res.Success, res.Stdout, res.Stderr)
	}
	return res, err
}

func (s *DeviceService) Pair(ctx context.Context, addr, code string) (domain.CommandResult, error) {
	cleanAddr := strings.TrimSpace(addr)
	cleanCode := strings.TrimSpace(code)
	if cleanAddr == "" || cleanCode == "" {
		return domain.CommandResult{}, domain.NewValidationError("missing addr or code")
	}
	var done func(bool, string, string)
	if s.auditor != nil {
		done = s.auditor.RecordStart(cleanAddr, "adb pair "+cleanAddr+" "+cleanCode, "pair")
	}
	res, err := s.adb.Pair(ctx, cleanAddr, cleanCode)
	if done != nil {
		done(res.Success, res.Stdout, res.Stderr)
	}
	return res, err
}

func (s *DeviceService) EnableTcpip(ctx context.Context, serial string, port int, autoConnect bool) (domain.CommandResult, error) {
	cleanSer := strings.TrimSpace(serial)
	sanPort := domain.SanitizePort(port)
	var done func(bool, string, string)
	if s.auditor != nil {
		done = s.auditor.RecordStart(cleanSer, fmt.Sprintf("adb tcpip %d", sanPort), "tcpip")
	}
	res, err := s.adb.EnableTcpip(ctx, cleanSer, sanPort)
	if done != nil {
		done(res.Success, res.Stdout, res.Stderr)
	}
	if err == nil && res.Success && autoConnect && cleanSer != "" {
		if specs, specErr := s.adb.QuerySpecsCached(ctx, cleanSer); specErr == nil && specs.WifiIP != nil && *specs.WifiIP != "" {
			wifiAddr := fmt.Sprintf("%s:%d", *specs.WifiIP, sanPort)
			time.Sleep(400 * time.Millisecond)
			connRes, _ := s.Connect(ctx, wifiAddr)
			if connRes.Success {
				res.Stdout += fmt.Sprintf(" • Tự động kết nối Wi-Fi tới %s", wifiAddr)
			}
		}
	}
	return res, err
}

func (s *DeviceService) ScanMdns(ctx context.Context) ([]string, error) {
	return s.adb.ScanMdns(ctx)
}

func (s *DeviceService) Reboot(ctx context.Context, serial string) (domain.CommandResult, error) {
	cleanSer := strings.TrimSpace(serial)
	var done func(bool, string, string)
	if s.auditor != nil {
		done = s.auditor.RecordStart(cleanSer, "adb reboot", "reboot")
	}
	res, err := s.adb.Reboot(ctx, cleanSer)
	if done != nil {
		done(res.Success, res.Stdout, res.Stderr)
	}
	return res, err
}

func (s *DeviceService) RunShell(ctx context.Context, serial, cmd string) (domain.CommandResult, error) {
	cleanCmd := strings.TrimSpace(cmd)
	if cleanCmd == "" {
		return domain.CommandResult{}, domain.NewValidationError("empty command")
	}
	cleanSer := strings.TrimSpace(serial)
	var done func(bool, string, string)
	if s.auditor != nil {
		done = s.auditor.RecordStart(cleanSer, "adb shell "+cleanCmd, "shell")
	}
	res, err := s.adb.RunShell(ctx, cleanSer, cleanCmd)
	if done != nil {
		done(res.Success, res.Stdout, res.Stderr)
	}
	return res, err
}
