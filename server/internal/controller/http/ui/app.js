let currentSerial = null;
let allDevicesCache = [];
let allHistoryCache = [];
let activeFilter = 'all';
let currentMainTab = 'devices';

function escapeHtml(str) {
  if (typeof str !== 'string') return String(str || '');
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function flashCopied(btn, label = '✓ Đã copy!') {
  if (!btn) return;
  const orig = btn.textContent;
  btn.textContent = label;
  btn.style.color = '#34d399';
  setTimeout(() => { btn.textContent = orig; btn.style.color = ''; }, 1600);
}

async function copyText(text, btn) {
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(text);
    } else {
      const ta = document.createElement('textarea');
      ta.value = text;
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
    }
    flashCopied(btn);
  } catch (err) { console.error('Copy failed:', err); }
}

function formatDurationSec(sec) {
  const s = Math.max(0, Math.floor(sec));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const rem = s % 60;
  const pad = n => String(n).padStart(2, '0');
  return h > 0 ? `${h}h ${pad(m)}m ${pad(rem)}s` : `${m}m ${pad(rem)}s`;
}

function formatBytesHuman(b) {
  const n = Number(b || 0);
  if (n < 1024) return n + ' B';
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB';
  if (n < 1024 * 1024 * 1024) return (n / (1024 * 1024)).toFixed(1) + ' MB';
  return (n / (1024 * 1024 * 1024)).toFixed(2) + ' GB';
}

function formatEpochTime(sec) {
  if (!sec) return '-';
  return new Date(sec * 1000).toLocaleString('vi-VN', { hour12: false });
}

function switchMainTab(tab) {
  currentMainTab = tab;
  document.getElementById('tabBtnDevices').classList.toggle('active', tab === 'devices');
  document.getElementById('tabBtnHistory').classList.toggle('active', tab === 'history');
  document.getElementById('tabContentDevices').classList.toggle('active', tab === 'devices');
  document.getElementById('tabContentHistory').classList.toggle('active', tab === 'history');
  const titles = { devices: 'Thiết Bị & Giám Sát', history: 'Lịch Sử Phiên Kết Nối' };
  document.getElementById('stageTitle').textContent = titles[tab] || 'Workspace';
  if (tab === 'history') renderGroupedHistory();
}

function tickLiveTimers() {
  const nowSec = Math.floor(Date.now() / 1000);
  document.querySelectorAll('[data-connected-at]').forEach(el => {
    const s = Number(el.dataset.connectedAt || 0);
    if (s > 0) el.textContent = formatDurationSec(nowSec - s);
  });
}
setInterval(tickLiveTimers, 1000);

function setFilter(type, btn) {
  activeFilter = type;
  document.querySelectorAll('.filter-chips .chip').forEach(b => b.classList.remove('active'));
  if (btn) btn.classList.add('active');
  filterDeviceCards();
}

function clearSearch() {
  const inp = document.getElementById('searchInput');
  inp.value = '';
  document.getElementById('searchClearBtn').style.display = 'none';
  filterDeviceCards();
}

function filterDeviceCards() {
  const query = (document.getElementById('searchInput').value || '').toLowerCase().trim();
  document.getElementById('searchClearBtn').style.display = query ? 'block' : 'none';

  const filtered = allDevicesCache.filter(dev => {
    const isOnline = dev.state === 'device';
    if (activeFilter === 'online' && !isOnline) return false;
    if (activeFilter === 'offline' && isOnline) return false;
    if (activeFilter === 'tunnel' && !dev.is_tunnel) return false;
    if (activeFilter === 'wifi' && (dev.is_tunnel || !dev.is_remote)) return false;
    if (activeFilter === 'usb' && (dev.is_tunnel || dev.is_remote)) return false;

    if (!query) return true;
    const target = [dev.serial, dev.model, dev.brand, dev.tunnel_alias, dev.wifi_ip].filter(Boolean).join(' ').toLowerCase();
    return target.includes(query);
  });
  renderDevices(filtered);
}

