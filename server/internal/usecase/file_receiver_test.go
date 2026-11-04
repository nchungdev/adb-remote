package usecase

import (
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestFileReceiver_SavesWithoutReplacing(t *testing.T) {
	dir := t.TempDir()
	r := NewFileReceiver(filepath.Join(dir, "nested"))
	first, err := r.Save("báo cáo.pdf", strings.NewReader("one"))
	if err != nil || filepath.Base(first) != "báo cáo.pdf" {
		t.Fatalf("first=%q err=%v", first, err)
	}
	second, err := r.Save("báo cáo.pdf", strings.NewReader("two"))
	if err != nil || filepath.Base(second) != "báo cáo (1).pdf" {
		t.Fatalf("second=%q err=%v", second, err)
	}
	if raw, _ := os.ReadFile(first); string(raw) != "one" {
		t.Fatalf("first file changed: %q", raw)
	}
}

func TestFileReceiver_NameCannotLeaveTheFolder(t *testing.T) {
	cases := map[string]string{
		"../../etc/passwd": "passwd",
		`..\..\evil.exe`:   "evil.exe",
		"/abs/path.txt":    "path.txt",
		".zshrc":           "zshrc",
		"..":               "file",
		"":                 "file",
		"a\nb.txt":         "ab.txt",
	}
	for input, want := range cases {
		if got := safeReceivedName(input); got != want {
			t.Errorf("%q: got %q, want %q", input, got, want)
		}
	}
}

type brokenReader struct{}

func (brokenReader) Read([]byte) (int, error) { return 0, errors.New("link lost") }

func TestFileReceiver_FailedTransferLeavesNothing(t *testing.T) {
	dir := t.TempDir()
	if _, err := NewFileReceiver(dir).Save("a.bin", brokenReader{}); err == nil {
		t.Fatal("want error")
	}
	if entries, _ := os.ReadDir(dir); len(entries) != 0 {
		t.Fatalf("left behind: %v", entries)
	}
}
