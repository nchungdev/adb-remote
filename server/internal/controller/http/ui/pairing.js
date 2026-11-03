// Real-time Pairing Approval & Remote Tunnel Handling
let pendingDev = null;
async function checkPairing() {
  try {
    const res = await fetch('/api/pair/pending');
    if (!res.ok) return;
    const list = await res.json();
    const prompt = document.getElementById('pairPrompt');
    if (list && list.length > 0) {
      pendingDev = list[0].device_id;
      document.getElementById('pairDev').textContent = list[0].device_name || list[0].device_id;
      document.getElementById('pairCode').textContent = list[0].code;
      prompt.style.display = 'flex';
    } else {
      prompt.style.display = 'none';
    }
  } catch(e) {}
}

async function confirmPair(approved) {
  if (!pendingDev) return;
  await fetch('/api/pair/confirm', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({device_id: pendingDev, approved: approved})
  });
  document.getElementById('pairPrompt').style.display = 'none';
  pendingDev = null;
  loadDevices();
}

async function checkTunnelStatus() {
  try {
    const res = await fetch('/api/remote/status');
    if (!res.ok) return;
    const data = await res.json();
    isTunnelActive = Boolean(data.active);
    const dot = document.getElementById('topbarTunnelDot');
    const txt = document.getElementById('topbarTunnelText');
    if (dot && txt) {
      dot.style.background = isTunnelActive ? '#10b981' : '#ef4444';
      txt.textContent = isTunnelActive ? 'Tunnel: On' : 'Tunnel: Off';
    }
  } catch(e) {}
}

async function toggleQuickTunnel() {
  const next = !isTunnelActive;
  const txt = document.getElementById('topbarTunnelText');
  if (txt) txt.textContent = next ? 'Đang mở...' : 'Đang tắt...';
  try {
    await fetch(`/api/remote/toggle?active=${next}`, { method: 'POST' });
    await checkTunnelStatus();
  } catch(e) {
    checkTunnelStatus();
  }
}

checkPairing();
checkTunnelStatus();

setInterval(checkPairing, 2000);
setInterval(checkTunnelStatus, 3000);
