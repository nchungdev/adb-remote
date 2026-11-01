package tray

import (
	"crypto/rand"
	"fmt"
	"net"
	"os"
	"os/exec"
	"path/filepath"
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

func LoadOrGenerateRoomCode(dataDir string) string {
	codeFile := filepath.Join(dataDir, "room_code")
	if data, err := os.ReadFile(codeFile); err == nil {
		code := strings.TrimSpace(string(data))
		if len(code) >= 6 {
			return code
		}
	}

	b := make([]byte, 3)
	_, _ = rand.Read(b)
	num := (int(b[0])<<16 | int(b[1])<<8 | int(b[2]))%900000 + 100000
	code := fmt.Sprintf("%03d-%03d", num/1000, num%1000)

	_ = os.MkdirAll(dataDir, 0755)
	_ = os.WriteFile(codeFile, []byte(code), 0600)
	return code
}
