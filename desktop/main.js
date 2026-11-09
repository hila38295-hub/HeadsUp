// 고개 들어! — 윈도우 버전 (Electron)
// - 설정 창, 항상 맨 위에 뜨는 배너, 작업 표시줄(트레이) 아이콘
// - 랜덤 간격 알림, 수업 시간 후 자동 종료, 일시정지, 예약 시작

const {
  app, BrowserWindow, Tray, Menu, ipcMain, screen, nativeTheme,
  nativeImage, clipboard, Notification,
} = require('electron');
const path = require('path');
const fs = require('fs');
const os = require('os');

const DEFAULTS = {
  minSec: 120,
  maxSec: 240,
  useDuration: true,
  durationMin: 75,
  message: '교수님을 바라보세요!',
  bgColor: '#FFFFFF',
  textColor: '#000000',
  showSec: 5,
  reserveOn: false,
  reserveTime: '09:00',
  reservedAt: 0,
  theme: 'light',
  onboarded: false,
  autoLaunch: false,
  trayHintShown: false,
};

const SMOKE = process.argv.includes('--smoke');
const START_HIDDEN = process.argv.includes('--hidden');
const TEST_DELAY_SEC = 10;
const BANNER_W = 760;
const BANNER_H = 150;

let settings = { ...DEFAULTS };
let mainWin = null;
let bannerWin = null;
let tray = null;
let quitting = false;

const state = {
  running: false,
  paused: false,
  endAt: 0,
  alertCount: 0,
};
const timers = { next: null, end: null, reserve: null, hide: null, test: null };

// ---------------- 설정 저장 ----------------

const settingsFile = () => path.join(app.getPath('userData'), 'settings.json');

function loadSettings() {
  try {
    const raw = JSON.parse(fs.readFileSync(settingsFile(), 'utf8'));
    settings = { ...DEFAULTS, ...raw };
  } catch {
    settings = { ...DEFAULTS };
  }
  settings.minSec = clamp(settings.minSec, 60, 600);
  settings.maxSec = clamp(settings.maxSec, settings.minSec, 600);
}

function saveSettings() {
  try {
    fs.mkdirSync(path.dirname(settingsFile()), { recursive: true });
    fs.writeFileSync(settingsFile(), JSON.stringify(settings, null, 2));
  } catch (e) {
    console.error('settings save failed', e);
  }
}

function clamp(v, a, b) { return Math.min(b, Math.max(a, Number(v) || a)); }

// ---------------- 창 ----------------

function createMainWindow() {
  mainWin = new BrowserWindow({
    width: 520,
    height: 860,
    minWidth: 440,
    minHeight: 600,
    title: '고개 들어!',
    icon: path.join(__dirname, 'assets', 'icon.png'),
    autoHideMenuBar: true,
    show: false,
    backgroundColor: nativeTheme.shouldUseDarkColors ? '#111317' : '#F4F5F7',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });
  mainWin.removeMenu();
  mainWin.loadFile(path.join(__dirname, 'renderer', 'index.html'));
  mainWin.once('ready-to-show', () => {
    if (!START_HIDDEN || SMOKE) mainWin.show();
  });
  // 창을 닫아도 트레이에서 계속 실행
  mainWin.on('close', (e) => {
    if (quitting) return;
    e.preventDefault();
    mainWin.hide();
    if (!settings.trayHintShown) {
      settings.trayHintShown = true;
      saveSettings();
      showSilentNotice('고개 들어!가 계속 실행 중이에요', '오른쪽 아래 작업 표시줄 아이콘에서 열거나 끌 수 있어요.');
    }
  });
}

