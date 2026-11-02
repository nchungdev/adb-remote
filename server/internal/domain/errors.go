package domain

import (
	"errors"
	"fmt"
)

var (
	ErrInvalidInput = errors.New("invalid input")
	ErrNotFound     = errors.New("resource not found")
	ErrCommandFail  = errors.New("command execution failed")
)

type DomainError struct {
	Kind    error
	Message string
}

func (e *DomainError) Error() string {
	if e.Message == "" {
		return e.Kind.Error()
	}
	return e.Message
}

func (e *DomainError) Unwrap() error {
	return e.Kind
}

func NewValidationError(format string, args ...any) error {
	return &DomainError{
		Kind:    ErrInvalidInput,
		Message: fmt.Sprintf(format, args...),
	}
}

func NewNotFoundError(format string, args ...any) error {
	return &DomainError{
		Kind:    ErrNotFound,
		Message: fmt.Sprintf(format, args...),
	}
}