function renderDevices(devices) {
  const grid = document.getElementById('deviceGrid');
  if (!devices || devices.length === 0) {
    grid.innerHTML = `
      <div class="empty-state">
        <div class="empty-icon"><svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="8" y1="12" x2="16" y2="12"/></svg></div>
        <div class="empty-title">Không tìm thấy thiết bị phù hợp</div>
        <p class="empty-desc">Thử đổi bộ lọc, cắm cáp USB hoặc mở app ADB Agent để bắt đầu.</p>
      </div>`;
    return;
  }

  grid.innerHTML = devices.map(dev => {
    const isOnline = dev.state === 'device';
    const isTunnel = Boolean(dev.is_tunnel || dev.serial.startsWith('127.0.0.1:') || dev.serial.startsWith('localhost:'));
    const badgeCls = isOnline ? 'tag-online' : (dev.state === 'unauthorized' ? 'tag-unauth' : 'tag-offline');
    const badgeTxt = isOnline ? 'ONLINE' : (dev.state === 'tunnel_only' ? 'P2P NỐI' : dev.state.toUpperCase());

    const typeTag = isTunnel
      ? '<span class="tag tag-p2p">⚡ P2P WEBRTC</span>'
      : (dev.is_remote ? '<span class="tag tag-wifi">WI-FI LOCAL</span>' : '<span class="tag tag-usb">CÁP USB</span>');

    const name = (dev.brand ? dev.brand.toUpperCase() + ' ' : '') + (dev.model || dev.device || dev.tunnel_alias || 'Android Device');
    const ipText = dev.wifi_ip || (isTunnel ? (dev.tunnel_local_port ? `:${dev.tunnel_local_port}` : 'P2P') : (dev.is_remote ? dev.serial.split(':')[0] : 'USB'));
    const batText = dev.battery ? `${dev.battery.level}% ${dev.battery.charging ? '⚡' : ''}` : 'N/A';
    const osText = dev.android_version ? `Android ${dev.android_version}` : 'N/A';
    const serialDisplay = isTunnel && dev.tunnel_alias ? `${dev.serial} · ${dev.tunnel_alias}` : dev.serial;

    return `
      <div class="dev-card ${isOnline ? '' : 'offline'}">
        <div class="card-top">
          <div class="dev-info">
            <div class="dev-icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="${isOnline ? '#38bdf8' : '#94a3b8'}" stroke-width="2"><rect x="5" y="2" width="14" height="20" rx="2" ry="2"/></svg>
            </div>
            <div style="min-width:0;">
              <div class="dev-title">${escapeHtml(name)}</div>
              <div class="dev-sub-row">
                <span class="dev-serial-text">${escapeHtml(serialDisplay)}</span>
                <button class="copy-chip" onclick="copyText('${escapeHtml(dev.serial)}', this)">Copy</button>
              </div>
            </div>
          </div>
          <div class="tag-stack">
            <span class="tag ${badgeCls}">${badgeTxt}</span>
            ${typeTag}
          </div>
        </div>

        <div class="bento-specs">
          <div class="bento-col"><span class="bento-lbl">HĐH</span><span class="bento-val">${osText}</span></div>
          <div class="bento-col"><span class="bento-lbl">PIN</span><span class="bento-val">${batText}</span></div>
          <div class="bento-col"><span class="bento-lbl">IP/PORT</span><span class="bento-val font-mono">${ipText}</span></div>
          <div class="bento-col">
            <span class="bento-lbl">${dev.is_tunnel ? 'UPTIME' : 'MODEL'}</span>
            <span class="bento-val font-mono" ${dev.is_tunnel && dev.tunnel_connected_at ? `data-connected-at="${dev.tunnel_connected_at}" style="color:#34d399;"` : ''}>
              ${dev.is_tunnel && dev.tunnel_connected_at ? formatDurationSec(Math.floor(Date.now() / 1000) - dev.tunnel_connected_at) : escapeHtml(dev.model || '-')}
            </span>
          </div>
        </div>

        <div class="card-actions">
          <button class="btn btn-sm btn-primary" onclick="openScreenModal('${dev.serial}', '${escapeHtml(name)}')">Màn hình</button>
          <button class="btn btn-sm" onclick="openLogcatModal('${dev.serial}', '${escapeHtml(name)}')">Logcat</button>
          <button class="btn btn-sm" onclick="openShellModal('${dev.serial}', '${escapeHtml(name)}')">Shell</button>
          <button class="btn btn-sm" onclick="enableTcpip('${dev.serial}')">TCP 5555</button>
          <button class="btn btn-sm" onclick="rebootDevice('${dev.serial}')">Reboot</button>
          ${dev.is_remote || isTunnel ? `<button class="btn btn-sm btn-danger" onclick="disconnectDevice('${dev.serial}')">Ngắt nối</button>` : ''}
        </div>
      </div>
    `;
  }).join('');
}