function createBannerWindow() {
  bannerWin = new BrowserWindow({
    width: BANNER_W,
    height: BANNER_H,
    frame: false,
    transparent: true,
    resizable: false,
    movable: false,
    minimizable: false,
    maximizable: false,
    focusable: false,
    skipTaskbar: true,
    alwaysOnTop: true,
    hasShadow: false,
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      backgroundThrottling: false,
    },
  });
  bannerWin.setAlwaysOnTop(true, 'screen-saver'); // 전체 화면 프로그램 위에도
  bannerWin.setVisibleOnAllWorkspaces(true);
  bannerWin.setIgnoreMouseEvents(true); // 클릭은 아래 프로그램으로 통과
  bannerWin.loadFile(path.join(__dirname, 'renderer', 'banner.html'));
}

function showMain() {
  if (!mainWin) return;
  if (mainWin.isMinimized()) mainWin.restore();
  mainWin.show();
  mainWin.focus();
}

function showSilentNotice(title, body) {
  try {
    if (Notification.isSupported()) new Notification({ title, body, silent: true }).show();
  } catch { /* 무시 */ }
}

// ---------------- 배너 ----------------

function showBanner() {
  if (!bannerWin) return false;
  clearTimeout(timers.hide);
  const point = screen.getCursorScreenPoint();
  const wa = screen.getDisplayNearestPoint(point).workArea;
  const x = Math.round(wa.x + (wa.width - BANNER_W) / 2);
  const y = Math.round(wa.y + 16);
  bannerWin.setBounds({ x, y, width: BANNER_W, height: BANNER_H });
  bannerWin.webContents.send('banner', {
    message: (settings.message || '').trim() || DEFAULTS.message,
    bgColor: settings.bgColor,
    textColor: settings.textColor,
  });
  bannerWin.setAlwaysOnTop(true, 'screen-saver');
  bannerWin.showInactive(); // 지금 쓰는 프로그램의 포커스를 뺏지 않음
  timers.hide = setTimeout(hideBanner, clamp(settings.showSec, 1, 30) * 1000);
  return true;
}

function hideBanner() {
  clearTimeout(timers.hide);
  if (bannerWin && bannerWin.isVisible()) bannerWin.hide();
}

// ---------------- 세션 ----------------

function randomDelaySec() {
  const lo = settings.minSec;
  const hi = Math.max(lo, settings.maxSec);
  return lo + Math.floor(Math.random() * (hi - lo + 1));
}

function scheduleNext() {
  clearTimeout(timers.next);
  timers.next = setTimeout(onAlert, randomDelaySec() * 1000);
}

function onAlert() {
  if (!state.running || state.paused) return;
  if (state.endAt && Date.now() >= state.endAt) { stopSession(); return; }
  if (showBanner()) state.alertCount++;
  scheduleNext();
  pushStatus();
}

function startSession() {
  cancelReserve(false);
  clearTimeout(timers.next);
  clearTimeout(timers.end);
  state.running = true;
  state.paused = false;
  state.alertCount = 0;
  state.endAt = settings.useDuration ? Date.now() + settings.durationMin * 60000 : 0;
  if (state.endAt) timers.end = setTimeout(stopSession, state.endAt - Date.now());
  scheduleNext();
  refreshTray();
  pushStatus();
}

function pauseSession() {
  if (!state.running || state.paused) return;
  state.paused = true;
  clearTimeout(timers.next);
  hideBanner();
  refreshTray();
  pushStatus();
}

function resumeSession() {
  if (!state.running || !state.paused) return;
  state.paused = false;
  scheduleNext();
  refreshTray();
  pushStatus();
}

function stopSession() {
  state.running = false;
  state.paused = false;
  state.endAt = 0;
  clearTimeout(timers.next);
  clearTimeout(timers.end);
  hideBanner();
  refreshTray();
  pushStatus();
}

// ---------------- 예약 ----------------

function nextReserveTime() {
  const [h, m] = String(settings.reserveTime || '09:00').split(':').map(Number);
  const d = new Date();
  d.setHours(h || 0, m || 0, 0, 0);
  if (d.getTime() <= Date.now()) d.setDate(d.getDate() + 1);
  return d.getTime();
}

