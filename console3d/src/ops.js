// SecureGuard Ops Center — core state, data source priority, bridge glue.
// This module contains NO Three.js code and NO business logic beyond
// presentation: it turns a native snapshot into scene/HUD state and (in the
// absence of native/backend data) a clearly-labelled simulation so the UI is
// never empty. Source priority: NATIVE bridge → backend → SIMULATION.

export const CHANNELS = [
  { id: 'BLE', label: 'BLE', color: '#00d4ff' },
  { id: 'WIFI', label: 'WiFi', color: '#ffc400' },
  { id: 'GPS', label: 'GPS', color: '#00e676' },
  { id: 'LORA', label: 'LoRa', color: '#9c6bff' },
  { id: 'NFC', label: 'NFC', color: '#ff9100' },
  { id: 'USB', label: 'USB', color: '#5c748f' },
  { id: 'OPTICAL', label: 'Optical', color: '#ff5ce1' },
  { id: 'URBAN', label: 'Urban', color: '#ffe14d' },
  { id: 'CROWD', label: 'Crowd', color: '#7ef2d9' },
  { id: 'SATELLITE', label: 'Satellite', color: '#c0c4ff' },
  { id: 'MQTT', label: 'MQTT', color: '#4df0a8' },
  { id: 'WEBSOCKET', label: 'WebSocket', color: '#f07b4d' }
]

const NATIVE_SCENE_ACTIONS = ['SWEEP', 'FOCUS', 'GEOFENCE', 'FORCE', 'ACTION']
const DEVICE_ACTIONS = ['ALARM', 'LIGHT', 'MOTOR_OFF', 'BATTERY', 'MESSAGE', 'POSITION', 'RESTART', 'TELEMETRY']

export function isSceneAction(action) {
  return NATIVE_SCENE_ACTIONS.includes(String(action || '').toUpperCase())
}

// --- tiny event bus ------------------------------------------------
const listeners = {}
export function on(event, fn) {
  ;(listeners[event] || (listeners[event] = [])).push(fn)
  return () => {
    listeners[event] = (listeners[event] || []).filter((f) => f !== fn)
  }
}
export function emit(event, payload) {
  ;(listeners[event] || []).forEach((fn) => {
    try {
      fn(payload)
    } catch (e) {
      console.error('[ops] listener error', e)
    }
  })
}

// --- state ---------------------------------------------------------
const state = {
  source: 'SIMULATION',
  agent: { id: 'agent', running: false, cycle: 0, online: false, uptimeMs: 0 },
  assets: [],
  alarms: [],
  detections: [],
  queue: [],
  channelLoad: {}, // channelId -> normalized usage 0..1
  feed: [], // live event feed (ring buffer)
  log: [],
  selected: new Set(),
  favs: new Set(),
  history: [],
  updatedAt: null
}

const FEED_MAX = 80
const LOG_MAX = 200

function feed(pushElem) {
  state.feed.push(pushElem)
  if (state.feed.length > FEED_MAX) state.feed.splice(0, state.feed.length - FEED_MAX)
}

function logPush(line) {
  state.log.push(line)
  if (state.log.length > LOG_MAX) state.log.splice(0, state.log.length - LOG_MAX)
  emit('log', line)
}

export function getState() {
  return state
}

export function nowStamp() {
  return new Date().toLocaleTimeString('en-GB', { hour12: false })
}

// --- simulation fallback -------------------------------------------
let simTimer = null

