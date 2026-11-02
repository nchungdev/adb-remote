package zeroconfadv

import (
	"log/slog"
	"os"
	"strings"

	"github.com/grandcat/zeroconf"
)

// Advertiser wraps zeroconf.Server to broadcast Bonjour / mDNS service.
type Advertiser struct {
	server *zeroconf.Server
	logger *slog.Logger
}

// Start registers an mDNS service on local network (e.g. _adb-remote._tcp or _agent-remote._tcp).
func Start(serviceType string, port int, logger *slog.Logger) *Advertiser {
	hostName, _ := os.Hostname()
	hostName = strings.TrimSuffix(hostName, ".local")
	if hostName == "" {
		hostName = "Mac"
	}

	serviceName := strings.TrimSuffix(serviceType, ".")
	if !strings.HasSuffix(serviceName, "._tcp") {
		serviceName += "._tcp"
	}

	server, err := zeroconf.Register(
		hostName,
		serviceName,
		"local.",
		port,
		[]string{"version=1.0", "name=" + hostName},
		nil,
	)
	if err != nil {
		if logger != nil {
			logger.Warn("failed to register zeroconf mDNS advertiser", "service", serviceType, "err", err)
		}
		return &Advertiser{logger: logger}
	}

	if logger != nil {
		logger.Info("registered mDNS advertiser", "service", serviceType, "port", port, "instance", hostName)
	}

	return &Advertiser{
		server: server,
		logger: logger,
	}
}

// Stop shuts down the mDNS advertisement cleanly.
func (a *Advertiser) Stop() {
	if a != nil && a.server != nil {
		a.server.Shutdown()
		if a.logger != nil {
			a.logger.Info("stopped mDNS advertiser")
		}
	}
}
