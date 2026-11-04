package httpctrl

import (
	"errors"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"time"

	"app.duinch/adb-device-manager/internal/usecase"
	"github.com/nchungdev/remote-tunnel/go/pairing"
)

const maxUploadBytes = 8 << 30

// rejectCrossSiteWrites stops a web page in the host's browser from driving this server:
// such a request does come from loopback, so only its Origin gives it away. Clients outside
// a browser, like the phone app, send no Origin.
func rejectCrossSiteWrites(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet && r.Method != http.MethodHead {
			if origin := r.Header.Get("Origin"); origin != "" {
				parsed, err := url.Parse(origin)
				if err != nil || parsed.Host != r.Host {
					http.Error(w, "Forbidden: cross-site request", http.StatusForbidden)
					return
				}
			}
		}
		next.ServeHTTP(w, r)
	})
}

// requireHostDashboard keeps files to the browser on this machine, like the shell. The Host
// check stops a web page from reaching them through a domain that resolves to loopback.
func (s *Server) requireHostDashboard(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		host := r.Host
		if h, _, err := net.SplitHostPort(r.Host); err == nil {
			host = h
		}
		isLocalName := host == "localhost" || net.ParseIP(host).IsLoopback()
		if !pairing.IsLoopbackRequest(r) || !isLocalName {
			http.Error(w, "Quản lý file chỉ mở được trên chính máy chạy server", http.StatusForbidden)
			return
		}
		next(w, r)
	}
}

func (s *Server) handleListFiles(w http.ResponseWriter, r *http.Request) {
	query := r.URL.Query()
	files, err := s.files.List(r.Context(), query.Get("serial"), query.Get("path"))
	if err != nil {
		writeFileError(w, err)
		return
	}
	if files == nil {
		files = []usecase.DeviceFile{}
	}
	writeJSON(w, http.StatusOK, map[string]any{"files": files})
}

func (s *Server) handleUploadFile(w http.ResponseWriter, r *http.Request) {
	disableDeadlines(w)
	query := r.URL.Query()
	body := http.MaxBytesReader(w, r.Body, maxUploadBytes)
	remote, err := s.files.Upload(r.Context(), query.Get("serial"), query.Get("dir"), query.Get("name"), body, query.Get("overwrite") == "1")
	if err != nil {
		writeFileError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"path": remote})
}

func (s *Server) handleDownloadFile(w http.ResponseWriter, r *http.Request) {
	disableDeadlines(w)
	query := r.URL.Query()
	file, err := s.files.Download(r.Context(), query.Get("serial"), query.Get("path"))
	if err != nil {
		writeFileError(w, err)
		return
	}
	defer file.Remove()
	local, err := os.Open(file.LocalPath)
	if err != nil {
		writeFileError(w, err)
		return
	}
	defer func() { _ = local.Close() }()
	w.Header().Set("Content-Type", "application/octet-stream")
	w.Header().Set("Content-Disposition", "attachment; filename*=UTF-8''"+url.PathEscape(file.Name))
	w.Header().Set("Content-Length", strconv.FormatInt(file.Size, 10))
	_, _ = io.Copy(w, local)
}

func (s *Server) handleInstallApk(w http.ResponseWriter, r *http.Request) {
	query := r.URL.Query()
	job, err := s.files.StartInstall(r.Context(), query.Get("serial"), query.Get("path"))
	if err != nil {
		writeFileError(w, err)
		return
	}
	writeJSON(w, http.StatusAccepted, map[string]any{"job": job})
}

func (s *Server) handleInstallResult(w http.ResponseWriter, r *http.Request) {
	query := r.URL.Query()
	status, err := s.files.InstallResult(r.Context(), query.Get("serial"), query.Get("job"))
	if err != nil {
		writeFileError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, status)
}

// disableDeadlines lets a large file over a slow tunnel outlast the server's request limits.
func disableDeadlines(w http.ResponseWriter) {
	controller := http.NewResponseController(w)
	_ = controller.SetReadDeadline(time.Time{})
	_ = controller.SetWriteDeadline(time.Time{})
}

func writeFileError(w http.ResponseWriter, err error) {
	var tooLarge *http.MaxBytesError
	switch {
	case errors.Is(err, usecase.ErrFileExists):
		http.Error(w, "File đã tồn tại", http.StatusConflict)
	case errors.Is(err, usecase.ErrFileBadRequest):
		http.Error(w, "Thiết bị, đường dẫn hoặc tên file không hợp lệ", http.StatusBadRequest)
	case errors.As(err, &tooLarge):
		http.Error(w, "File quá lớn", http.StatusRequestEntityTooLarge)
	default:
		http.Error(w, err.Error(), http.StatusBadGateway)
	}
}
