package ui

import "embed"

//go:embed index.html
var IndexHTML []byte

//go:embed *
var DistFS embed.FS
