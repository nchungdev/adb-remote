let currentScreenBlob = null;
let currentScreenObjectUrl = null;
let logcatTimer = null;
let rawLogcatCache = '';

function closeModal(id) {
  const m = document.getElementById(id);
  if (m) m.classList.remove('active');
  if (id === 'logcatModal' && logcatTimer) {
    clearInterval(logcatTimer);
    logcatTimer = null;
    document.getElementById('logcatAutoRefresh').checked = false;
  }
}

function handleBackdropClick(event, id) {
  if (event.target && event.target.id === id) closeModal(id);
}

// Quick Actions
async function connectRemote() {
  const addr = (document.getElementById('remoteAddr').value || '').trim();
  if (!addr) { alert('Vui lòng nhập IP:Port (vd: 192.168.1.50:5555)'); return; }
  await connectDevice(addr);
}

async function connectDevice(addr) {
  try {
    const res = await fetch('/api/devices/connect', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ address: addr })
    });
    const d = await res.json();
    if (!res.ok) throw new Error(d.error || 'Lỗi kết nối');
    loadDevices();
  } catch (err) { alert('Lỗi: ' + err.message); }
}

async function disconnectDevice(addr) {
  if (!confirm(`Ngắt kết nối thiết bị: ${addr}?`)) return;
  try {
    await fetch('/api/devices/disconnect', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ address: addr })
    });
    loadDevices();
  } catch (err) { alert('Lỗi: ' + err.message); }
}

async function enableTcpip(serial) {
  try {
    const res = await fetch('/api/devices/tcpip', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ serial, port: 5555 })
    });
    const d = await res.json();
    alert(d.message || 'Đã kích hoạt cổng TCP 5555!');
    loadDevices();
  } catch (err) { alert('Lỗi: ' + err.message); }
}

async function rebootDevice(serial) {
  if (!confirm(`Reboot thiết bị: ${serial}?`)) return;
  try {
    await fetch('/api/devices/reboot', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ serial })
    });
    alert('Đã gửi lệnh reboot.');
  } catch (err) { alert('Lỗi: ' + err.message); }
}

async function restartServer() {
  if (!confirm('Khởi động lại ADB daemon trên máy chủ?')) return;
  try {
    await fetch('/api/server/restart', { method: 'POST' });
    alert('Đang khởi động lại ADB. Trang sẽ tải lại sau 3s...');
    setTimeout(() => window.location.reload(), 3000);
  } catch (err) { setTimeout(() => window.location.reload(), 2000); }
}

async function clearTunnelHistory() {
  if (!confirm('Xóa các phiên kết nối cũ đã đóng?')) return;
  try {
    const res = await fetch('/api/tunnel/history', { method: 'DELETE' });
    if (res.ok) { allHistoryCache = []; renderGroupedHistory(); }
  } catch (err) { alert('Lỗi: ' + err.message); }
}

// Modals: Connect & Pair
function openConnectModal() {
  document.getElementById('connectModal').classList.add('active');
  setTimeout(() => document.getElementById('connectModalAddr').focus(), 100);
}

async function scanMdns() {
  const list = document.getElementById('mdnsList');
  const area = document.getElementById('mdnsScanArea');
  area.style.display = 'flex';
  list.innerHTML = '<span style="font-size:0.72rem; color:var(--text-muted);">Đang quét mDNS...</span>';
  try {
    const res = await fetch('/api/discovery/mdns');
    const data = await res.json();
    if (!data.services || data.services.length === 0) {
      list.innerHTML = '<span style="font-size:0.72rem; color:var(--text-faint);">Không thấy thiết bị mDNS.</span>';
      return;
    }
    list.innerHTML = data.services.map(s => `
      <div style="display:flex; justify-content:space-between; align-items:center; padding:4px 8px; background:rgba(30,41,59,0.7); border-radius:4px; font-size:0.72rem;">
        <span>${escapeHtml(s.name || s.host)}:<b>${s.port}</b></span>
        <button class="btn btn-sm btn-primary" onclick="document.getElementById('connectModalAddr').value='${s.host}:${s.port}'; connectWirelessModal();">Nối</button>
      </div>
    `).join('');
  } catch (err) { list.innerHTML = `<span style="font-size:0.72rem; color:#f87171;">Lỗi: ${escapeHtml(err.message)}</span>`; }
}

async function connectWirelessModal() {
  const addr = (document.getElementById('connectModalAddr').value || '').trim();
  if (!addr) return;
  closeModal('connectModal');
  await connectDevice(addr);
}

function openPairModal() {
  document.getElementById('pairModal').classList.add('active');
  setTimeout(() => document.getElementById('pairAddr').focus(), 100);
}

async function pairWireless() {
  const addr = (document.getElementById('pairAddr').value || '').trim();
  const code = (document.getElementById('pairCode').value || '').trim();
  if (!addr || !code) { alert('Vui lòng nhập IP:Port và mã 6 số.'); return; }
  try {
    const res = await fetch('/api/devices/pair', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ address: addr, code })
    });
    const d = await res.json();
    if (!res.ok) throw new Error(d.error || 'Ghép đôi thất bại');
    alert(d.message || 'Ghép đôi thành công!');
    closeModal('pairModal');
    loadDevices();
  } catch (err) { alert('Lỗi: ' + err.message); }
}

// Modal: Shell
function openShellModal(serial, name) {
  currentSerial = serial;
  document.getElementById('shellModalTitle').querySelector('span').textContent = `ADB Shell: ${name}`;
  document.getElementById('shellOutput').textContent = 'Sẵn sàng...';
  document.getElementById('shellModal').classList.add('active');
  setTimeout(() => document.getElementById('shellCommandInput').focus(), 100);
}

