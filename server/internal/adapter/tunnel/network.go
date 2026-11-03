package tunnel

import (
	"net"
	"os/exec"
	"strings"
)

func findPrimaryLanIP() string {
	ifaces, err := net.Interfaces()
	if err != nil {
		return ""
	}
	for _, iface := range ifaces {
		if iface.Flags&net.FlagLoopback != 0 || iface.Flags&net.FlagUp == 0 {
			continue
		}
		addrs, err := iface.Addrs()
		if err != nil {
			continue
		}
		for _, a := range addrs {
			ipNet, ok := a.(*net.IPNet)
			if !ok || ipNet.IP.IsLoopback() {
				continue
			}
			ip4 := ipNet.IP.To4()
			if ip4 != nil {
				ipStr := ip4.String()
				if strings.HasPrefix(ipStr, "192.168.") || strings.HasPrefix(ipStr, "10.") || strings.HasPrefix(ipStr, "172.") {
					return ipStr
				}
			}
		}
	}
	return ""
}

func detectServerSSID() string {
	out, err := exec.Command("ipconfig", "getsummary", "en1").Output()
	if err == nil {
		lines := strings.Split(string(out), "\n")
		for _, line := range lines {
			trimmed := strings.TrimSpace(line)
			if strings.HasPrefix(trimmed, "SSID :") {
				return strings.TrimSpace(strings.TrimPrefix(trimmed, "SSID :"))
			}
		}
	}
	return ""
}

// PathKind ranks how a device reaches the server; a higher value is preferred.
type PathKind int

const (
	PathRemote PathKind = iota
	PathLAN
)

func (p PathKind) String() string {
	if p == PathLAN {
		return "lan"
	}
	return "remote"
}

// PathForHost treats private addresses as LAN. Loopback is the local cloudflared
// forwarding a Quick Tunnel client, so it counts as remote.
func PathForHost(host string) PathKind {
	if ip := net.ParseIP(host); ip != nil && ip.IsPrivate() {
		return PathLAN
	}
	return PathRemote
}