function armReserve() {
  clearTimeout(timers.reserve);
  if (!settings.reservedAt) return;
  const wait = settings.reservedAt - Date.now();
  if (wait < -60000) { settings.reservedAt = 0; saveSettings(); return; }
  // setTimeout 최대값(약 24.8일) 안쪽이라 문제 없음
  timers.reserve = setTimeout(() => {
    settings.reservedAt = 0;
    saveSettings();
    startSession();
  }, Math.max(0, wait));
}

function reserve() {
  settings.reservedAt = nextReserveTime();
  saveSettings();
  armReserve();
  refreshTray();
  pushStatus();
  return settings.reservedAt;
}

function cancelReserve(push = true) {
  clearTimeout(timers.reserve);
  if (settings.reservedAt) { settings.reservedAt = 0; saveSettings(); }
  if (push) { refreshTray(); pushStatus(); }
}

// ---------------- 상태 / 트레이 ----------------

function status() {
  return {
    running: state.running,
    paused: state.paused,
    endAt: state.endAt,
    alertCount: state.alertCount,
    reservedAt: settings.reservedAt || 0,
    now: Date.now(),
  };
}

function pushStatus() {
  if (mainWin && !mainWin.isDestroyed()) mainWin.webContents.send('status', status());
}

function trayIcon() {
  const img = nativeImage.createFromPath(path.join(__dirname, 'assets', 'tray.png'));
  return img.isEmpty() ? nativeImage.createEmpty() : img.resize({ width: 16, height: 16 });
}

function refreshTray() {
  if (!tray) return;
  const items = [{ label: '열기', click: showMain }, { type: 'separator' }];
  if (state.running) {
    items.push(state.paused
      ? { label: '재개', click: resumeSession }
      : { label: '일시정지', click: pauseSession });
    items.push({ label: '종료', click: stopSession });
  } else if (settings.reservedAt) {
    items.push({ label: '지금 바로 시작', click: startSession });
    items.push({ label: '예약 취소', click: () => cancelReserve() });
  } else {
    items.push({ label: '시작', click: startSession });
  }
  items.push({ type: 'separator' }, {
    label: '앱 완전히 끄기',
    click: () => { quitting = true; app.quit(); },
  });
  tray.setContextMenu(Menu.buildFromTemplate(items));
  tray.setToolTip(state.running
    ? (state.paused ? '고개 들어! · 일시정지됨' : '고개 들어! · 실행 중')
    : (settings.reservedAt ? '고개 들어! · 예약됨' : '고개 들어!'));
}

function applyTheme() {
  nativeTheme.themeSource = ['light', 'dark', 'system'].includes(settings.theme) ? settings.theme : 'light';
}

function applyAutoLaunch() {
  try {
    app.setLoginItemSettings({ openAtLogin: !!settings.autoLaunch, args: ['--hidden'] });
  } catch { /* 무시 */ }
}

function deviceInfo() {
  return [
    '[고개 들어! 버그 문의 · 윈도우]',
    `앱 버전: ${app.getVersion()}`,
    `윈도우: ${os.release()} (${os.arch()})`,
    `간격: ${settings.minSec}~${settings.maxSec}초, 표시 ${settings.showSec}초`,
    `실행 중: ${state.running}, 일시정지: ${state.paused}, 표시 ${state.alertCount}회`,
    `화면 수: ${screen.getAllDisplays().length}`,
    '증상: ',
  ].join('\n');
}

// ---------------- IPC ----------------

