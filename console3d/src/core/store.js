/**
 * Minimalistischer, abhängigkeitsfreier State-Container mit Event-Bus.
 * Bewusst klein gehalten: kein Framework, keine Build-Magie – dadurch läuft
 * die Konsole identisch im Browser und im Android-WebView.
 */

const listeners = new Map()

function emit (event, payload) {
  const set = listeners.get(event)
  if (set) for (const fn of [...set]) fn(payload, state)
  if (event !== '*') {
    const all = listeners.get('*')
    if (all) for (const fn of [...all]) fn({ event, payload }, state)
  }
}

export function on (event, fn) {
  if (!listeners.has(event)) listeners.set(event, new Set())
  listeners.get(event).add(fn)
  return () => listeners.get(event)?.delete(fn)
}

export const state = {
  /** 'simulation' | 'backend' */
  source: 'simulation',
  connected: false,
  latencyMs: null,

  agent: {
    running: true,
    startedAt: Date.now(),
    cycle: 0,
    lastRunAt: null,
    intervalSec: 30
  },

  assets: [],
  detections: [],     // jüngste zuerst, max. 400
  alerts: [],         // jüngste zuerst
  commands: [],       // Befehlsprotokoll, jüngste zuerst
  events: [],         // Live-Feed, jüngste zuerst

  queue: 0,           // Offline-Warteschlange
  selection: new Set(),
  favorites: new Set(loadFavorites()),

  filters: { query: '', status: 'ALL' },
  category: 'SIGNAL',
  note: '',

  view: 'orbit',      // orbit | top | tactical
  overlays: { geofence: false, heatmap: false },
  feedPaused: false,
  busyAction: null
}

/* ------------------------------------------------------------------ */
/* Persistenz (Favoriten)                                             */
/* ------------------------------------------------------------------ */

function loadFavorites () {
  try {
    return JSON.parse(localStorage.getItem('sg.favorites') || '["ALARM","POSITION"]')
  } catch { return ['ALARM', 'POSITION'] }
}

function saveFavorites () {
  try {
    localStorage.setItem('sg.favorites', JSON.stringify([...state.favorites]))
  } catch { /* Speicher im WebView evtl. gesperrt – unkritisch */ }
}

/* ------------------------------------------------------------------ */
/* Mutationen                                                          */
/* ------------------------------------------------------------------ */

let seq = 1
export const nextId = () => `${Date.now().toString(36)}-${seq++}`