function quickShell(cmd) {
  document.getElementById('shellCommandInput').value = cmd;
  executeShell();
}

async function executeShell() {
  const cmd = (document.getElementById('shellCommandInput').value || '').trim();
  if (!cmd || !currentSerial) return;
  const out = document.getElementById('shellOutput');
  out.textContent = `$ ${cmd}\nĐang chạy...\n`;
  try {
    const res = await fetch('/api/devices/shell', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ serial: currentSerial, command: cmd })
    });
    const d = await res.json();
    out.textContent = `$ ${cmd}\n` + (d.output || d.error || '(Không có phản hồi)');
  } catch (err) { out.textContent = `$ ${cmd}\nLỗi: ${err.message}`; }
}

function copyShellOutput(btn) { copyText(document.getElementById('shellOutput').textContent, btn); }

// Modal: Screen
function openScreenModal(serial, name) {
  currentSerial = serial;
  document.getElementById('screenModalTitle').querySelector('span').textContent = `Màn hình: ${name}`;
  document.getElementById('screenModal').classList.add('active');
  refreshCurrentScreenshot();
}

async function refreshCurrentScreenshot() {
  if (!currentSerial) return;
  const loadEl = document.getElementById('screenLoading');
  const imgEl = document.getElementById('screenImage');
  loadEl.style.display = 'block';
  imgEl.style.display = 'none';

  try {
    const res = await fetch(`/api/devices/screencap?serial=${encodeURIComponent(currentSerial)}&t=${Date.now()}`);
    if (!res.ok) throw new Error('Chụp màn hình thất bại');
    currentScreenBlob = await res.blob();
    if (currentScreenObjectUrl) URL.revokeObjectURL(currentScreenObjectUrl);
    currentScreenObjectUrl = URL.createObjectURL(currentScreenBlob);
    imgEl.src = currentScreenObjectUrl;
    imgEl.onload = () => { loadEl.style.display = 'none'; imgEl.style.display = 'block'; };
  } catch (err) { loadEl.textContent = 'Lỗi chụp màn hình: ' + err.message; }
}

async function copyCurrentScreenshot(btn) {
  if (!currentScreenBlob) return;
  try {
    await navigator.clipboard.write([new ClipboardItem({ [currentScreenBlob.type || 'image/png']: currentScreenBlob })]);
    flashCopied(btn, '✓ Đã copy ảnh!');
  } catch (err) { alert('Không thể copy ảnh: ' + err.message); }
}

function downloadCurrentScreenshot() {
  if (!currentScreenObjectUrl) return;
  const a = document.createElement('a');
  a.href = currentScreenObjectUrl;
  a.download = `screencap-${currentSerial || 'android'}-${Date.now()}.png`;
  a.click();
}

// Modal: Logcat
function openLogcatModal(serial, name) {
  currentSerial = serial;
  document.getElementById('logcatModalTitle').querySelector('span').textContent = `Logcat: ${name}`;
  document.getElementById('logcatModal').classList.add('active');
  fetchLogcat();
}

async function fetchLogcat() {
  if (!currentSerial) return;
  const out = document.getElementById('logcatOutput');
  try {
    const res = await fetch(`/api/devices/logcat?serial=${encodeURIComponent(currentSerial)}&lines=300`);
    if (!res.ok) throw new Error('Lỗi tải logcat');
    rawLogcatCache = await res.text();
    applyLogcatFilter();
  } catch (err) { out.textContent = 'Lỗi đọc logcat: ' + err.message; }
}

function applyLogcatFilter() {
  const level = document.getElementById('logcatLevel').value;
  const filterText = (document.getElementById('logcatFilterText').value || '').toLowerCase().trim();
  const out = document.getElementById('logcatOutput');
  if (!rawLogcatCache) return;

  const lines = rawLogcatCache.split('\n');
  const filtered = lines.filter(l => {
    if (filterText && !l.toLowerCase().includes(filterText)) return false;
    if (level === 'all') return true;
    if (level === 'I') return l.includes(' I ') || l.includes(' W ') || l.includes(' E ') || l.includes(' F ');
    if (level === 'W') return l.includes(' W ') || l.includes(' E ') || l.includes(' F ');
    if (level === 'E') return l.includes(' E ') || l.includes(' F ');
    return true;
  });

  out.innerHTML = renderColoredLogcat(filtered.join('\n'));
  out.scrollTop = out.scrollHeight;
}

function renderColoredLogcat(text) {
  if (!text) return '(Không có dòng log nào)';
  return escapeHtml(text).replace(/^.*$/gm, l => {
    if (l.includes(' E ') || l.includes(' F ')) return `<span style="color:#f87171;">${l}</span>`;
    if (l.includes(' W ')) return `<span style="color:#fbbf24;">${l}</span>`;
    if (l.includes(' I ')) return `<span style="color:#38bdf8;">${l}</span>`;
    if (l.includes(' D ')) return `<span style="color:#a5b4fc;">${l}</span>`;
    return `<span style="color:#94a3b8;">${l}</span>`;
  });
}

function copyLogcatText(btn) { copyText(document.getElementById('logcatOutput').textContent, btn); }

function downloadLogcatFile() {
  const blob = new Blob([document.getElementById('logcatOutput').textContent], { type: 'text/plain' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `logcat-${currentSerial || 'android'}-${Date.now()}.txt`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function toggleLogcatAutoRefresh(enabled) {
  if (logcatTimer) clearInterval(logcatTimer);
  logcatTimer = null;
  if (enabled) logcatTimer = setInterval(fetchLogcat, 3000);
}
