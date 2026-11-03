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
}

func newDeviceSlot(alias string, ln net.Listener, mappedPort int) *deviceSlot {
	slot := &deviceSlot{
		alias:      alias,
		mappedPort: mappedPort,
		listener:   ln,
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

// add registers a session and reports whether it changed the active path.
func (d *deviceSlot) add(sess *Session) bool {
	d.mu.Lock()
	defer d.mu.Unlock()
	d.sessions = append(d.sessions, sess)
	return d.electLocked()
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

// electLocked prefers a LAN path, then the newest session: a retry on the same
// path must win over its predecessor, which may be dead but not yet detected.
func (d *deviceSlot) electLocked() bool {
	var best *Session
	for _, s := range d.sessions {
		if s.Alive() && (best == nil || s.path >= best.path) {
			best = s
		}
	}
	changed := best != d.active
	d.active = best
	return changed
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
