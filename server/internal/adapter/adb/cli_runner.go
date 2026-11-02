package adb

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"os/exec"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

	"app.duinch/adb-device-manager/internal/domain"
	"app.duinch/adb-device-manager/internal/usecase"
)

var (
	reBrand  = regexp.MustCompile(`__BRAND__=(\S+)`)
	reRel    = regexp.MustCompile(`__REL__=(\S+)`)
	reSDK    = regexp.MustCompile(`__SDK__=(\S+)`)
	reLevel  = regexp.MustCompile(`level:\s*(\d+)`)
	reStatus = regexp.MustCompile(`status:\s*(\d+)`)
	reInet   = regexp.MustCompile(`inet\s+(\d+\.\d+\.\d+\.\d+)`)
)

const specsProbeCmd = "echo __BRAND__=$(getprop ro.product.brand); " +
	"echo __REL__=$(getprop ro.build.version.release); " +
	"echo __SDK__=$(getprop ro.build.version.sdk); " +
	"dumpsys battery 2>/dev/null | grep -E 'level:|status:'; " +
	"ip -f inet addr show wlan0 2>/dev/null | grep inet"

type cachedSpecs struct {
	fetchedAt time.Time
	data      domain.DeviceSpecs
}

type CLIRunner struct {
	adbBin string
	mu     sync.RWMutex
	cache  map[string]cachedSpecs
}

func NewCLIRunner(adbBin string) *CLIRunner {
	return &CLIRunner{
		adbBin: adbBin,
		cache:  make(map[string]cachedSpecs),
	}
}

func (r *CLIRunner) ListRawDevices(ctx context.Context) ([]usecase.RawAdbDevice, error) {
	res, err := r.execText(ctx, 10*time.Second, "devices", "-l")
	if err != nil {
		return nil, err
	}
	return ParseDevicesOutput(res.Stdout), nil
}

func (r *CLIRunner) QuerySpecsCached(ctx context.Context, serial string) (domain.DeviceSpecs, error) {
	r.mu.RLock()
	entry, ok := r.cache[serial]
	r.mu.RUnlock()
	if ok && time.Since(entry.fetchedAt) < 15*time.Second {
		return entry.data, nil
	}

	res, err := r.execText(ctx, 4*time.Second, "-s", serial, "shell", specsProbeCmd)
	if err != nil || !res.Success || res.Stdout == "" {
		return domain.DeviceSpecs{}, err
	}
	specs := ParseDeviceSpecs(res.Stdout)
	r.mu.Lock()
	r.cache[serial] = cachedSpecs{fetchedAt: time.Now(), data: specs}
	r.mu.Unlock()
	return specs, nil
}

func (r *CLIRunner) Connect(ctx context.Context, addr string) (domain.CommandResult, error) {
	return r.execText(ctx, 10*time.Second, "connect", addr)
}

func (r *CLIRunner) Disconnect(ctx context.Context, addr string) (domain.CommandResult, error) {
	return r.execText(ctx, 8*time.Second, "disconnect", addr)
}

func (r *CLIRunner) Pair(ctx context.Context, addr, code string) (domain.CommandResult, error) {
	return r.execText(ctx, 12*time.Second, "pair", addr, code)
}

func (r *CLIRunner) EnableTcpip(ctx context.Context, serial string, port int) (domain.CommandResult, error) {
	portStr := strconv.Itoa(port)
	if serial != "" {
		return r.execText(ctx, 10*time.Second, "-s", serial, "tcpip", portStr)
	}
	return r.execText(ctx, 10*time.Second, "tcpip", portStr)
}

func (r *CLIRunner) Reboot(ctx context.Context, serial string) (domain.CommandResult, error) {
	if serial != "" {
		return r.execText(ctx, 5*time.Second, "-s", serial, "reboot")
	}
	return r.execText(ctx, 5*time.Second, "reboot")
}

func (r *CLIRunner) RunShell(ctx context.Context, serial, command string) (domain.CommandResult, error) {
	return r.execText(ctx, 20*time.Second, "-s", serial, "shell", command)
}

func (r *CLIRunner) CaptureScreenshot(ctx context.Context, serial string) ([]byte, error) {
	cctx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()

	cmd := exec.CommandContext(cctx, r.adbBin, "-s", serial, "exec-out", "screencap", "-p")
	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	if err := cmd.Run(); err != nil {
		msg := strings.TrimSpace(stderr.String())
		if msg == "" {
			msg = err.Error()
		}
		return nil, fmt.Errorf("screenshot %s: %s", serial, msg)
	}
	if stdout.Len() == 0 {
		return nil, errors.New("empty screenshot payload")
	}
	return stdout.Bytes(), nil
}

