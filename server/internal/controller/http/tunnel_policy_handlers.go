package httpctrl

import (
	"encoding/json"
	"net/http"
	"strings"

	"app.duinch/adb-device-manager/internal/adapter/tunnel"
)

type setPolicyRequest struct {
	Policy string `json:"policy"`
}

func (s *Server) handleGetTunnelPolicy(w http.ResponseWriter, _ *http.Request) {
	if s.policyMgr == nil {
		writeJSON(w, http.StatusOK, map[string]any{
			"policy": tunnel.PolicyAuto.String(),
		})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"policy": s.policyMgr.Policy().String(),
	})
}

func (s *Server) handleSetTunnelPolicy(w http.ResponseWriter, r *http.Request) {
	if s.policyMgr == nil {
		http.Error(w, "policy manager not available", http.StatusNotImplemented)
		return
	}

	rawPolicy := r.URL.Query().Get("policy")
	if r.Header.Get("Content-Type") == "application/json" || r.ContentLength > 0 {
		var req setPolicyRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err == nil && req.Policy != "" {
			rawPolicy = req.Policy
		}
	}

	policyStr := strings.ToLower(strings.TrimSpace(rawPolicy))
	switch policyStr {
	case "auto", "lan", "remote":
		// valid
	default:
		writeJSON(w, http.StatusBadRequest, map[string]any{
			"success": false,
			"error":   "invalid policy: must be auto, lan, or remote",
		})
		return
	}

	policy := tunnel.ParsePathPolicy(policyStr)
	s.policyMgr.SetPolicy(policy)
	writeJSON(w, http.StatusOK, map[string]any{
		"success": true,
		"policy":  policy.String(),
	})
}
