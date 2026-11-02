package domain

type DeviceCommandRecord struct {
	ID         string `json:"id"`
	Device     string `json:"device"`
	Command    string `json:"command"`
	Source     string `json:"source"`
	Status     string `json:"status"`
	StartTime  int64  `json:"start_time"`
	DurationMs int64  `json:"duration_ms"`
	Output     string `json:"output,omitempty"`
	Error      string `json:"error,omitempty"`
}
