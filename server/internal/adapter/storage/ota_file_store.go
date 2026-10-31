package storage

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"io"
	"os"
	"path/filepath"
	"sync"
	"time"

	"app.duinch/adb-device-manager/internal/domain"
)

type OtaFileStore struct {
	downloadsDir string
	mu           sync.Mutex
	cachedMtime  time.Time
	cachedHash   string
}

func NewOtaFileStore(dataDir string) *OtaFileStore {
	return &OtaFileStore{
		downloadsDir: filepath.Join(dataDir, "downloads"),
	}
}

func (s *OtaFileStore) LoadManifest() (domain.AppVersionManifest, error) {
	apkPath := filepath.Join(s.downloadsDir, "adb-agent.apk")
	info, err := os.Stat(apkPath)
	if err != nil || info.IsDir() {
		return domain.AppVersionManifest{}, domain.NewNotFoundError("no APK published yet")
	}

	meta := domain.AppVersionManifest{
		VersionCode: 6,
		VersionName: "0.5.0",
		DownloadURL: "/download/adb-agent.apk",
		Changelog:   "Tích hợp tự động kiểm tra và cập nhật OTA ngay trong ứng dụng.",
	}

	metaPath := filepath.Join(s.downloadsDir, "version.json")
	if raw, err := os.ReadFile(metaPath); err == nil {
		_ = json.Unmarshal(raw, &meta)
	}

	if meta.SHA256 == "" {
		if sum, err := s.computeApkHashCached(apkPath, info.ModTime()); err == nil {
			meta.SHA256 = sum
		}
	}
	return meta, nil
}

func (s *OtaFileStore) OpenApk() (io.ReadCloser, int64, domain.AppVersionManifest, error) {
	meta, err := s.LoadManifest()
	if err != nil {
		return nil, 0, domain.AppVersionManifest{}, err
	}
	apkPath := filepath.Join(s.downloadsDir, "adb-agent.apk")
	f, err := os.Open(apkPath)
	if err != nil {
		return nil, 0, domain.AppVersionManifest{}, domain.NewNotFoundError("APK file not found")
	}
	st, err := f.Stat()
	if err != nil {
		_ = f.Close()
		return nil, 0, domain.AppVersionManifest{}, err
	}
	return f, st.Size(), meta, nil
}

func (s *OtaFileStore) computeApkHashCached(apkPath string, mtime time.Time) (string, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.cachedHash != "" && s.cachedMtime.Equal(mtime) {
		return s.cachedHash, nil
	}
	f, err := os.Open(apkPath)
	if err != nil {
		return "", err
	}
	defer func() { _ = f.Close() }()

	h := sha256.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", err
	}
	sum := hex.EncodeToString(h.Sum(nil))
	s.cachedMtime = mtime
	s.cachedHash = sum
	return sum, nil
}
