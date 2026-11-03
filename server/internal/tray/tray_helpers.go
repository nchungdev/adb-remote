package tray

import (
	"fmt"
	"net"
	"os/exec"
	"strings"
)

func getLocalLANIP() string {
	conn, err := net.Dial("udp", "8.8.8.8:80")
	if err == nil {
		defer conn.Close()
		if localAddr, ok := conn.LocalAddr().(*net.UDPAddr); ok {
			return localAddr.IP.String()
		}
	}
	return "127.0.0.1"
}

func copyToClipboard(text string) {
	cmd := exec.Command("pbcopy")
	cmd.Stdin = strings.NewReader(text)
	_ = cmd.Run()

	notify("ADB Device Manager", "Copied to clipboard", text)
}

func notify(title, subtitle, body string) {
	script := fmt.Sprintf(`display notification "%s" with title "%s" subtitle "%s"`, body, title, subtitle)
	_ = exec.Command("osascript", "-e", script).Run()
}

func openBrowser(targetUrl string) {
	_ = exec.Command("open", targetUrl).Start()
}
