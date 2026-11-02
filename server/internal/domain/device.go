package domain

type BatteryInfo struct {
	Level    int  `json:"level"`
	Charging bool `json:"charging"`
}

type DeviceSpecs struct {
	Brand          *string      `json:"brand"`
	AndroidVersion *string      `json:"android_version"`
	SDK            *string      `json:"sdk"`
	Battery        *BatteryInfo `json:"battery"`
	WifiIP         *string      `json:"wifi_ip"`
}

type Device struct {
	Serial            string       `json:"serial"`
	State             string       `json:"state"`
	Product           string       `json:"product"`
	Model             string       `json:"model"`
	DeviceCode        string       `json:"device"`
	TransportID       string       `json:"transport_id"`
	IsRemote          bool         `json:"is_remote"`
	IsTunnel          bool         `json:"is_tunnel"`
	TunnelAlias       *string      `json:"tunnel_alias"`
	TunnelLocalPort   *int         `json:"tunnel_local_port"`
	TunnelConnectedAt *int64       `json:"tunnel_connected_at,omitempty"`
	TunnelBytes       *int64       `json:"tunnel_bytes,omitempty"`
	Battery           *BatteryInfo `json:"battery"`
	AndroidVersion    *string      `json:"android_version"`
	SDK               *string      `json:"sdk,omitempty"`
	Screen            *string               `json:"screen"`
	WifiIP            *string               `json:"wifi_ip"`
	Brand             *string               `json:"brand"`
	RecentCommands    []DeviceCommandRecord `json:"recent_commands,omitempty"`
}

type CommandResult struct {
	Success    bool   `json:"success"`
	Stdout     string `json:"stdout"`
	Stderr     string `json:"stderr"`
	ReturnCode int    `json:"returncode"`
}

type AppVersionManifest struct {
	VersionCode int    `json:"versionCode"`
	VersionName string `json:"versionName"`
	DownloadURL string `json:"downloadUrl"`
	SHA256      string `json:"sha256,omitempty"`
	Changelog   string `json:"changelog"`
}
