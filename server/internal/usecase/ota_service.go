package usecase

import (
	"io"

	"app.duinch/adb-device-manager/internal/domain"
)

type OtaService struct {
	repo OtaRepository
}

func NewOtaService(repo OtaRepository) *OtaService {
	return &OtaService{repo: repo}
}

func (s *OtaService) GetManifest() (domain.AppVersionManifest, error) {
	return s.repo.LoadManifest()
}

func (s *OtaService) OpenApk() (io.ReadCloser, int64, domain.AppVersionManifest, error) {
	return s.repo.OpenApk()
}
