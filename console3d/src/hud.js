// SecureGuard Ops Center — HUD (metrics, event feed, asset list, action dock,
// drawers, command palette, confirm dialog, toasts). DOM-only; reads/writes
// the shared ops state via events. No business logic of its own.
import { CHANNELS, getState, isSceneAction, on, emit, selectAsset, selectAll, execute, toggleFav, nowStamp } from './ops.js'

const SCENE_ACTIONS = [
  { key: 'SWEEP', label: 'Sweep', risk: 'LOW', riskClass: 'low', scene: true },
  { key: 'FOCUS', label: 'Focus', risk: 'LOW', riskClass: 'low', scene: true },
  { key: 'GEOFENCE', label: 'Geofence', risk: 'MED', riskClass: 'med', scene: true },
  { key: 'FORCE', label: 'Force', risk: 'HIGH', riskClass: 'high', scene: true },
  { key: 'ACTION', label: 'Scene Action', risk: 'MED', riskClass: 'med', scene: true }
]

const DEVICE_ACTIONS = [
  { key: 'ALARM', label: 'Alarm', risk: 'HIGH', riskClass: 'high' },
  { key: 'LIGHT', label: 'Lights', risk: 'LOW', riskClass: 'low' },
  { key: 'MOTOR_OFF', label: 'Motor Off', risk: 'HIGH', riskClass: 'high' },
  { key: 'BATTERY', label: 'Battery', risk: 'LOW', riskClass: 'low' },
  { key: 'MESSAGE', label: 'Message', risk: 'LOW', riskClass: 'low' },
  { key: 'POSITION', label: 'Position', risk: 'MED', riskClass: 'med' },
  { key: 'RESTART', label: 'Restart', risk: 'CRIT', riskClass: 'crit' },
  { key: 'TELEMETRY', label: 'Telemetry', risk: 'LOW', riskClass: 'low' }
]

let el = {}
let searchQ = ''
let favFilter = false
let confirmPending = null
let paletteIndex = 0
let paletteItems = []

function $(id) {
  return document.getElementById(id)
}

function toast(text, kind) {
  const host = $('toasts')
  const t = document.createElement('div')
  t.className = 'toast' + (kind === 'err' ? ' err' : '')
  t.textContent = text
  host.appendChild(t)
  window.setTimeout(() => {
    if (t.parentNode) t.parentNode.removeChild(t)
  }, 3600)
}

// ---- top metric bar ------------------------------------------------
function renderMetrics() {
  const st = getState()
  const c = st.counts || {}
  const agentOk = !!(st.agent && (st.agent.online || st.agent.running))
  const src = st.source || 'SIMULATION'
  const sim = src === 'SIMULATION'
  el.topbar.innerHTML = ''
  const metric = (k, v, sub) => {
    const d = document.createElement('div')
    d.className = 'metric'
    d.innerHTML = `<div class="k">${k}</div><div class="v">${v}</div><div class="sub">${sub || ''}</div>`
    el.topbar.appendChild(d)
  }
  metric('ASSETS', c.assets ?? 0, `online ${c.online ?? 0}`)
  metric('ALARMS', c.alarms ?? 0, 'open')
  metric('DETECT', c.detections ?? 0, 'total')
  metric('QUEUE', c.queue ?? 0, 'offline')
  const ab = document.createElement('div')
  ab.className = 'badge ' + (agentOk ? 'ok' : 'err')
  ab.innerHTML = `<span class="dot"></span>AGENT ${sim || agentOk ? 'ONLINE' : 'OFFLINE'}`
  el.topbar.appendChild(ab)
  const sr = document.createElement('div')
  sr.className = 'badge ' + (sim ? 'source-sim' : 'ok')
  sr.innerHTML = `<span class="dot"></span>SOURCE: ${src}`
  sr.title = sim ? 'Keine Native-/Backend-Daten — Simulation, keine echten Gerätedaten' : 'Echte Datenquelle'
  el.topbar.appendChild(sr)
}

// ---- event feed ----------------------------------------------------
function renderFeed() {
  const st = getState()
  el.eventfeed.innerHTML = (st.feed || []).slice(-14).reverse().map((ev) => `<div class="ev"><span class="t">${ev.t}</span>${escapeHtml(ev.text)}</div>`).join('')
}

// ---- asset list ----------------------------------------------------
function renderAssetList() {
  const st = getState()
  const q = searchQ.trim().toLowerCase()
  const list = (st.assets || []).filter((a) => {
    if (!q) return true
    const hay = (a.name + ' ' + (a.shortName || '') + ' ' + (a.mac || '') + ' ' + a.id).toLowerCase()
    return hay.includes(q)
  })
  el.assetlist.innerHTML = list
    .map((a) => {
      const color = statusRgb(a.status)
      const sel = st.selected.has(a.id) ? ' selected' : ''
      return `<div class="asset-item${sel}" data-id="${escapeHtml(a.id)}"><span class="led" style="background:${color}"></span><span class="nm">${escapeHtml(a.shortName || a.name)}</span><span class="rssi">${a.rssi ?? '–'}dBm</span></div>`
    })
    .join('')
}

