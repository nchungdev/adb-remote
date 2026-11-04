package usecase

import (
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

// FileReceiver keeps files a device sends to this machine, all in one folder. It never
// replaces a file, so a device can only ever add to that folder.
type FileReceiver struct {
	dir string
}

func NewFileReceiver(dir string) *FileReceiver {
	return &FileReceiver{dir: dir}
}

// Save returns the path it chose: name, or name with a number when name is taken.
func (r *FileReceiver) Save(name string, content io.Reader) (string, error) {
	if err := os.MkdirAll(r.dir, 0o755); err != nil {
		return "", err
	}
	file, err := r.createUnique(safeReceivedName(name))
	if err != nil {
		return "", err
	}
	_, err = io.Copy(file, content)
	if closeErr := file.Close(); err == nil {
		err = closeErr
	}
	if err != nil {
		_ = os.Remove(file.Name())
		return "", err
	}
	return file.Name(), nil
}

func (r *FileReceiver) createUnique(name string) (*os.File, error) {
	ext := filepath.Ext(name)
	stem := strings.TrimSuffix(name, ext)
	candidate := name
	for attempt := 1; attempt < 10000; attempt++ {
		file, err := os.OpenFile(filepath.Join(r.dir, candidate), os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0o644)
		if err == nil {
			return file, nil
		}
		if !errors.Is(err, os.ErrExist) {
			return nil, err
		}
		candidate = fmt.Sprintf("%s (%d)%s", stem, attempt, ext)
	}
	return nil, errors.New("too many files with this name")
}

// safeReceivedName keeps only a plain file name: the name comes from another machine.
func safeReceivedName(name string) string {
	name = strings.ReplaceAll(name, "\\", "/")
	name = name[strings.LastIndex(name, "/")+1:]
	name = strings.Map(func(c rune) rune {
		if c < 32 || c == 127 {
			return -1
		}
		return c
	}, name)
	name = strings.TrimLeft(strings.TrimSpace(name), ".")
	if name == "" {
		return "file"
	}
	if len(name) > 200 {
		ext := filepath.Ext(name)
		if len(ext) > 20 {
			ext = ""
		}
		name = name[:200-len(ext)] + ext
	}
	return name
}