export const actions = {
  setSource (source, connected) {
    state.source = source
    state.connected = !!connected
    emit('source')
  },

  setLatency (ms) {
    state.latencyMs = ms
    emit('agent')
  },

  setAssets (assets) {
    state.assets = assets
    // Auswahl auf existierende Assets beschränken
    const ids = new Set(assets.map((a) => a.id))
    for (const id of [...state.selection]) if (!ids.has(id)) state.selection.delete(id)
    if (state.selection.size === 0 && assets.length) state.selection.add(assets[0].id)
    emit('assets')
    emit('selection')
  },

  patchAsset (id, patch) {
    const asset = state.assets.find((a) => a.id === id)
    if (!asset) return
    Object.assign(asset, patch)
    emit('assets')
  },

  setAgent (patch) {
    Object.assign(state.agent, patch)
    emit('agent')
  },

  toggleAgent () {
    const running = !state.agent.running
    state.agent.running = running
    state.agent.startedAt = running ? Date.now() : null
    emit('agent')
    actions.pushEvent(running ? 'Agent gestartet' : 'Agent gestoppt', running ? '#00e676' : '#ff4d6a')
  },

  addDetection (detection) {
    state.detections.unshift(detection)
    if (state.detections.length > 400) state.detections.length = 400
    emit('detection', detection)
  },

  addAlert (alert) {
    state.alerts.unshift(alert)
    if (state.alerts.length > 200) state.alerts.length = 200
    emit('alerts', alert)
  },

  ackAlert (id) {
    const alert = state.alerts.find((a) => a.id === id)
    if (alert) alert.acknowledged = true
    emit('alerts')
  },

  ackAllAlerts () {
    state.alerts.forEach((a) => { a.acknowledged = true })
    emit('alerts')
  },

  addCommand (entry) {
    state.commands.unshift(entry)
    if (state.commands.length > 300) state.commands.length = 300
    emit('commands', entry)
  },

  updateCommand (id, patch) {
    const cmd = state.commands.find((c) => c.id === id)
    if (cmd) Object.assign(cmd, patch)
    emit('commands')
  },

  clearCommands () {
    state.commands = []
    emit('commands')
  },

  setQueue (n) {
    state.queue = Math.max(0, n)
    emit('commands')
  },

  pushEvent (text, color = '#93a7bd', tag = 'SYS') {
    if (state.feedPaused) return
    state.events.unshift({ id: nextId(), ts: Date.now(), text, color, tag })
    if (state.events.length > 60) state.events.length = 60
    emit('events')
  },

  /* --------------- Auswahl --------------- */

  selectOnly (id) {
    state.selection = new Set([id])
    emit('selection')
  },

  toggleSelection (id) {
    if (state.selection.has(id)) state.selection.delete(id)
    else state.selection.add(id)
    emit('selection')
  },

  selectAllVisible (ids) {
    state.selection = new Set(ids)
    emit('selection')
  },

  clearSelection () {
    state.selection = new Set()
    emit('selection')
  },

  /* --------------- UI-Zustand --------------- */

  setFilter (patch) {
    Object.assign(state.filters, patch)
    emit('assets')
  },

  setCategory (id) {
    state.category = id
    emit('category')
  },

  setNote (text) {
    state.note = text
    emit('note')
  },

  toggleFavorite (id) {
    if (state.favorites.has(id)) state.favorites.delete(id)
    else state.favorites.add(id)
    saveFavorites()
    emit('category')
  },

  setView (view) {
    state.view = view
    emit('view')
  },

  toggleOverlay (key) {
    state.overlays[key] = !state.overlays[key]
    emit('overlays')
  },

  setBusy (actionId) {
    state.busyAction = actionId
    emit('busy')
  },

  toggleFeedPause () {
    state.feedPaused = !state.feedPaused
    emit('events')
  }
}

/* ------------------------------------------------------------------ */
/* Abgeleitete Werte                                                   */
/* ------------------------------------------------------------------ */

export const selectors = {
  targets: () => state.assets.filter((a) => state.selection.has(a.id)),

  primary: () => state.assets.find((a) => state.selection.has(a.id)) || null,

  visibleAssets () {
    const q = state.filters.query.trim().toLowerCase()
    const st = state.filters.status
    return state.assets
      .filter((a) => {
        const hit = !q ||
          a.name.toLowerCase().includes(q) ||
          a.shortName.toLowerCase().includes(q) ||
          a.mac.toLowerCase().includes(q) ||
          a.id.toLowerCase().includes(q)
        return hit && (st === 'ALL' || a.status === st)
      })
      .sort((a, b) => a.shortName.localeCompare(b.shortName))
  },

  counts () {
    const c = { total: state.assets.length, ONLINE: 0, OFFLINE: 0, MAINTENANCE: 0, SEARCHING: 0, UNKNOWN: 0 }
    for (const a of state.assets) c[a.status] = (c[a.status] || 0) + 1
    return c
  },

  unacknowledgedAlerts: () => state.alerts.filter((a) => !a.acknowledged),

  /** Detektionen je Kanal in den letzten [windowMs]. */
  channelActivity (windowMs = 120000) {
    const since = Date.now() - windowMs
    const map = {}
    for (const d of state.detections) {
      if (d.ts < since) break
      map[d.source] = (map[d.source] || 0) + 1
    }
    return map
  },

  /** Detektionen pro Minuten-Bucket – Datenbasis für die Sparklines. */
  series (buckets = 20, bucketMs = 15000) {
    const now = Date.now()
    const out = new Array(buckets).fill(0)
    for (const d of state.detections) {
      const idx = buckets - 1 - Math.floor((now - d.ts) / bucketMs)
      if (idx >= 0 && idx < buckets) out[idx]++
    }
    return out
  }
}

export { emit }
