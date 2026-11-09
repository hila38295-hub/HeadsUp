const api = window.headsup;
const $ = (id) => document.getElementById(id);
const DEFAULT_MESSAGE = '교수님을 바라보세요!';

const PALETTE = [
  '#FFFFFF', '#F5F5F5', '#E0E0E0', '#BDBDBD', '#9E9E9E', '#616161', '#303030', '#000000',
  '#FFCDD2', '#F8BBD0', '#E1BEE7', '#D1C4E9', '#C5CAE9', '#BBDEFB', '#B2EBF2', '#B2DFDB',
  '#C8E6C9', '#DCEDC8', '#F0F4C3', '#FFF9C4', '#FFECB3', '#FFE0B2', '#FFCCBC', '#D7CCC8',
  '#F44336', '#E91E63', '#9C27B0', '#673AB7', '#3F51B5', '#2196F3', '#03A9F4', '#00BCD4',
  '#009688', '#4CAF50', '#8BC34A', '#CDDC39', '#FFEB3B', '#FFC107', '#FF9800', '#FF5722',
  '#B71C1C', '#880E4F', '#4A148C', '#1A237E', '#0D47A1', '#004D40', '#1B5E20', '#3E2723',
];

const GUIDE = [
  ['👀', '고개 들어!', '수업 중에 화면만 보고 있으면 의심받기 쉬워요.\n정해 둔 간격 안에서 랜덤한 순간에\n화면 맨 위에 배너로 알려 드려요.'],
  ['⏱️', '원하는 대로 설정', '알림 간격(1~10분), 수업 시간, 시작 예약,\n배너 문구와 색까지 바꿀 수 있어요.\n소리는 없고, 클릭이 배너를 통과해요.'],
  ['🖥️', '창을 닫아도 계속 실행', '창을 닫으면 오른쪽 아래 작업 표시줄 아이콘으로 들어가요.\n(안 보이면 ^ 화살표 안에 있어요)\n아이콘을 누르면 열기·일시정지·종료를 할 수 있어요.'],
  ['✅', '바로 확인해 보기', "'10초 뒤 띄워보기'를 누르면 창이 내려가고\n10초 뒤 화면 위에 배너가 떠요.\n테마 변경과 문의는 오른쪽 위 ⋯ 더보기에 있어요."],
];

let S = null;
let ST = { running: false, paused: false, endAt: 0, alertCount: 0, reservedAt: 0, now: Date.now() };
let editingBg = true;

// ---------- 포맷 ----------
const fmtSec = (sec) => { const m = Math.floor(sec / 60), r = sec % 60; return r ? `${m}분 ${r}초` : `${m}분`; };
const fmtMin = (min) => { const h = Math.floor(min / 60), m = min % 60; return !h ? `${m}분` : (!m ? `${h}시간` : `${h}시간 ${m}분`); };
const fmtClock = (s) => { const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), x = s % 60;
  const p = (n) => String(n).padStart(2, '0'); return h ? `${h}:${p(m)}:${p(x)}` : `${m}:${p(x)}`; };
const fmtTime = (ms) => new Date(ms).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' });
const fmtDateTime = (ms) => { const d = new Date(ms);
  return `${d.getMonth() + 1}/${d.getDate()}(${'일월화수목금토'[d.getDay()]}) ${fmtTime(ms)}`; };
const msg = () => (S.message || '').trim() || DEFAULT_MESSAGE;

function toast(text) {
  const t = $('toast'); t.textContent = text; t.classList.remove('hidden');
  clearTimeout(toast.h); toast.h = setTimeout(() => t.classList.add('hidden'), 2600);
}

function save(patch) { Object.assign(S, patch); api.save(patch); }

// ---------- 대비 ----------
function lum(hex) {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255]
    .map((v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); })
    .reduce((a, v, i) => a + v * [0.2126, 0.7152, 0.0722][i], 0);
}
const contrast = (a, b) => { const x = lum(a), y = lum(b); return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05); };

// ---------- 그리기 ----------
function paintPreview(el) {
  el.textContent = msg();
  el.style.background = S.bgColor;
  el.style.color = S.textColor;
}

