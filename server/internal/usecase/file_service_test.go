package usecase

import (
	"context"
	"errors"
	"os"
	"regexp"
	"strings"
	"testing"

	"app.duinch/adb-device-manager/internal/domain"
)

type fakeFileAdb struct {
	shell     func(command string) domain.CommandResult
	commands  []string
	pushErr   error
	pushedTo  string
	pushBytes string
}

func (f *fakeFileAdb) RunShell(_ context.Context, _, command string) (domain.CommandResult, error) {
	f.commands = append(f.commands, command)
	if f.shell != nil {
		return f.shell(command), nil
	}
	return domain.CommandResult{Success: true}, nil
}

func (f *fakeFileAdb) Push(_ context.Context, _, local, remote string) (domain.CommandResult, error) {
	raw, _ := os.ReadFile(local)
	f.pushBytes, f.pushedTo = string(raw), remote
	return domain.CommandResult{Success: f.pushErr == nil}, f.pushErr
}

func (f *fakeFileAdb) Pull(_ context.Context, _, _, local string) (domain.CommandResult, error) {
	return domain.CommandResult{Success: true}, os.WriteFile(local, []byte("data"), 0o600)
}

func (f *fakeFileAdb) ran(part string) bool {
	return strings.Contains(strings.Join(f.commands, "\n"), part)
}

func noFile(command string) domain.CommandResult {
	return domain.CommandResult{Success: !strings.HasPrefix(command, "test -e")}
}

func TestParseListing(t *testing.T) {
	files := parseListing("/sdcard", strings.Join([]string{
		"total 12",
		"-rw-rw---- 1 u0_a1 media_rw 2048 2026-11-04 08:28 báo cáo 'cuối' .pdf",
		"drwxrws--x 2 u0_a1 media_rw 3452 2026-11-03 10:00 Download",
		"ls: /sdcard/x: Permission denied",
		"drwxrws--- 2 u0_a1 media_rw 3452 2026-11-03 10:00 alpha",
	}, "\n"))
	if len(files) != 3 {
		t.Fatalf("got %+v", files)
	}
	if files[0].Name != "alpha" || files[1].Name != "Download" || !files[1].IsDir {
		t.Fatalf("directories should come first, by name: %+v", files)
	}
	if files[2].Name != "báo cáo 'cuối' .pdf" || files[2].Size != 2048 || files[2].Path != "/sdcard/báo cáo 'cuối' .pdf" {
		t.Fatalf("file row: %+v", files[2])
	}
}

func TestPathsOutsideSharedStorageAreRefused(t *testing.T) {
	svc := NewFileService(&fakeFileAdb{})
	for _, bad := range []string{"/data/data/x", "/sdcard/../data/x", "/sdcardx", "sdcard", "/", "/sdcard/a\nb"} {
		if _, err := svc.List(context.Background(), "S1", bad); !errors.Is(err, ErrFileBadRequest) {
			t.Fatalf("%q: err=%v", bad, err)
		}
	}
	if _, err := svc.List(context.Background(), "-s", "/sdcard"); !errors.Is(err, ErrFileBadRequest) {
		t.Fatalf("a serial must not read as an adb flag: %v", err)
	}
}

