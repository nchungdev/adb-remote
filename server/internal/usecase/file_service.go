package usecase

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"os"
	"path"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"

	"app.duinch/adb-device-manager/internal/domain"
)

var (
	ErrFileBadRequest = errors.New("invalid device, path or file name")
	ErrFileExists     = errors.New("file already exists")
)

// FileTransferRunner is what moving files needs from adb.
type FileTransferRunner interface {
	RunShell(ctx context.Context, serial, command string) (domain.CommandResult, error)
	Push(ctx context.Context, serial, localPath, remotePath string) (domain.CommandResult, error)
	Pull(ctx context.Context, serial, remotePath, localPath string) (domain.CommandResult, error)
}

// DeviceFile is one entry of a device directory.
type DeviceFile struct {
	Name     string `json:"name"`
	Path     string `json:"path"`
	IsDir    bool   `json:"is_dir"`
	Size     int64  `json:"size"`
	Modified string `json:"modified"`
}

// DownloadedFile is a device file copied to a local temp file; Remove must be called.
type DownloadedFile struct {
	LocalPath string
	Name      string
	Size      int64
}

func (f DownloadedFile) Remove() { _ = os.Remove(f.LocalPath) }

// InstallStatus is how a started install stands.
type InstallStatus struct {
	State  string `json:"state"` // running, success or failure
	Output string `json:"output,omitempty"`
}

const (
	installDir = "/data/local/tmp"
	// The tunnel app holds the connection adb uses when it is the app being replaced.
	selfPackage  = "app.duinch.adbagent"
	selfActivity = selfPackage + "/.MainActivity"
)

var (
	deviceRoots = []string{"/sdcard", "/storage", installDir}
	serialRe    = regexp.MustCompile(`^[A-Za-z0-9._:\[\]][A-Za-z0-9._:\[\]-]*$`)
	jobRe       = regexp.MustCompile(`^[0-9a-f]{16}$`)
	lsLineRe    = regexp.MustCompile(`^([dlcbps-])[rwxsStT-]{9}[+.]?\s+\d+\s+\S+\s+\S+\s+(\d+)\s+(\d{4}-\d{2}-\d{2} \d{2}:\d{2})\s(.+)$`)
)

// FileService moves files between this machine and a device over adb. It keeps to shared
// storage: adb could reach further, but this is reached from a browser.
type FileService struct {
	adb FileTransferRunner
}

func NewFileService(adb FileTransferRunner) *FileService {
	return &FileService{adb: adb}
}

func (s *FileService) List(ctx context.Context, serial, dir string) ([]DeviceFile, error) {
	clean, err := checkTarget(serial, dir)
	if err != nil {
		return nil, err
	}
	res, err := s.adb.RunShell(ctx, serial, "ls -lAL -- "+shellQuote(clean+"/"))
	if err != nil {
		return nil, err
	}
	files := parseListing(clean, res.Stdout)
	if !res.Success && len(files) == 0 {
		return nil, fmt.Errorf("%s", firstNonEmpty(res.Stderr, res.Stdout, "cannot read directory"))
	}
	return files, nil
}

// Upload stores content as dir/name on the device. A failed transfer leaves no partial new
// file behind, and never removes a file that was there before.
func (s *FileService) Upload(ctx context.Context, serial, dir, name string, content io.Reader, overwrite bool) (string, error) {
	cleanDir, err := checkTarget(serial, dir)
	if err != nil {
		return "", err
	}
	if !validFileName(name) {
		return "", ErrFileBadRequest
	}
	remote := path.Join(cleanDir, name)
	existed := s.exists(ctx, serial, remote)
	if existed && !overwrite {
		return "", ErrFileExists
	}

	tmp, err := os.CreateTemp("", "adb-upload-*")
	if err != nil {
		return "", err
	}
	defer func() { _ = os.Remove(tmp.Name()) }()
	_, err = io.Copy(tmp, content)
	if closeErr := tmp.Close(); err == nil {
		err = closeErr
	}
	if err != nil {
		return "", err
	}

	res, err := s.adb.Push(ctx, serial, tmp.Name(), remote)
	if err == nil && !res.Success {
		err = fmt.Errorf("%s", firstNonEmpty(res.Stderr, res.Stdout, "push failed"))
	}
	if err != nil {
		if !existed {
			// The request may be what was cancelled, and the leftover still has to go.
			cleanupCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
			defer cancel()
			_, _ = s.adb.RunShell(cleanupCtx, serial, "rm -f -- "+shellQuote(remote))
		}
		return "", err
	}
	return remote, nil
}

func (s *FileService) Download(ctx context.Context, serial, remote string) (DownloadedFile, error) {
	clean, err := checkTarget(serial, remote)
	if err != nil {
		return DownloadedFile{}, err
	}
	tmp, err := os.CreateTemp("", "adb-download-*")
	if err != nil {
		return DownloadedFile{}, err
	}
	_ = tmp.Close()
	file := DownloadedFile{LocalPath: tmp.Name(), Name: path.Base(clean)}

	check, err := s.adb.RunShell(ctx, serial, "test -f "+shellQuote(clean))
	if err != nil || !check.Success {
		file.Remove()
		return DownloadedFile{}, ErrFileBadRequest
	}
	res, err := s.adb.Pull(ctx, serial, clean, tmp.Name())
	if err == nil && !res.Success {
		err = fmt.Errorf("%s", firstNonEmpty(res.Stderr, res.Stdout, "pull failed"))
	}
	if err != nil {
		file.Remove()
		return DownloadedFile{}, err
	}
	info, err := os.Stat(tmp.Name())
	if err != nil {
		file.Remove()
		return DownloadedFile{}, err
	}
	file.Size = info.Size()
	return file, nil
}

