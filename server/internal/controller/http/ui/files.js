// File manager for one device. Built with DOM calls, not HTML strings: names come from the device.
const filesState = { serial: '', path: '/sdcard' };
const INSTALL_POLL_MS = 2000;
const INSTALL_TIMEOUT_MS = 180000;

function filesEl(tag, className, text) {
  const el = document.createElement(tag);
  if (className) el.className = className;
  if (text !== undefined) el.textContent = text;
  return el;
}

function ensureFilesModal() {
  if (document.getElementById('filesModal')) return;
  const modal = filesEl('div', 'modal');
  modal.id = 'filesModal';
  modal.onclick = (event) => handleBackdropClick(event, 'filesModal');
  modal.innerHTML = `
    <div class="modal-box" style="max-width:760px;">
      <div class="modal-head"><div id="filesTitle" class="modal-title"></div></div>
      <div class="modal-body">
        <div class="files-bar">
          <button class="btn btn-sm" id="filesUp">Lên trên</button>
          <code id="filesPath" class="files-path"></code>
          <button class="btn btn-sm btn-primary" id="filesPick">Tải lên</button>
          <input type="file" id="filesInput" multiple hidden>
        </div>
        <div id="filesDrop" class="files-drop">
          <div id="filesList" class="files-list"></div>
          <div class="files-hint">Kéo thả file vào đây để gửi lên điện thoại</div>
        </div>
        <div id="filesJobs" class="files-jobs"></div>
      </div>
      <div class="modal-foot"><button class="btn btn-sm btn-danger" id="filesClose">Đóng</button></div>
    </div>`;
  document.body.appendChild(modal);
  document.getElementById('filesClose').onclick = () => closeModal('filesModal');
  document.getElementById('filesUp').onclick = () => {
    const parent = filesState.path.replace(/\/[^/]*$/, '');
    if (parent) loadFiles(parent);
  };
  const input = document.getElementById('filesInput');
  document.getElementById('filesPick').onclick = () => input.click();
  input.onchange = () => { sendFiles(input.files); input.value = ''; };
  const drop = document.getElementById('filesDrop');
  drop.ondragover = (event) => { event.preventDefault(); drop.classList.add('over'); };
  drop.ondragleave = () => drop.classList.remove('over');
  drop.ondrop = (event) => {
    event.preventDefault();
    drop.classList.remove('over');
    sendFiles(event.dataTransfer.files);
  };
}

function openFilesModal(serial, name) {
  ensureFilesModal();
  filesState.serial = serial;
  document.getElementById('filesTitle').textContent = 'File trên ' + name;
  document.getElementById('filesJobs').textContent = '';
  document.getElementById('filesModal').classList.add('active');
  loadFiles('/sdcard');
}

function filesQuery(params) {
  return new URLSearchParams({ serial: filesState.serial, ...params }).toString();
}

async function loadFiles(path) {
  filesState.path = path;
  document.getElementById('filesPath').textContent = path;
  const list = document.getElementById('filesList');
  list.textContent = 'Đang tải...';
  try {
    const res = await fetch('/api/files?' + filesQuery({ path }));
    if (!res.ok) throw new Error(await res.text());
    const data = await res.json();
    list.textContent = '';
    if (data.files.length === 0) list.appendChild(filesEl('div', 'files-empty', 'Thư mục trống'));
    data.files.forEach((file) => list.appendChild(fileRow(file)));
  } catch (err) {
    list.textContent = '';
    list.appendChild(filesEl('div', 'files-error', err.message));
  }
}