func TestUpload(t *testing.T) {
	t.Run("sends the content to dir/name", func(t *testing.T) {
		adb := &fakeFileAdb{shell: noFile}
		remote, err := NewFileService(adb).Upload(context.Background(), "S1", "/sdcard/Download", "a b.txt", strings.NewReader("hi"), false)
		if err != nil || remote != "/sdcard/Download/a b.txt" || adb.pushBytes != "hi" || adb.pushedTo != remote {
			t.Fatalf("remote=%q err=%v adb=%+v", remote, err, adb)
		}
	})
	t.Run("refuses names that leave the directory", func(t *testing.T) {
		for _, name := range []string{"../x", "a/b", "..", ""} {
			_, err := NewFileService(&fakeFileAdb{}).Upload(context.Background(), "S1", "/sdcard", name, strings.NewReader(""), false)
			if !errors.Is(err, ErrFileBadRequest) {
				t.Fatalf("%q: %v", name, err)
			}
		}
	})
	t.Run("asks before replacing", func(t *testing.T) {
		adb := &fakeFileAdb{}
		_, err := NewFileService(adb).Upload(context.Background(), "S1", "/sdcard", "a", strings.NewReader("x"), false)
		if !errors.Is(err, ErrFileExists) || adb.pushedTo != "" {
			t.Fatalf("err=%v pushed=%q", err, adb.pushedTo)
		}
	})
	t.Run("a failed push removes only a file it created", func(t *testing.T) {
		adb := &fakeFileAdb{shell: noFile, pushErr: errors.New("link lost")}
		if _, err := NewFileService(adb).Upload(context.Background(), "S1", "/sdcard", "new", strings.NewReader("x"), false); err == nil {
			t.Fatal("want error")
		}
		if !adb.ran("rm -f -- '/sdcard/new'") {
			t.Fatalf("partial file left: %v", adb.commands)
		}
		adb = &fakeFileAdb{pushErr: errors.New("link lost")}
		if _, err := NewFileService(adb).Upload(context.Background(), "S1", "/sdcard", "old", strings.NewReader("x"), true); err == nil {
			t.Fatal("want error")
		}
		if adb.ran("rm -f") {
			t.Fatalf("removed a file it did not create: %v", adb.commands)
		}
	})
}

func TestShellQuoteKeepsAQuoteInsideOneArgument(t *testing.T) {
	adb := &fakeFileAdb{shell: func(string) domain.CommandResult { return domain.CommandResult{Success: true} }}
	if _, err := NewFileService(adb).List(context.Background(), "S1", "/sdcard/it's; rm -rf x"); err != nil {
		t.Fatal(err)
	}
	if !adb.ran(`'/sdcard/it'\''s; rm -rf x/'`) {
		t.Fatalf("not quoted: %v", adb.commands)
	}
}

func TestStartInstall(t *testing.T) {
	adb := &fakeFileAdb{}
	job, err := NewFileService(adb).StartInstall(context.Background(), "S1", "/sdcard/Download/a.apk")
	if err != nil {
		t.Fatal(err)
	}
	if !regexp.MustCompile(`^[0-9a-f]{16}$`).MatchString(job) {
		t.Fatalf("job %q", job)
	}
	for _, part := range []string{"setsid nohup sh -c", "pm install -r -t", "am start -n app.duinch.adbagent/.MainActivity", job} {
		if !adb.ran(part) {
			t.Fatalf("missing %q in %v", part, adb.commands)
		}
	}
	if _, err := NewFileService(adb).StartInstall(context.Background(), "S1", "/sdcard/a.txt"); !errors.Is(err, ErrFileBadRequest) {
		t.Fatalf("non-APK: %v", err)
	}
}

func TestInstallResult(t *testing.T) {
	const job = "0123456789abcdef"
	cases := []struct {
		name  string
		res   domain.CommandResult
		state string
	}{
		{"success", domain.CommandResult{Success: true, Stdout: "Success\nEXIT:0\nDONE"}, "success"},
		{"refused", domain.CommandResult{Success: true, Stdout: "Failure [INSTALL_FAILED]\nEXIT:1\nDONE"}, "failure"},
		{"still installing", domain.CommandResult{Success: true, Stdout: "Performing"}, "running"},
		{"device away while the app restarts", domain.CommandResult{Stderr: "device not found"}, "running"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			adb := &fakeFileAdb{shell: func(string) domain.CommandResult { return tc.res }}
			status, err := NewFileService(adb).InstallResult(context.Background(), "S1", job)
			if err != nil || status.State != tc.state {
				t.Fatalf("status=%+v err=%v", status, err)
			}
		})
	}
	if _, err := NewFileService(&fakeFileAdb{}).InstallResult(context.Background(), "S1", "x; rm -rf /"); !errors.Is(err, ErrFileBadRequest) {
		t.Fatalf("bad job: %v", err)
	}
}