async function loadDevices() {
  const spin = document.getElementById('refreshSpinIcon');
  if (spin) spin.style.animation = 'pulseLive 0.8s infinite';
  try {
    const res = await fetch('/api/devices');
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const data = await res.json();
    allDevicesCache = data.devices || [];

    const total = allDevicesCache.length;
    const online = allDevicesCache.filter(d => d.state === 'device').length;
    const tunnels = allDevicesCache.filter(d => d.is_tunnel).length;
    const totalBytes = allDevicesCache.reduce((acc, d) => acc + (d.tunnel_bytes || 0), 0);

    document.getElementById('metricTotalDevices').textContent = total;
    document.getElementById('metricOnlineRatio').textContent = `${online} trực tuyến / ${total} tổng`;
    document.getElementById('metricActiveTunnels').textContent = tunnels;
    document.getElementById('metricBridgedBytes').textContent = formatBytesHuman(totalBytes);
    document.getElementById('tabDeviceCountBadge').textContent = total;
    document.getElementById('stagePill').textContent = `${total} thiết bị`;

    filterDeviceCards();
  } catch (err) { console.error('loadDevices err:', err); }
  finally { if (spin) spin.style.animation = ''; }

  try {
    const hRes = await fetch('/api/tunnel/history');
    if (hRes.ok) {
      const hData = await hRes.json();
      allHistoryCache = hData.history || [];
      document.getElementById('tabHistoryCountBadge').textContent = allHistoryCache.length;
      if (currentMainTab === 'history') renderGroupedHistory();
    }
  } catch (err) { console.warn('loadHistory err:', err); }
}

function buildDeviceHistoryGroups() {
  const groups = new Map();
  const getOrCreate = (k, init) => { if (!groups.has(k)) groups.set(k, init); return groups.get(k); };

  allDevicesCache.forEach(dev => {
    if (!dev.is_tunnel && !dev.is_remote) return;
    const key = dev.tunnel_alias || dev.serial;
    const g = getOrCreate(key, {
      key, name: (dev.brand ? dev.brand.toUpperCase() + ' ' : '') + (dev.model || dev.tunnel_alias || 'Android Device'),
      serial: dev.serial, activeSession: null, sessions: []
    });
    if (dev.is_tunnel) {
      g.activeSession = { alias: dev.tunnel_alias, mapped_addr: dev.serial, connected_at: dev.tunnel_connected_at || Math.floor(Date.now() / 1000), bytes_bridged: dev.tunnel_bytes || 0 };
    }
  });

  allHistoryCache.forEach(e => {
    const key = e.alias || e.mapped_addr || 'unknown';
    const g = getOrCreate(key, { key, name: e.alias || 'Android Device', serial: e.mapped_addr || '', activeSession: null, sessions: [] });
    g.sessions.push(e);
  });

  groups.forEach(g => {
    g.totalBytes = (g.activeSession ? (g.activeSession.bytes_bridged || 0) : 0) + g.sessions.reduce((acc, s) => acc + (s.bytes_bridged || 0), 0);
  });

  return Array.from(groups.values());
}

function filterHistoryGroups() {
  const query = (document.getElementById('historySearchInput').value || '').toLowerCase().trim();
  const allGroups = buildDeviceHistoryGroups();
  const filtered = query ? allGroups.filter(g => (g.name + ' ' + g.serial).toLowerCase().includes(query)) : allGroups;
  const container = document.getElementById('historyGroupsContainer');
  document.getElementById('historyDeviceGroupsCount').textContent = `${filtered.length} thiết bị`;

  if (!filtered || filtered.length === 0) {
    container.innerHTML = `<div class="empty-state"><div class="empty-title">Chưa có lịch sử phiên</div></div>`;
    return;
  }

  container.innerHTML = filtered.map(g => `
    <div class="dev-card" style="padding:12px 14px;">
      <div style="display:flex; justify-content:space-between; align-items:center;">
        <div>
          <div style="font-weight:700; color:#fff; font-size:0.88rem;">${escapeHtml(g.name)}</div>
          <div style="font-size:0.7rem; color:var(--text-muted); font-family:var(--font-mono);">${escapeHtml(g.serial)}</div>
        </div>
        <div style="display:flex; gap:10px; align-items:center;">
          <span style="font-family:var(--font-mono); font-size:0.78rem; color:#38bdf8;">${formatBytesHuman(g.totalBytes)}</span>
          <span class="chip" style="font-size:0.68rem;">${g.sessions.length} phiên cũ</span>
        </div>
      </div>
    </div>
  `).join('');
}

function renderGroupedHistory() { filterHistoryGroups(); }

window.addEventListener('keydown', e => {
  if (e.key === '/' && document.activeElement.tagName !== 'INPUT') {
    e.preventDefault();
    document.getElementById('searchInput').focus();
  }
  if (e.key === 'Escape') document.querySelectorAll('.modal.active').forEach(m => m.classList.remove('active'));
});

loadDevices();
setInterval(loadDevices, 6000);