function startSimulation() {
  if (simTimer) return
  state.source = 'SIMULATION'
  state.agent = { id: 'agent', running: true, cycle: 3, online: true, uptimeMs: 912000 }
  const assets = [
    ['SG-001', 'Roller #1', 'ONLINE', -45, 78, 52.52, 13.405],
    ['SG-002', 'Fahrrad #2', 'MAINTENANCE', -60, 54, 52.498, 13.404],
    ['SG-003', 'Schlüssel #3', 'OFFLINE', -90, 12, null, null],
    ['SG-004', 'Tablet #4', 'ONLINE', -55, 92, 52.5219, 13.4132],
    ['SG-005', 'Smartphone #5', 'SEARCHING', -72, 41, 52.538, 13.42]
  ]
  state.assets = assets.map((a, i) => ({
    id: a[0],
    mac: 'AA:BB:CC:DD:EE:0' + (i + 1),
    name: a[1],
    shortName: a[1],
    status: a[2],
    rssi: a[3],
    batteryLevel: a[4],
    latitude: a[5],
    longitude: a[6],
    lastSeen: Date.now() - i * 40000,
    simulated: true
  }))
  state.alarms = [
    { id: 101, assetId: 'SG-003', severity: 'CRITICAL', type: 'GEOFENCE', message: 'Asset outside geofence', acknowledged: false, timestamp: Date.now() - 300000 }
  ]
  state.channelLoad = { BLE: 0.62, WIFI: 0.35, GPS: 0.21, LORA: 0.13, NFC: 0.04, USB: 0.0 }
  updateDerived()
  simTimer = setInterval(() => {
    // Pulsing the channel utilisation keeps sparklines/tiles alive — clearly
    // labelled SIMULATION, never presented as real device data.
    const scale = 0.05
    Object.keys(state.channelLoad).forEach((k) => {
      state.channelLoad[k] = Math.min(1, Math.max(0.02, state.channelLoad[k] + (Math.random() - 0.5) * scale))
    })
    state.agent.cycle += 1
    state.agent.uptimeMs = Date.now() - 1600000000
    if (Math.random() < 0.18) {
      feed({ t: nowStamp(), text: `SIM · agent cycle #${state.agent.cycle} (demo fallback)`, kind: 'sim' })
      emit('feed')
    }
    state.updatedAt = Date.now()
    emit('snapshot', snapshot())
  }, 3000)
}

function stopSimulation() {
  if (simTimer) {
    clearInterval(simTimer)
    simTimer = null
  }
}

// --- ingestion ------------------------------------------------------
export function ingestRaw(json) {
  let data
  try {
    data = typeof json === 'string' ? JSON.parse(json) : json
  } catch (e) {
    logPush('[err] ingest: invalid JSON')
    return snapshot()
  }
  return ingest(data)
}

export function ingest(data) {
  stopSimulation()
  const s = data && typeof data === 'object' ? data : {}
  state.source = s.source || 'NATIVE'
  state.agent = { simulated: false, ...(s.agent || {}) }
  state.assets = Array.isArray(s.assets) ? s.assets : []
  state.alarms = Array.isArray(s.alarms) ? s.alarms : []
  state.detections = Array.isArray(s.detections) ? s.detections : []
  state.queue = Array.isArray(s.queue) ? s.queue : []
  state.channelLoad = s.channelLoad && typeof s.channelLoad === 'object' ? s.channelLoad : {}
  state.updatedAt = Date.now()
  updateDerived()
  emit('snapshot', snapshot())
  feed({ t: nowStamp(), text: `SNAPSHOT · ${state.assets.length} assets · source ${state.source}`, kind: 'sys' })
  emit('feed')
  return snapshot()
}

function updateDerived() {
  const ch = {}
  CHANNELS.forEach((c) => (ch[c.id] = 0))
  const counts = {}
  ;(state.detections || []).forEach((d) => {
    const src = String(d.sourceType || d.source || '').toUpperCase()
    if (ch[src] !== undefined) ch[src] += 1
    counts[src] = (counts[src] || 0) + 1
  })
  // Normalise to a max of 1 for the pylons; if no detections at all, keep 0.
  const max = Math.max(1, ...Object.values(counts))
  Object.keys(counts).forEach((k) => {
    if (ch[k] !== undefined) ch[k] = Math.min(1, counts[k] / max)
  })
  state.channelLoad = ch
}

// --- actions --------------------------------------------------------
export function selectAsset(id, additive) {
  if (!additive) state.selected.clear()
  if (state.selected.has(id)) state.selected.delete(id)
  else state.selected.add(id)
  emit('selection', Array.from(state.selected))
  return Array.from(state.selected)
}

export function selectAll(on) {
  state.selected.clear()
  if (on) state.assets.forEach((a) => state.selected.add(a.id))
  emit('selection', Array.from(state.selected))
  return Array.from(state.selected)
}

