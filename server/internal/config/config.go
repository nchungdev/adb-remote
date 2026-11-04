package config

import (
	"errors"
	"flag"
	"fmt"
	"log/slog"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
)

type Config struct {
	ListenAddr     string
	DataDir        string
	BaseTunnelPort int
	AdbBin         string
	LogLevel       slog.Level
	ForcePath      string
}

func Load(args []string) (Config, error) {
	fs := flag.NewFlagSet("adb-device-manager", flag.ContinueOnError)
	defaultDataDir := defaultRuntimeDir()

	addr := fs.String("addr", envOr("ADB_MANAGER_ADDR", "0.0.0.0:8080"), "HTTP listen address")
	dataDir := fs.String("data-dir", envOr("ADB_MANAGER_DATA_DIR", defaultDataDir), "Data directory for downloads and history")
	basePort := fs.Int("base-tunnel-port", envIntOr("ADB_MANAGER_BASE_TUNNEL_PORT", 15555), "Starting loopback port for reverse tunnels")
	adbBin := fs.String("adb-bin", envOr("ADB_BIN", ""), "Explicit path to adb binary")
	logLvl := fs.String("log-level", envOr("ADB_MANAGER_LOG_LEVEL", "info"), "Log level (debug, info, warn, error)")
	forcePath := fs.String("force-path", envOr("ADB_MANAGER_FORCE_PATH", "auto"), "Force connection path: auto, lan, or remote")

	if err := fs.Parse(args); err != nil {
		return Config{}, fmt.Errorf("parse flags: %w", err)
	}

	ensureHomebrewAndSdkPath()
	resolvedAdb := strings.TrimSpace(*adbBin)
	if resolvedAdb == "" {
		resolvedAdb = ResolveAdbBinary()
	}

	cfg := Config{
		ListenAddr:     strings.TrimSpace(*addr),
		DataDir:        strings.TrimSpace(*dataDir),
		BaseTunnelPort: *basePort,
		AdbBin:         resolvedAdb,
		LogLevel:       parseLogLevel(*logLvl),
		ForcePath:      strings.ToLower(strings.TrimSpace(*forcePath)),
	}
	if err := cfg.Validate(); err != nil {
		return Config{}, err
	}
	return cfg, nil
}

func (c Config) Validate() error {
	if c.ListenAddr == "" {
		return errors.New("listen address must not be empty")
	}
	if c.DataDir == "" {
		return errors.New("data directory must not be empty")
	}
	if c.BaseTunnelPort < 1024 || c.BaseTunnelPort > 60000 {
		return fmt.Errorf("base tunnel port %d out of range [1024..60000]", c.BaseTunnelPort)
	}
	switch strings.ToLower(c.ForcePath) {
	case "", "auto", "lan", "remote", "wifi", "local", "webrtc", "tunnel":
	default:
		return errors.New("invalid force-path: must be auto, lan, or remote")
	}
	return nil
}

func NewLogger(level slog.Level) *slog.Logger {
	opts := &slog.HandlerOptions{
		Level: level,
		ReplaceAttr: func(_ []string, a slog.Attr) slog.Attr {
			k := strings.ToLower(a.Key)
			if strings.Contains(k, "secret") || strings.Contains(k, "token") || strings.Contains(k, "cookie") {
				return slog.String(a.Key, "[REDACTED]")
			}
			return a
		},
	}
	return slog.New(slog.NewTextHandler(os.Stdout, opts))
}

func ResolveAdbBinary() string {
	home, _ := os.UserHomeDir()
	candidates := []string{
		"/opt/homebrew/bin/adb",
		filepath.Join(home, "Library", "Android", "sdk", "platform-tools", "adb"),
		"/usr/local/bin/adb",
	}
	for _, p := range candidates {
		if info, err := os.Stat(p); err == nil && !info.IsDir() && info.Mode()&0o111 != 0 {
			return p
		}
	}
	if found, err := exec.LookPath("adb"); err == nil {
		return found
	}
	return "adb"
}

func ensureHomebrewAndSdkPath() {
	home, _ := os.UserHomeDir()
	extras := []string{
		"/opt/homebrew/bin",
		filepath.Join(home, "Library", "Android", "sdk", "platform-tools"),
		"/usr/local/bin",
	}
	cur := os.Getenv("PATH")
	parts := strings.Split(cur, string(os.PathListSeparator))
	seen := make(map[string]bool, len(parts))
	for _, p := range parts {
		seen[p] = true
	}
	var prepend []string
	for _, dir := range extras {
		if !seen[dir] {
			if info, err := os.Stat(dir); err == nil && info.IsDir() {
				prepend = append(prepend, dir)
			}
		}
	}
	if len(prepend) > 0 {
		_ = os.Setenv("PATH", strings.Join(append(prepend, cur), string(os.PathListSeparator)))
	}
}

func defaultRuntimeDir() string {
	home, err := os.UserHomeDir()
	if err != nil || home == "" {
		return "."
	}
	return filepath.Join(home, ".local", "share", "adb-device-manager")
}

func envOr(key, fallback string) string {
	if v := strings.TrimSpace(os.Getenv(key)); v != "" {
		return v
	}
	return fallback
}

func envIntOr(key string, fallback int) int {
	if v := strings.TrimSpace(os.Getenv(key)); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return fallback
}

func parseLogLevel(raw string) slog.Level {
	switch strings.ToLower(strings.TrimSpace(raw)) {
	case "debug":
		return slog.LevelDebug
	case "warn", "warning":
		return slog.LevelWarn
	case "error":
		return slog.LevelError
	default:
		return slog.LevelInfo
	}
}