// ---- action dock -----------------------------------------------------
function renderDock() {
  const st = getState()
  const favs = st.favs || new Set()
  const actions = favFilter ? SCENE_ACTIONS.concat(DEVICE_ACTIONS).filter((a) => favs.has(actionKey(a))) : SCENE_ACTIONS.concat(DEVICE_ACTIONS)
  el.actiondock.innerHTML =
    `<div style="font-family:var(--mono);font-size:10px;color:var(--muted);">ACTIONS</div>` +
    `<button class="btn favtoggle" type="button">${favFilter ? '★ all' : '☆ favs'}</button>` +
    actions
      .map(
        (a) =>
          `<div class="btn" data-action="${a.key}" role="button" tabindex="0"><span>${a.scene ? '◈ ' : ''}${a.label}</span><span class="risk ${a.riskClass}">${a.risk}</span></div>`
      )
      .join('')
}

function actionKey(a) {
  return (a.scene ? 'scene:' : 'device:') + a.key
}

function statusRgb(status) {
  switch (String(status).toUpperCase()) {
    case 'ONLINE': return '#00e676'
    case 'OFFLINE': return '#ff1744'
    case 'MAINTENANCE': return '#ffc400'
    case 'SEARCHING': return '#448aff'
    default: return '#8899aa'
  }
}

function escapeHtml(s) {
  return String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]))
}

// ---- drawers ---------------------------------------------------------
function renderLog() {
  const st = getState()
  el.logdrawer.innerHTML = '<h3>Log</h3>' + (st.log || []).slice(-40).reverse().map((l) => `<div class="row">${escapeHtml(l)}</div>`).join('')
}

function renderAlarms() {
  const st = getState()
  if (!st.alarms || !st.alarms.length) {
    el.alarmdrawer.innerHTML = '<h3>Alarms</h3><div class="row">No alarms.</div>'
    return
  }
  el.alarmdrawer.innerHTML =
    '<h3>Alarms</h3>' +
    st.alarms
      .slice()
      .reverse()
      .map((a) => {
        const sev = String(a.severity || 'INFO').toUpperCase()
        const color = sev === 'CRITICAL' ? '#ff1744' : sev === 'WARNING' ? '#ffc400' : '#00e676'
        const t = a.timestamp ? new Date(a.timestamp).toLocaleTimeString('en-GB', { hour12: false }) : '–'
        return `<div class="row"><span style="color:${color}">●</span> [${sev}] ${t} · ${escapeHtml(a.message || a.type || '')} ${a.acknowledged ? '' : '(open)'}</div>`
      })
      .join('')
}

// ---- command palette ---------------------------------------------------
const COMMANDS = [
  { id: 'sweep', label: 'Sweep (scene)', run: () => runAction('SWEEP') },
  { id: 'focus', label: 'Focus (scene)', run: () => runAction('FOCUS') },
  { id: 'geofence', label: 'Geofence (scene)', run: () => runAction('GEOFENCE') },
  { id: 'force', label: 'Force (scene)', run: () => runAction('FORCE') },
  { id: 'alarm', label: 'Device: Alarm', run: () => runAction('ALARM') },
  { id: 'motor_off', label: 'Device: Motor Off', run: () => runAction('MOTOR_OFF') },
  { id: 'restart', label: 'Device: Restart', run: () => runAction('RESTART') },
  { id: 'select_all', label: 'Select all assets', run: () => { selectAll(true); renderAssetList() } },
  { id: 'clear_selection', label: 'Clear selection', run: () => { selectAll(false); renderAssetList() } },
  { id: 'toggle_log', label: 'Toggle log drawer', run: toggleLog },
  { id: 'toggle_alarms', label: 'Toggle alarm drawer', run: toggleAlarms }
]

function openPalette() {
  el.palette.classList.add('open')
  el.paletteInput.value = ''
  paletteIndex = 0
  updatePalette('')
  el.paletteInput.focus()
}

function closePalette() {
  el.palette.classList.remove('open')
}

function updatePalette(q) {
  const t = q.trim().toLowerCase()
  paletteItems = COMMANDS.filter((c) => c.label.toLowerCase().includes(t))
  paletteIndex = 0
  el.paletteResults.innerHTML = paletteItems
    .map((c, i) => `<div class="cmd${i === 0 ? ' active' : ''}">${escapeHtml(c.label)}</div>`)
    .join('')
}

function paletteSelect(i) {
  const c = paletteItems[i]
  if (!c) return
  closePalette()
  c.run()
}