function renderSettings() {
  $('minSec').value = S.minSec; $('maxSec').value = S.maxSec;
  $('minLabel').textContent = fmtSec(S.minSec); $('maxLabel').textContent = fmtSec(S.maxSec);
  $('rangeLabel').textContent = S.minSec === S.maxSec
    ? `${fmtSec(S.minSec)}마다 (고정)` : `${fmtSec(S.minSec)} ~ ${fmtSec(S.maxSec)} 사이 랜덤 (초 단위)`;

  $('useDuration').checked = S.useDuration;
  $('durationMin').value = S.durationMin;
  $('durLabel').textContent = fmtMin(S.durationMin);
  $('durationRow').classList.toggle('hidden', !S.useDuration);
  $('durationLabel').textContent = S.useDuration
    ? `시작하고 ${fmtMin(S.durationMin)} 뒤 자동으로 꺼져요` : '종료를 누를 때까지 계속 알려 드려요';

  $('reserveOn').checked = S.reserveOn;
  $('reserveTime').value = S.reserveTime || '09:00';
  $('reserveRow').classList.toggle('hidden', !S.reserveOn);

  paintPreview($('preview'));
  $('bgHex').textContent = S.bgColor.toUpperCase(); $('bgDot').style.background = S.bgColor;
  $('textHex').textContent = S.textColor.toUpperCase(); $('textDot').style.background = S.textColor;
  $('showSec').value = S.showSec; $('showLabel').textContent = `${S.showSec}초`;
}

function nextReserveMs() {
  const [h, m] = (S.reserveTime || '09:00').split(':').map(Number);
  const d = new Date(); d.setHours(h || 0, m || 0, 0, 0);
  if (d.getTime() <= Date.now()) d.setDate(d.getDate() + 1);
  return d.getTime();
}

function renderStatus() {
  const now = Date.now();
  const p = $('btnPrimary'), s = $('btnSecondary');
  if (ST.running) {
    $('stTitle').textContent = ST.paused ? '일시정지됨' : '실행 중';
    const line1 = ST.endAt
      ? `남은 수업 시간 ${fmtClock(Math.max(0, Math.floor((ST.endAt - now) / 1000)))} · ${fmtTime(ST.endAt)} 종료`
      : '종료를 누를 때까지 계속 알려 드려요';
    $('stSub').textContent = `${line1}\n지금까지 ${ST.alertCount}번 표시됨`;
    p.textContent = ST.paused ? '재개' : '일시정지';
    s.textContent = '종료'; s.classList.remove('hidden');
  } else if (ST.reservedAt) {
    $('stTitle').textContent = '예약됨';
    $('stSub').textContent = `${fmtDateTime(ST.reservedAt)}에 자동으로 시작해요`;
    p.textContent = '지금 바로 시작';
    s.textContent = '예약 취소'; s.classList.remove('hidden');
  } else {
    $('stTitle').textContent = '대기 중';
    $('stSub').textContent = S.reserveOn
      ? `${fmtDateTime(nextReserveMs())}에 시작하도록 예약할 수 있어요`
      : `시작하면 ${fmtSec(S.minSec)}~${fmtSec(S.maxSec)} 뒤 첫 배너가 떠요`;
    p.textContent = S.reserveOn ? '예약하기' : '시작';
    s.classList.add('hidden');
  }
}

// ---------- 색 선택 ----------
function openColor(bg) {
  editingBg = bg;
  $('cpTitle').textContent = bg ? '배경색' : '글자색';
  renderColor();
  $('colorModal').classList.remove('hidden');
}

function renderColor() {
  const cur = (editingBg ? S.bgColor : S.textColor).toUpperCase();
  paintPreview($('cpPreview'));
  $('cpWarn').classList.toggle('hidden', contrast(S.bgColor, S.textColor) >= 3);
  $('cpHex').textContent = cur;
  $('cpCustom').value = cur.toLowerCase();
  document.querySelectorAll('#cpGrid .sw').forEach((b) => b.classList.toggle('sel', b.dataset.c === cur));
}

function setColor(c) {
  c = c.toUpperCase();
  save(editingBg ? { bgColor: c } : { textColor: c });
  renderColor(); renderSettings();
}

function buildPalette() {
  const g = $('cpGrid');
  PALETTE.forEach((c) => {
    const b = document.createElement('button');
    b.className = 'sw'; b.style.background = c; b.dataset.c = c; b.title = c;
    b.addEventListener('click', () => setColor(c));
    g.appendChild(b);
  });
  $('cpCustom').addEventListener('input', (e) => setColor(e.target.value));
  $('cpDone').addEventListener('click', () => $('colorModal').classList.add('hidden'));
}

// ---------- 더보기 / 안내 ----------
function openMore() {
  document.querySelectorAll('#themeSeg button').forEach((b) => b.classList.toggle('on', b.dataset.theme === S.theme));
  $('autoLaunch').checked = !!S.autoLaunch;
  $('moreModal').classList.remove('hidden');
}