function registerIpc() {
  ipcMain.handle('get', () => ({ settings, status: status(), version: app.getVersion() }));
  ipcMain.handle('save', (_e, patch) => {
    const prevTheme = settings.theme;
    const prevAuto = settings.autoLaunch;
    settings = { ...settings, ...patch };
    settings.minSec = clamp(settings.minSec, 60, 600);
    settings.maxSec = clamp(settings.maxSec, settings.minSec, 600);
    saveSettings();
    if (settings.theme !== prevTheme) applyTheme();
    if (settings.autoLaunch !== prevAuto) applyAutoLaunch();
    return settings;
  });
  ipcMain.handle('start', () => { startSession(); return status(); });
  ipcMain.handle('pause', () => { pauseSession(); return status(); });
  ipcMain.handle('resume', () => { resumeSession(); return status(); });
  ipcMain.handle('stop', () => { stopSession(); return status(); });
  ipcMain.handle('reserve', () => reserve());
  ipcMain.handle('cancelReserve', () => { cancelReserve(); return status(); });
  ipcMain.handle('preview', () => showBanner());
  ipcMain.handle('test', () => {
    clearTimeout(timers.test);
    timers.test = setTimeout(showBanner, TEST_DELAY_SEC * 1000);
    if (mainWin) mainWin.minimize(); // 다른 프로그램을 보고 있는 상황처럼
    return TEST_DELAY_SEC;
  });
  ipcMain.handle('copyInfo', () => { clipboard.writeText(deviceInfo()); return true; });
}

// ---------------- 실행 테스트 (CI용) ----------------

async function runSmoke() {
  const out = process.env.SMOKE_OUT || path.join(os.tmpdir(), 'headsup-smoke');
  fs.mkdirSync(out, { recursive: true });
  const wait = (ms) => new Promise((r) => setTimeout(r, ms));
  const shot = async (win, name) => {
    try {
      const img = await win.webContents.capturePage();
      fs.writeFileSync(path.join(out, name), img.toPNG());
    } catch (e) {
      fs.writeFileSync(path.join(out, name + '.err.txt'), String(e && e.stack || e));
    }
  };
  const log = [];
  try {
    await wait(3000);
    await shot(mainWin, '1_onboarding.png');
    await mainWin.webContents.executeJavaScript('window.__smokeCloseGuide && window.__smokeCloseGuide()');
    await wait(600);
    await shot(mainWin, '2_main.png');
    await mainWin.webContents.executeJavaScript('window.__smokeOpenMore && window.__smokeOpenMore()');
    await wait(600);
    await shot(mainWin, '3_more.png');
    await mainWin.webContents.executeJavaScript('window.__smokeOpenColor && window.__smokeOpenColor()');
    await wait(600);
    await shot(mainWin, '4_color.png');
    settings.theme = 'dark'; applyTheme();
    await mainWin.webContents.executeJavaScript('window.__smokeCloseAll && window.__smokeCloseAll()');
    await wait(800);
    await shot(mainWin, '5_dark.png');
    log.push('banner shown: ' + showBanner());
    await wait(900);
    log.push('banner visible: ' + bannerWin.isVisible());
    log.push('banner bounds: ' + JSON.stringify(bannerWin.getBounds()));
    await shot(bannerWin, '6_banner.png');
    startSession();
    log.push('status: ' + JSON.stringify(status()));
    stopSession();
  } catch (e) {
    log.push('ERROR ' + (e && e.stack || e));
  }
  fs.writeFileSync(path.join(out, 'log.txt'), log.join('\n'));
  quitting = true;
  app.exit(0);
}

// ---------------- 시작 ----------------

if (!SMOKE && !app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', showMain);
  app.setAppUserModelId('com.headsup.desktop');

  app.whenReady().then(() => {
    if (SMOKE) {
      // 실행 테스트는 깨끗한 설정에서
      app.setPath('userData', path.join(os.tmpdir(), 'headsup-smoke-data-' + Date.now()));
    }
    loadSettings();
    applyTheme();
    registerIpc();
    createMainWindow();
    createBannerWindow();
    tray = new Tray(trayIcon());
    tray.on('click', showMain);
    refreshTray();
    armReserve();
    setInterval(pushStatus, 1000);
    if (SMOKE) runSmoke();
  });

  app.on('before-quit', () => { quitting = true; });
  // 창을 다 닫아도 트레이에서 계속 실행
  app.on('window-all-closed', (e) => { if (!quitting) e.preventDefault(); });
}
