package adb

import (
	"context"
	"time"

	"app.duinch/adb-device-manager/internal/domain"
)

// A slow tunnel can take a long time over a large file.
const fileTransferTimeout = 30 * time.Minute

func (r *CLIRunner) Push(ctx context.Context, serial, localPath, remotePath string) (domain.CommandResult, error) {
	return r.execText(ctx, fileTransferTimeout, "-s", serial, "push", localPath, remotePath)
}

func (r *CLIRunner) Pull(ctx context.Context, serial, remotePath, localPath string) (domain.CommandResult, error) {
	return r.execText(ctx, fileTransferTimeout, "-s", serial, "pull", remotePath, localPath)
}