let gi = 0;
function renderGuide() {
  const [e, t, b] = GUIDE[gi];
  $('gEmoji').textContent = e; $('gTitle').textContent = t; $('gBody').textContent = b;
  $('gDots').textContent = GUIDE.map((_, i) => (i === gi ? '●' : '○')).join(' ');
  $('gNext').textContent = gi === GUIDE.length - 1 ? '시작하기' : '다음';
  $('gSkip').style.visibility = gi === GUIDE.length - 1 ? 'hidden' : 'visible';
}
function openGuide() { gi = 0; renderGuide(); $('guideModal').classList.remove('hidden'); }
function closeGuide() { $('guideModal').classList.add('hidden'); save({ onboarded: true }); }

// ---------- 이벤트 ----------
function bind() {
  const num = (id) => Number($(id).value);
  $('minSec').addEventListener('input', () => {
    const v = num('minSec'); const patch = { minSec: v };
    if (v > S.maxSec) patch.maxSec = v;
    save(patch); renderSettings(); renderStatus();
  });
  $('maxSec').addEventListener('input', () => {
    const v = num('maxSec'); const patch = { maxSec: v };
    if (v < S.minSec) patch.minSec = v;
    save(patch); renderSettings(); renderStatus();
  });
  $('useDuration').addEventListener('change', (e) => { save({ useDuration: e.target.checked }); renderSettings(); });
  $('durationMin').addEventListener('input', () => { save({ durationMin: num('durationMin') }); renderSettings(); });
  $('reserveOn').addEventListener('change', (e) => { save({ reserveOn: e.target.checked }); renderSettings(); renderStatus(); });
  $('reserveTime').addEventListener('change', (e) => { save({ reserveTime: e.target.value || '09:00' }); renderStatus(); });
  $('message').addEventListener('input', (e) => { save({ message: e.target.value }); renderSettings(); });
  $('showSec').addEventListener('input', () => { save({ showSec: num('showSec') }); renderSettings(); });
  $('pickBg').addEventListener('click', () => openColor(true));
  $('pickText').addEventListener('click', () => openColor(false));

  $('btnPreview').addEventListener('click', () => api.preview());
  $('btnTest').addEventListener('click', async () => {
    const sec = await api.test();
    toast(`${sec}초 뒤 화면 위에 배너가 떠요`);
  });

  $('btnPrimary').addEventListener('click', async () => {
    if (ST.running) ST = ST.paused ? await api.resume() : await api.pause();
    else if (ST.reservedAt) ST = await api.start();
    else if (S.reserveOn) {
      const at = await api.reserve();
      ST = { ...ST, reservedAt: at };
      toast(`${fmtDateTime(at)}에 시작하도록 예약했어요`);
    } else {
      ST = await api.start();
      toast('시작했어요. 창을 닫아도 계속 실행돼요');
    }
    renderStatus();
  });
  $('btnSecondary').addEventListener('click', async () => {
    if (ST.running) ST = await api.stop();
    else if (ST.reservedAt) { ST = await api.cancelReserve(); toast('예약을 취소했어요'); }
    renderStatus();
  });

  $('btnMore').addEventListener('click', openMore);
  $('btnMoreClose').addEventListener('click', () => $('moreModal').classList.add('hidden'));
  document.querySelectorAll('#themeSeg button').forEach((b) => b.addEventListener('click', () => {
    save({ theme: b.dataset.theme }); openMore();
  }));
  $('autoLaunch').addEventListener('change', (e) => save({ autoLaunch: e.target.checked }));
  $('btnCopy').addEventListener('click', async () => { await api.copyInfo(); toast('복사했어요. 카톡에 붙여넣어 보내 주세요'); });
  $('btnGuide').addEventListener('click', () => { $('moreModal').classList.add('hidden'); openGuide(); });

  $('gSkip').addEventListener('click', closeGuide);
  $('gNext').addEventListener('click', () => { if (gi === GUIDE.length - 1) closeGuide(); else { gi++; renderGuide(); } });

  // 바깥 클릭으로 닫기 (안내 창은 제외)
  ['colorModal', 'moreModal'].forEach((id) => $(id).addEventListener('click', (e) => {
    if (e.target.id === id) $(id).classList.add('hidden');
  }));

  api.onStatus((st) => { ST = st; renderStatus(); });
}

// 실행 테스트(CI)에서 화면을 캡처하기 위한 도우미
window.__smokeCloseGuide = () => closeGuide();
window.__smokeOpenMore = () => openMore();
window.__smokeOpenColor = () => { $('moreModal').classList.add('hidden'); openColor(true); };
window.__smokeCloseAll = () => document.querySelectorAll('.modal').forEach((m) => m.classList.add('hidden'));

(async () => {
  const d = await api.get();
  S = d.settings; ST = d.status;
  $('version').textContent = `고개 들어! for Windows v${d.version}`;
  $('message').value = S.message;
  buildPalette();
  bind();
  renderSettings();
  renderStatus();
  if (!S.onboarded) openGuide();
})();
