package tunnel

import (
	"net"
	"sync"
	"time"
)

// deviceSlot owns one mapped loopback port for a device. A device may reach the
// server over several paths at once; all stay up and ADB uses the best alive one.
type deviceSlot struct {
	alias      string
	mappedPort int
	listener   net.Listener
	mu         sync.RWMutex
	sessions   []*Session
	active     *Session
	policy     PathPolicy
}

func newDeviceSlot(alias string, ln net.Listener, mappedPort int, policy PathPolicy) *deviceSlot {
	slot := &deviceSlot{
		alias:      alias,
		mappedPort: mappedPort,
		listener:   ln,
		policy:     policy,
	}
	go slot.acceptLoop()
	return slot
}

func (d *deviceSlot) acceptLoop() {
	for {
		conn, err := d.listener.Accept()
		if err != nil {
			if errorsIsClosed(err) {
				return
			}
			time.Sleep(150 * time.Millisecond)
			continue
		}
		if sess := d.activeSession(); sess != nil && sess.Alive() {
			sess.AttachLocalClient(conn)
		} else {
			_ = conn.Close()
		}
	}
}

func (d *deviceSlot) activeSession() *Session {
	d.mu.RLock()
	defer d.mu.RUnlock()
	return d.active
}

// add registers a session and reports whether it changed the active path, plus any
// sessions it replaced: a client slot only ever has one, so an older one with the same
// connection ID is dead even if the server has not noticed yet.
func (d *deviceSlot) add(sess *Session) (activeChanged bool, replaced []*Session) {
	d.mu.Lock()
	defer d.mu.Unlock()
	kept := d.sessions[:0]
	for _, s := range d.sessions {
		if sess.connectionID != "" && s.connectionID == sess.connectionID {
			replaced = append(replaced, s)
		} else {
			kept = append(kept, s)
		}
	}
	d.sessions = append(kept, sess)
	return d.electLocked(), replaced
}

// remove drops a session and reports whether it changed the active path.
func (d *deviceSlot) remove(sess *Session) bool {
	d.mu.Lock()
	defer d.mu.Unlock()
	for i, s := range d.sessions {
		if s == sess {
			d.sessions = append(d.sessions[:i], d.sessions[i+1:]...)
			break
		}
	}
	return d.electLocked()
}

// electLocked chooses the active session according to the slot policy.
// In PolicyLAN, only LAN sessions can be active.
// In PolicyRemote, only Remote sessions can be active.
// In PolicyAuto, LAN is preferred, then newest session.
func (d *deviceSlot) electLocked() bool {
	var best *Session
	for _, s := range d.sessions {
		if !s.Alive() {
			continue
		}
		switch d.policy {
		case PolicyLAN:
			if s.path == PathLAN && (best == nil || s.ConnectedAt >= best.ConnectedAt) {
				best = s
			}
		case PolicyRemote:
			if s.path == PathRemote && (best == nil || s.ConnectedAt >= best.ConnectedAt) {
				best = s
			}
		default:
			if best == nil || s.path >= best.path {
				best = s
			}
		}
	}
	changed := best != d.active
	d.active = best
	return changed
}

func (d *deviceSlot) setPolicy(p PathPolicy) bool {
	d.mu.Lock()
	defer d.mu.Unlock()
	if d.policy == p {
		return false
	}
	d.policy = p
	return d.electLocked()
}

func (d *deviceSlot) currentPolicy() PathPolicy {
	d.mu.RLock()
	defer d.mu.RUnlock()
	return d.policy
}

func (d *deviceSlot) drain() []*Session {
	d.mu.Lock()
	defer d.mu.Unlock()
	out := d.sessions
	d.sessions = nil
	d.active = nil
	return out
}

// publishRoles tells each session whose role changed. Writes run in goroutines
// because a dead path's write may never return.
func (d *deviceSlot) publishRoles() {
	d.mu.RLock()
	active := d.active
	sessions := append([]*Session(nil), d.sessions...)
	d.mu.RUnlock()
	for _, s := range sessions {
		standby := s != active
		if s.standby.Swap(standby) != standby {
			go s.sendRole(standby)
		}
	}
}
