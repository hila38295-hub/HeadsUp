const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('headsup', {
  get: () => ipcRenderer.invoke('get'),
  save: (patch) => ipcRenderer.invoke('save', patch),
  start: () => ipcRenderer.invoke('start'),
  pause: () => ipcRenderer.invoke('pause'),
  resume: () => ipcRenderer.invoke('resume'),
  stop: () => ipcRenderer.invoke('stop'),
  reserve: () => ipcRenderer.invoke('reserve'),
  cancelReserve: () => ipcRenderer.invoke('cancelReserve'),
  preview: () => ipcRenderer.invoke('preview'),
  test: () => ipcRenderer.invoke('test'),
  copyInfo: () => ipcRenderer.invoke('copyInfo'),
  onStatus: (cb) => ipcRenderer.on('status', (_e, s) => cb(s)),
  onBanner: (cb) => ipcRenderer.on('banner', (_e, b) => cb(b)),
});