func (r *CLIRunner) GetLogcat(ctx context.Context, serial string, lineCount int, filter string) (string, error) {
	if lineCount <= 0 || lineCount > 2000 {
		lineCount = 300
	}
	args := []string{"-s", serial, "logcat", "-d", "-v", "time", "-t", strconv.Itoa(lineCount)}
	if f := strings.TrimSpace(filter); f != "" {
		args = append(args, f)
	}
	cctx, cancel := context.WithTimeout(ctx, 8*time.Second)
	defer cancel()

	cmd := exec.CommandContext(cctx, r.adbBin, args...)
	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	if err := cmd.Run(); err != nil {
		msg := strings.TrimSpace(stderr.String())
		if msg == "" {
			msg = err.Error()
		}
		return "", fmt.Errorf("logcat %s: %s", serial, msg)
	}
	return stdout.String(), nil
}

func (r *CLIRunner) ScanMdns(ctx context.Context) ([]string, error) {
	res, err := r.execText(ctx, 4*time.Second, "mdns", "services")
	if err != nil {
		return nil, err
	}
	lines := strings.Split(res.Stdout, "\n")
	var services []string
	for _, l := range lines {
		line := strings.TrimSpace(l)
		if line == "" || strings.HasPrefix(line, "List of") || strings.HasPrefix(line, "*") {
			continue
		}
		services = append(services, line)
	}
	return services, nil
}

func (r *CLIRunner) RestartServer(ctx context.Context) error {
	_, _ = r.execText(ctx, 8*time.Second, "kill-server")
	time.Sleep(500 * time.Millisecond)
	_, err := r.execText(ctx, 8*time.Second, "start-server")
	return err
}

func (r *CLIRunner) execText(ctx context.Context, defaultTimeout time.Duration, args ...string) (domain.CommandResult, error) {
	cctx, cancel := context.WithTimeout(ctx, defaultTimeout)
	defer cancel()

	cmd := exec.CommandContext(cctx, r.adbBin, args...)
	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	err := cmd.Run()

	if errors.Is(cctx.Err(), context.DeadlineExceeded) {
		return domain.CommandResult{
			Success:    false,
			Stderr:     "Command timed out",
			ReturnCode: -1,
		}, cctx.Err()
	}

	exitCode := 0
	if err != nil {
		exitCode = -1
		var exitErr *exec.ExitError
		if errors.As(err, &exitErr) {
			exitCode = exitErr.ExitCode()
		}
	}

	errText := strings.TrimSpace(stderr.String())
	if err != nil && errText == "" {
		errText = err.Error()
	}

	return domain.CommandResult{
		Success:    err == nil && exitCode == 0,
		Stdout:     strings.TrimSpace(stdout.String()),
		Stderr:     errText,
		ReturnCode: exitCode,
	}, nil
}

func ParseDevicesOutput(stdout string) []usecase.RawAdbDevice {
	lines := strings.Split(stdout, "\n")
	var out []usecase.RawAdbDevice
	for i, rawLine := range lines {
		if i == 0 && strings.HasPrefix(strings.TrimSpace(rawLine), "List of devices") {
			continue
		}
		line := strings.TrimSpace(rawLine)
		if line == "" || strings.HasPrefix(line, "*") {
			continue
		}
		parts := strings.Fields(line)
		if len(parts) < 2 {
			continue
		}
		dev := usecase.RawAdbDevice{
			Serial: parts[0],
			State:  parts[1],
		}
		for _, kv := range parts[2:] {
			k, v, ok := strings.Cut(kv, ":")
			if !ok {
				continue
			}
			switch k {
			case "product":
				dev.Product = v
			case "model":
				dev.Model = v
			case "device":
				dev.DeviceCode = v
			case "transport_id":
				dev.TransportID = v
			}
		}
		out = append(out, dev)
	}
	return out
}

func ParseDeviceSpecs(stdout string) domain.DeviceSpecs {
	var specs domain.DeviceSpecs
	if m := reBrand.FindStringSubmatch(stdout); len(m) == 2 {
		v := m[1]
		specs.Brand = &v
	}
	if m := reRel.FindStringSubmatch(stdout); len(m) == 2 {
		v := m[1]
		specs.AndroidVersion = &v
	}
	if m := reSDK.FindStringSubmatch(stdout); len(m) == 2 {
		v := m[1]
		specs.SDK = &v
	}
	if m := reLevel.FindStringSubmatch(stdout); len(m) == 2 {
		if lvl, err := strconv.Atoi(m[1]); err == nil {
			charging := false
			if sm := reStatus.FindStringSubmatch(stdout); len(sm) == 2 && sm[1] == "2" {
				charging = true
			}
			specs.Battery = &domain.BatteryInfo{Level: lvl, Charging: charging}
		}
	}
	if m := reInet.FindStringSubmatch(stdout); len(m) == 2 {
		v := m[1]
		specs.WifiIP = &v
	}
	return specs
}