export function execute(action, ids) {
  const idsArr = ids || Array.from(state.selected)
  const scene = isSceneAction(action)
  const rec = { action: String(action), ids: idsArr.slice(), scene, ok: null, at: Date.now(), t: nowStamp() }
  if (!scene) {
    // Native bridge forwards real commands to Kotlin. In simulation mode we
    // must NOT fake a device success — the action is queued (offline semantics).
    queueLocal(action, idsArr)
    rec.ok = state.source === 'SIMULATION' ? 'queued' : 'pending'
  }
  state.history.push(rec)
  if (state.history.length > 60) state.history.splice(0, state.history.length - 60)
  logPush(`[${rec.t}] ${action} ${scene ? '(scene)' : '→ assets'} [${idsArr.join(',') || 'none'}]`)
  emit('action', { action, assetIds: idsArr.slice(), scene, via: 'SecureGuardOps' })
  return rec
}

function queueLocal(action, ids) {
  const kind = NATIVE_SCENE_ACTIONS.includes(action) ? 'scene' : 'device'
  ;(ids || []).forEach((id) => {
    state.queue.unshift({ id: 'q' + Date.now() + Math.random().toString(36).slice(2, 6), action, assetId: id, status: 'queued', kind })
  })
  emit('queue', state.queue)
  feed({ t: nowStamp(), text: `QUEUE · ${action} queued for ${id} (offline)`, kind: 'queue' })
}

export function queueEvent(e) {
  const q = { id: 'q' + Date.now() + Math.random().toString(36).slice(2, 6), action: e.action || '?', assetId: e.asset || '', status: e.status || 'queued', kind: e.kind || 'device', message: e.message || '' }
  state.queue.unshift(q)
  if (state.queue.length > 40) state.queue.length = 40
  emit('queue', state.queue)
  feed({ t: nowStamp(), text: `QUEUE · ${q.action} ${q.status}${q.message ? ' — ' + q.message : ''}`, kind: 'queue' })
  emit('feed')
}

export function event(e) {
  const ev = { type: e.type || 'event', data: e.data || {}, t: nowStamp() }
  emit('native', ev)
  const txt = typeof ev.data === 'object' ? JSON.stringify(ev.data).slice(0, 120) : String(ev.data)
  feed({ t: ev.t, text: `${ev.type} · ${txt}`, kind: 'sys' })
  emit('feed')
  logPush(`[${ev.t}] native.${ev.type}`)
}

export function nativeReady(payload) {
  state.bridge = 'SecureGuardNative'
  logPush('[bridge] SecureGuardNative ready')
  emit('ready', payload || {})
  return true
}

export function toggleFav(actionKey) {
  if (state.favs.has(actionKey)) state.favs.delete(actionKey)
  else state.favs.add(actionKey)
  emit('favs', Array.from(state.favs))
  return Array.from(state.favs)
}

// --- public snapshot (stable object identity for scene diffing) -----
const snap = {}
export function snapshot() {
  const alarms = state.alarms || []
  snap.source = state.source
  snap.agent = state.agent
  snap.assets = state.assets || []
  snap.alarms = alarms
  snap.detections = state.detections || []
  snap.queue = state.queue || []
  snap.channelLoad = state.channelLoad || {}
  snap.counts = {
    assets: snap.assets.length,
    online: snap.assets.filter((a) => String(a.status).toUpperCase() === 'ONLINE').length,
    alarms: alarms.filter((a) => !a.acknowledged).length,
    detections: (state.detections || []).length,
    nodes: 12,
    queue: (state.queue || []).length
  }
  snap.updatedAt = state.updatedAt
  return snap
}

export function boot() {
  // If the native bridge delivered a snapshot before main ran, apply it once.
  const pre = window.__SG_PRE_SNAPSHOT__
  if (pre) {
    try {
      ingest(pre)
      return
    } catch (e) {
      console.error('pre-snapshot failed', e)
    }
  }
  // No native/backend data yet → simulation fallback, clearly labelled.
  window.setTimeout(() => {
    if (state.source === 'SIMULATION') startSimulation()
  }, 400)
}