function fileRow(file) {
  const row = filesEl('div', 'file-row');
  const name = filesEl(file.is_dir ? 'button' : 'span', 'file-name' + (file.is_dir ? ' is-dir' : ''), file.name);
  if (file.is_dir) name.onclick = () => loadFiles(file.path);
  row.appendChild(name);
  row.appendChild(filesEl('span', 'file-meta', file.is_dir ? '' : formatBytesHuman(file.size)));
  row.appendChild(filesEl('span', 'file-meta', file.modified));
  const actions = filesEl('span', 'file-actions');
  if (!file.is_dir) {
    if (file.name.toLowerCase().endsWith('.apk')) {
      const install = filesEl('button', 'btn btn-sm btn-primary', 'Cài');
      install.onclick = () => installApk(file);
      actions.appendChild(install);
    }
    const download = filesEl('a', 'btn btn-sm', 'Tải về');
    download.href = '/api/files/download?' + filesQuery({ path: file.path });
    download.download = file.name;
    actions.appendChild(download);
  }
  row.appendChild(actions);
  return row;
}

function jobRow(title) {
  const row = filesEl('div', 'file-job');
  row.appendChild(filesEl('span', 'file-name', title));
  const status = filesEl('span', 'file-meta');
  const bar = filesEl('div', 'file-bar');
  const fill = filesEl('div', 'file-bar-fill');
  bar.appendChild(fill);
  row.appendChild(status);
  row.appendChild(bar);
  document.getElementById('filesJobs').prepend(row);
  return {
    say(text, failed) { status.textContent = text; status.classList.toggle('files-error', !!failed); },
    progress(ratio) { fill.style.width = Math.round(ratio * 100) + '%'; },
    finish() { bar.remove(); }
  };
}

async function sendFiles(fileList) {
  const dir = filesState.path;
  for (const file of Array.from(fileList)) {
    await sendFile(file, dir, false);
  }
  if (filesState.path === dir) loadFiles(dir);
}

function sendFile(file, dir, overwrite) {
  const job = jobRow(file.name);
  return new Promise((resolve) => {
    const xhr = new XMLHttpRequest();
    const params = { dir, name: file.name };
    if (overwrite) params.overwrite = '1';
    xhr.open('POST', '/api/files/upload?' + filesQuery(params));
    xhr.upload.onprogress = (event) => {
      if (!event.lengthComputable) return;
      job.progress(event.loaded / event.total);
      job.say(event.loaded < event.total ? 'Đang gửi lên server...' : 'Đang chuyển vào điện thoại...');
    };
    xhr.onload = async () => {
      job.finish();
      if (xhr.status === 409 && confirm(`"${file.name}" đã có trên điện thoại. Ghi đè?`)) {
        job.say('Ghi đè...');
        resolve(await sendFile(file, dir, true));
      } else if (xhr.status === 409) {
        job.say('Đã bỏ qua');
        resolve();
      } else if (xhr.status === 200) {
        job.say('Xong');
        resolve();
      } else {
        job.say(xhr.responseText || 'Gửi thất bại', true);
        resolve();
      }
    };
    xhr.onerror = () => { job.finish(); job.say('Mất kết nối tới server', true); resolve(); };
    xhr.send(file);
  });
}

async function installApk(file) {
  const job = jobRow('Cài ' + file.name);
  job.finish();
  job.say('Đang cài...');
  const serial = filesState.serial;
  try {
    const res = await fetch('/api/files/install?' + filesQuery({ path: file.path }), { method: 'POST' });
    if (!res.ok) throw new Error(await res.text());
    const { job: id } = await res.json();
    const deadline = Date.now() + INSTALL_TIMEOUT_MS;
    while (Date.now() < deadline) {
      await new Promise((done) => setTimeout(done, INSTALL_POLL_MS));
      const poll = await fetch('/api/files/install?' + new URLSearchParams({ serial, job: id }));
      if (!poll.ok) continue;
      const status = await poll.json();
      if (status.state === 'success') { job.say('Đã cài xong'); return; }
      if (status.state === 'failure') {
        job.say(status.output || 'Cài thất bại', true);
        return;
      }
    }
    job.say('Chưa rõ kết quả: thiết bị chưa kết nối lại. Hãy mở điện thoại kiểm tra.', true);
  } catch (err) {
    job.say(err.message, true);
  }
}