// ---- confirm dialog -------------------------------------------------
function runAction(action) {
  const ids = Array.from(getState().selected)
  const isScene = isSceneAction(action)
  if (!isScene && ids.length === 0) {
    toast('Select at least one asset', 'err')
    return
  }
  const risk = [...SCENE_ACTIONS, ...DEVICE_ACTIONS].find((a) => a.key === action)
  const risky = risk && (risk.riskClass === 'high' || risk.riskClass === 'crit')
  if (risky) {
    confirmAction(action, ids)
    return
  }
  execute(action, ids)
  if (isScene) toast(`Scene action ${action} executed`)
  else toast(`${action} → ${ids.length} asset(s)`)
}

function confirmAction(action, ids) {
  confirmPending = { action, ids }
  el.dialogTitle.textContent = `Confirm ${action}`
  el.dialogText.textContent = `Execute ${action} on ${ids.length} asset(s)? This is a critical/high-risk action.`
  el.dialog.classList.add('open')
}

function closeConfirm() {
  el.dialog.classList.remove('open')
  confirmPending = null
}

function confirmOk() {
  if (!confirmPending) return
  execute(confirmPending.action, confirmPending.ids)
  toast(`${confirmPending.action} confirmed`)
  closeConfirm()
}

// ---- drawers toggle --------------------------------------------------
function toggleLog() {
  const open = el.logdrawer.classList.toggle('open')
  if (open) renderLog()
}
function toggleAlarms() {
  const open = el.alarmdrawer.classList.toggle('open')
  if (open) renderAlarms()
}

// ---- native inversion (SecureGuardNative → DOM) -------------------------
function nativeError(msg) {
  toast('native: ' + msg, 'err')
}

function startOf(fn) {
  return fn
}

function installHud() {
  el = {
    topbar: $('topbar'),
    eventfeed: $('eventfeed'),
    assetlist: $('assetlist'),
    assetsearch: $('assetsearch'),
    actiondock: $('actiondock'),
    srcindicator: $('srcindicator'),
    logdrawer: $('logdrawer'),
    alarmdrawer: $('alarmdrawer'),
    palette: $('palette'),
    paletteInput: $('paletteInput'),
    paletteResults: $('paletteResults'),
    dialog: $('dialog'),
    dialogTitle: $('dialogTitle'),
    dialogText: $('dialogText')
  }

  el.assetlist.addEventListener('click', (e) => {
    const item = e.target.closest('.asset-item')
    if (!item) return
    selectAsset(item.dataset.id, e.metaKey || e.ctrlKey)
    renderAssetList()
    renderDock()
  })

  el.assetsearch.addEventListener('input', (e) => {
    searchQ = e.target.value
    renderAssetList()
  })

  el.actiondock.addEventListener('click', (e) => {
    const node = e.target.closest('[data-action]')
    if (node) {
      runAction(node.dataset.action)
      return
    }
    if (e.target.classList.contains('favtoggle')) {
      favFilter = !favFilter
      renderDock()
    }
  })

  document.addEventListener('keydown', (e) => {
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault()
      if (el.palette.classList.contains('open')) closePalette()
      else openPalette()
    } else if (e.key === 'Escape') {
      if (el.palette.classList.contains('open')) closePalette()
      else if (el.dialog.classList.contains('open')) closeConfirm()
    }
  })

  el.paletteInput.addEventListener('input', (e) => updatePalette(e.target.value))
  el.paletteInput.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      paletteIndex = Math.min(paletteItems.length - 1, paletteIndex + 1)
      highlightPalette()
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      paletteIndex = Math.max(0, paletteIndex - 1)
      highlightPalette()
    } else if (e.key === 'Enter') {
      e.preventDefault()
      paletteSelect(paletteIndex)
    }
  })
  el.paletteResults.addEventListener('click', (e) => {
    const cmd = e.target.closest('.cmd')
    if (cmd) {
      const idx = Array.from(el.paletteResults.children).indexOf(cmd)
      paletteSelect(idx)
    }
  })

  $('btn-log').addEventListener('click', toggleLog)
  $('btn-alarms').addEventListener('click', toggleAlarms)
  $('btn-cmd').addEventListener('click', openPalette)
  $('dialogCancel').addEventListener('click', closeConfirm)
  $('dialogOk').addEventListener('click', confirmOk)

  // Re-render on state changes.
  on('snapshot', () => {
    renderMetrics()
    renderAssetList()
    renderDock()
  })
  on('feed', renderFeed)
  on('selection', renderAssetList)
  on('log', () => {
    if (el.logdrawer.classList.contains('open')) renderLog()
  })
  on('bridge-error', nativeError)

  renderMetrics()
  renderAssetList()
  renderDock()
  renderFeed()
}

function highlightPalette() {
  Array.from(el.paletteResults.children).forEach((node, i) => {
    node.classList.toggle('active', i === paletteIndex)
  })
}

export { installHud, toast, openPalette, renderMetrics, renderAssetList, renderDock, renderFeed, renderLog, renderAlarms, toggleLog, toggleAlarms }