// StartInstall installs an APK from a process that outlives this adb connection. Replacing
// the tunnel app ends the connection mid-install, so nothing here can wait for the result:
// the process logs it to a file for InstallResult, and reopens the tunnel app if it died.
func (s *FileService) StartInstall(ctx context.Context, serial, apk string) (string, error) {
	clean, err := checkTarget(serial, apk)
	if err != nil {
		return "", err
	}
	if !strings.HasSuffix(strings.ToLower(clean), ".apk") {
		return "", ErrFileBadRequest
	}
	buf := make([]byte, 8)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	job := hex.EncodeToString(buf)
	copied := path.Join(installDir, ".adbr-install-"+job+".apk")
	// package manager cannot always read shared storage, so it installs from a private copy.
	script := fmt.Sprintf(
		`WAS=$(pidof %[1]s); cp %[2]s %[3]s && pm install -r -t %[3]s; echo "EXIT:$?"; rm -f %[3]s; `+
			`if [ -n "$WAS" ] && ! pidof %[1]s >/dev/null; then am start -n %[4]s; fi; echo DONE`,
		selfPackage, shellQuote(clean), shellQuote(copied), selfActivity,
	)
	command := fmt.Sprintf("setsid nohup sh -c %s > %s 2>&1 < /dev/null &", shellQuote(script), shellQuote(installLog(job)))
	res, err := s.adb.RunShell(ctx, serial, command)
	if err != nil {
		return "", err
	}
	if !res.Success {
		return "", fmt.Errorf("%s", firstNonEmpty(res.Stderr, res.Stdout, "could not start install"))
	}
	return job, nil
}

// InstallResult reports running while the device cannot be reached: when it is the tunnel
// app being replaced, that is the expected state until the app is back.
func (s *FileService) InstallResult(ctx context.Context, serial, job string) (InstallStatus, error) {
	if !serialRe.MatchString(serial) || !jobRe.MatchString(job) {
		return InstallStatus{}, ErrFileBadRequest
	}
	res, err := s.adb.RunShell(ctx, serial, "cat "+shellQuote(installLog(job)))
	if err != nil || !res.Success || !strings.Contains(res.Stdout, "DONE") {
		return InstallStatus{State: "running"}, nil
	}
	_, _ = s.adb.RunShell(ctx, serial, "rm -f "+shellQuote(installLog(job)))
	state := "failure"
	if strings.Contains(res.Stdout, "EXIT:0") && strings.Contains(res.Stdout, "Success") {
		state = "success"
	}
	return InstallStatus{State: state, Output: res.Stdout}, nil
}

func (s *FileService) exists(ctx context.Context, serial, remote string) bool {
	res, err := s.adb.RunShell(ctx, serial, "test -e "+shellQuote(remote))
	return err == nil && res.Success
}

func installLog(job string) string {
	return path.Join(installDir, ".adbr-install-"+job+".log")
}

func checkTarget(serial, devicePath string) (string, error) {
	if !serialRe.MatchString(serial) || !strings.HasPrefix(devicePath, "/") ||
		strings.ContainsAny(devicePath, "\x00\n\r") {
		return "", ErrFileBadRequest
	}
	clean := path.Clean(devicePath)
	for _, root := range deviceRoots {
		if clean == root || strings.HasPrefix(clean, root+"/") {
			return clean, nil
		}
	}
	return "", ErrFileBadRequest
}

func validFileName(name string) bool {
	return name != "" && name != "." && name != ".." && len(name) <= 255 &&
		!strings.ContainsAny(name, "/\\\x00\n\r")
}

func shellQuote(value string) string {
	return "'" + strings.ReplaceAll(value, "'", `'\''`) + "'"
}

// parseListing reads `ls -lAL`. Lines it cannot read, like a total or a permission error,
// are skipped: one unreadable entry should not hide the rest.
func parseListing(dir, stdout string) []DeviceFile {
	var files []DeviceFile
	for _, line := range strings.Split(stdout, "\n") {
		m := lsLineRe.FindStringSubmatch(strings.TrimRight(line, "\r"))
		if m == nil {
			continue
		}
		size, _ := strconv.ParseInt(m[2], 10, 64)
		files = append(files, DeviceFile{
			Name:     m[4],
			Path:     path.Join(dir, m[4]),
			IsDir:    m[1] == "d",
			Size:     size,
			Modified: m[3],
		})
	}
	sort.SliceStable(files, func(i, j int) bool {
		if files[i].IsDir != files[j].IsDir {
			return files[i].IsDir
		}
		return strings.ToLower(files[i].Name) < strings.ToLower(files[j].Name)
	})
	return files
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if strings.TrimSpace(value) != "" {
			return strings.TrimSpace(value)
		}
	}
	return ""
}
