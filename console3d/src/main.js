/**
 * SecureGuard · 3D Operations Center – Einstiegspunkt.
 *
 * Verbindet Datenquelle (Backend oder Simulation), 3D-Lagebild und HUD.
 * Läuft identisch im Desktop-Browser und im WebView der Android-App.
 */

import './style.css'

import { ACTIONS, ACTION_MAP, CHANNELS, STATUS } from './data/catalog.js'
import { actions, nextId, on, selectors, state } from './core/store.js'
import { api } from './core/api.js'
import { ORIGIN, Simulation } from './core/simulation.js'
import { native } from './core/native.js'
import { createWorld } from './scene/world.js'
import { createPanels } from './ui/panels.js'
import { createDock, targetSummary } from './ui/dock.js'
import { createOverlays } from './ui/overlays.js'
import { $, duration, el } from './ui/dom.js'

/* ================================================================== */
/* Initialisierung                                                     */
/* ================================================================== */

const bootStatus = $('#bootStatus')
const setBoot = (text) => { bootStatus.textContent = text }

setBoot('WebGL-Kontext wird aufgebaut …')

const world = createWorld($('#scene'), $('#labels'), {
  origin: ORIGIN,
  onSelect: (assetId, additive) => {
    if (!assetId) return actions.clearSelection()
    if (additive) actions.toggleSelection(assetId)
    else actions.selectOnly(assetId)
  }
})

const panels = createPanels({ onFocusAsset: (id) => world.focus(id) })
const overlays = createOverlays({ onPaletteCommand: () => overlays.openPalette(buildPaletteItems()) })
const dock = createDock({
  onExecute: (action) => executeAction(action),
  onFlushQueue: () => flushQueue()
})

// Alarm-Quittierung an die App weiterreichen, wenn die Brücke aktiv ist.
const ackAlertLocal = actions.ackAlert
actions.ackAlert = (id) => {
  if (state.source === 'native' && native.available) native.acknowledgeAlert(id)
  ackAlertLocal(id)
}
const ackAllLocal = actions.ackAllAlerts
actions.ackAllAlerts = () => {
  if (state.source === 'native' && native.available) {
    state.alerts.filter((a) => !a.acknowledged).forEach((a) => native.acknowledgeAlert(a.id))
  }
  ackAllLocal()
}

const simulation = new Simulation()
let backendSocket = null
let backendTimer = null

/* ================================================================== */
/* Datenquelle                                                         */
/* ================================================================== */

let nativeTimer = null

/** Höchste Priorität: echte App-Daten über die WebView-Brücke. */
function startNative () {
  if (!native.available) return false
  stopSources()
  actions.setSource('native', true)
  actions.pushEvent('Mit SecureGuard-App verbunden – Live-Daten aus der App', '#00e676', 'APP')

  const sync = () => {
    const snap = native.snapshot()
    if (!snap) return
    if (Array.isArray(snap.assets)) actions.setAssets(snap.assets)
    if (snap.agent) actions.setAgent(snap.agent)
    if (typeof snap.queue === 'number') actions.setQueue(snap.queue)
    if (Array.isArray(snap.alerts)) {
      for (const alert of snap.alerts) {
        if (!state.alerts.some((a) => a.id === alert.id)) actions.addAlert(alert)
      }
    }
    if (Array.isArray(snap.detections)) {
      for (const d of snap.detections) {
        if (!state.detections.some((x) => x.id === d.id)) actions.addDetection(d)
      }
    }
  }

  sync()
  nativeTimer = setInterval(sync, 1500)
  return true
}

async function startBackend () {
  stopSources()
  const probe = await api.probe()
  if (!probe.ok) return false

  actions.setSource('backend', true)
  actions.setLatency(probe.latency)
  actions.pushEvent('Backend verbunden – Live-Daten aktiv', '#00e676', 'NET')

  const poll = async () => {
    try {
      const [assets, detections, alerts] = await Promise.all([
        api.assets(), api.detections(80), api.alerts()
      ])
      if (assets.length) actions.setAssets(assets)
      for (const d of detections.slice(0, 5)) actions.addDetection(d)
      if (alerts.length) {
        for (const alert of alerts.slice(0, 20)) {
          if (!state.alerts.some((a) => a.id === alert.id)) actions.addAlert(alert)
        }
      }
    } catch {
      actions.pushEvent('Backend antwortet nicht – Wechsel auf Simulation', '#ffc400', 'NET')
      startSimulation()
    }
  }

  await poll()
  backendTimer = setInterval(poll, 4000)
  backendSocket = api.connectSocket((message) => {
    actions.pushEvent(String(message.type || 'Ereignis'), '#00d4ff', 'WS')
  })
  return true
}

function startSimulation () {
  stopSources()
  simulation.start()
}

function stopSources () {
  simulation.stop()
  if (nativeTimer) clearInterval(nativeTimer)
  nativeTimer = null
  if (backendTimer) clearInterval(backendTimer)
  backendTimer = null
  if (backendSocket) { try { backendSocket.close() } catch { /* egal */ } }
  backendSocket = null
}

/** Zustelladapter – hinter dieser Funktion liegt Backend oder Simulation. */
async function dispatch (action, asset) {
  if (state.source === 'native' && native.available) {
    const note = action.acceptsNote ? state.note.slice(0, 120) : ''
    return native.execute(asset.id, action.wire, note)
  }
  if (state.source === 'backend' && api.available) {
    try {
      const payload = action.acceptsNote && state.note ? state.note.slice(0, 120) : null
      const res = await api.execute(asset.id, action.wire, payload)
      return { state: res?.status === 'queued' ? 'queued' : 'delivered', detail: 'Backend/MQTT' }
    } catch (error) {
      return { state: 'blocked', detail: error.message || 'Backend-Fehler' }
    }
  }
  return simulation.dispatch(action, asset)
}

/* ================================================================== */
/* Aktionen                                                            */
/* ================================================================== */

async function executeAction (action) {
  if (action.local) return runLocalAction(action)

  const targets = selectors.targets()
  if (!targets.length) {
    overlays.toast('Kein Ziel ausgewählt – bitte Asset wählen', 'error')
    return
  }

  if (action.risk === 'critical') {
    const ok = await overlays.confirm({
      title: action.confirmTitle || `${action.title} bestätigen`,
      text: action.confirmText || 'Diese Aktion wirkt unmittelbar auf das Gerät.',
      targets: targetSummary(),
      confirmLabel: action.title,
      icon: action.icon
    })
    if (!ok) {
      overlays.toast('Abgebrochen')
      return
    }
  }

  actions.setBusy(action.id)
  let delivered = 0; let queued = 0; let blocked = 0

  for (const target of targets) {
    const id = nextId()
    actions.addCommand({
      id, ts: Date.now(), action: action.id, title: action.title,
      assetId: target.id, assetName: target.shortName, state: 'running', detail: ''
    })

    let result
    try {
      result = await dispatch(action, target)
    } catch (error) {
      result = { state: 'blocked', detail: error.message || 'Fehler' }
    }

    actions.updateCommand(id, { state: result.state, detail: result.detail })
    if (result.state === 'delivered') {
      delivered++
      world.pulseDetection(target.id, 'MQTT')
      actions.pushEvent(`${action.title} → ${target.shortName}`, action.color, 'CMD')
    } else if (result.state === 'queued') {
      queued++
      actions.pushEvent(`${action.title} für ${target.shortName} eingereiht`, '#ffc400', 'QUEUE')
    } else {
      blocked++
      actions.pushEvent(`${action.title} an ${target.shortName} blockiert`, '#ff4d6a', 'FEHLER')
    }
  }

  actions.setBusy(null)

  const parts = []
  if (delivered) parts.push(`${delivered} zugestellt`)
  if (queued) parts.push(`${queued} in Warteschlange`)
  if (blocked) parts.push(`${blocked} blockiert`)
  overlays.toast(`${action.title}: ${parts.join(' · ') || 'keine Zustellung'}`,
    delivered ? 'ok' : 'error')

  if (action.acceptsNote) { actions.setNote(''); dock.syncNote() }
}

/** Aktionen, die nur das Lagebild betreffen (keine Funkbefehle). */
function runLocalAction (action) {
  switch (action.id) {
    case 'SWEEP': {
      world.runSweep()
      overlays.toast('Radar-Sweep über alle Kanäle gestartet')
      actions.pushEvent('Manueller Suchlauf über alle Kanäle', '#00d4ff', 'AGENT')
      const online = state.assets.filter((a) => a.status !== 'OFFLINE')
      online.forEach((asset, i) => {
        setTimeout(() => {
          const channel = CHANNELS[i % CHANNELS.length]
          actions.addDetection({
            id: nextId(), assetId: asset.id, assetName: asset.shortName,
            source: channel.id, rssi: asset.rssi, lat: asset.lat, lon: asset.lon, ts: Date.now()
          })
        }, 120 * i)
      })
      break
    }
    case 'FOCUS': {
      const primary = selectors.primary()
      if (!primary) return overlays.toast('Kein Ziel zum Anfliegen', 'error')
      world.focus(primary.id)
      overlays.toast(`Kamera auf ${primary.shortName}`)
      break
    }
    case 'GEOFENCE':
      actions.toggleOverlay('geofence')
      overlays.toast(`Geofence ${state.overlays.geofence ? 'eingeblendet' : 'ausgeblendet'}`)
      break
    case 'HEATMAP':
      actions.toggleOverlay('heatmap')
      overlays.toast(`Heatmap ${state.overlays.heatmap ? 'aktiv' : 'aus'}`)
      break
    default:
      overlays.toast('Unbekannte Aktion', 'error')
  }
}

async function flushQueue () {
  if (!state.queue) return overlays.toast('Warteschlange ist leer')
  let sent = 0
  if (state.source === 'native' && native.available) {
    const res = await native.flushQueue()
    sent = Number(res?.detail) || 0
  } else if (state.source !== 'backend') {
    sent = await simulation.flushQueue()
  }
  overlays.toast(sent ? `${sent} Befehl(e) zugestellt` : 'Kein Befehl zustellbar', sent ? 'ok' : 'error')
  actions.pushEvent(`Offline-Queue: ${sent} zugestellt`, sent ? '#00e676' : '#ff4d6a', 'QUEUE')
}

/* ================================================================== */
/* Befehlspalette                                                      */
/* ================================================================== */

function buildPaletteItems () {
  const items = []

  for (const action of ACTIONS) {
    items.push({
      icon: action.icon,
      label: action.title,
      hint: action.local ? 'Lagebild' : `Befehl · ${action.category.toLowerCase()}`,
      run: () => executeAction(action)
    })
  }

  for (const asset of state.assets) {
    items.push({
      icon: '📦',
      label: `Ziel: ${asset.shortName}`,
      hint: `${STATUS[asset.status]?.label} · ${asset.mac}`,
      run: () => { actions.selectOnly(asset.id); world.focus(asset.id) }
    })
  }

  items.push(
    { icon: '🎥', label: 'Ansicht: Orbit', hint: 'V', run: () => setView('orbit') },
    { icon: '🗺️', label: 'Ansicht: Draufsicht', hint: 'V', run: () => setView('top') },
    { icon: '🎯', label: 'Ansicht: Taktisch', hint: 'V', run: () => setView('tactical') },
    { icon: '📋', label: 'Befehlsprotokoll öffnen', hint: 'L', run: () => overlays.toggleLog(true) },
    { icon: '🔔', label: 'Alarme öffnen', hint: 'A', run: () => overlays.toggleAlerts(true) },
    { icon: '✅', label: 'Alle Alarme bestätigen', hint: '', run: () => actions.ackAllAlerts() },
    {
      icon: '⏯️',
      label: state.agent.running ? 'Agent stoppen' : 'Agent starten',
      hint: 'Leertaste',
      run: () => actions.toggleAgent()
    },
    { icon: '🔌', label: 'Datenquelle umschalten', hint: 'D', run: () => toggleSource() },
    { icon: '🧹', label: 'Auswahl aufheben', hint: 'Esc', run: () => actions.clearSelection() }
  )

  return items
}

/* ================================================================== */
/* Kopfzeile & Ansichten                                               */
/* ================================================================== */

const VIEWS = ['orbit', 'top', 'tactical']

function setView (view) {
  actions.setView(view)
  world.setView(view)
  overlays.toast(`Ansicht: ${view === 'orbit' ? 'Orbit' : view === 'top' ? 'Draufsicht' : 'Taktisch'}`)
}

async function toggleSource () {
  if (state.source === 'native') {
    overlays.toast('App-Daten sind aktiv – Quelle ist fest verbunden')
    return
  }
  if (state.source === 'backend') {
    actions.setSource('simulation', false)
    startSimulation()
    overlays.toast('Simulation aktiv')
  } else {
    overlays.toast('Suche Backend …')
    const ok = await startBackend()
    if (!ok) {
      startSimulation()
      overlays.toast('Kein Backend erreichbar – Simulation läuft weiter', 'error')
    }
  }
  renderTopBar()
}

function renderTopBar () {
  const running = state.agent.running
  const toggle = $('#agentToggle')
  toggle.classList.toggle('is-off', !running)
  $('#agentLabel').textContent = running ? 'Agent aktiv' : 'Agent gestoppt'
  $('#uptimeValue').textContent = running && state.agent.startedAt
    ? duration(Date.now() - state.agent.startedAt) : '–'
  $('#cycleValue').textContent = state.agent.cycle
  $('#latencyValue').textContent = state.latencyMs == null ? '–' : `${state.latencyMs} ms`
  $('#sourceLabel').textContent = state.source === 'native'
    ? 'App-Daten live'
    : state.source === 'backend' ? 'Backend live' : 'Simulation'
  $('#btnSource').classList.toggle('is-active', state.source !== 'simulation')
}

/* ================================================================== */
/* Render-Planer                                                       */
/* ================================================================== */

const dirty = new Set()
const markDirty = (...keys) => keys.forEach((k) => dirty.add(k))

function flushRenders () {
  if (!dirty.size) return
  if (dirty.has('assets')) {
    world.syncAssets(state.assets)
    world.setSelection([...state.selection])
    panels.renderAssets()
    panels.renderChips()
    panels.renderKpis()
    dock.renderHeader()
    dock.renderGrid()
    if (state.overlays.geofence) world.setGeofence(true)
  }
  if (dirty.has('selection')) {
    world.setSelection([...state.selection])
    panels.renderAssets()
    dock.renderAll()
    if (state.overlays.geofence) world.setGeofence(true)
  }
  if (dirty.has('detections')) { panels.renderChannels(); panels.renderKpis() }
  if (dirty.has('alerts')) { overlays.renderAlerts(); panels.renderKpis() }
  if (dirty.has('commands')) { overlays.renderLog(); dock.renderHeader() }
  if (dirty.has('events')) panels.renderFeed()
  if (dirty.has('dock')) dock.renderAll()
  if (dirty.has('top')) renderTopBar()
  dirty.clear()
}

/* ================================================================== */
/* Store-Anbindung                                                     */
/* ================================================================== */

on('assets', () => markDirty('assets'))
on('selection', () => markDirty('selection'))
on('alerts', () => markDirty('alerts'))
on('commands', () => markDirty('commands'))
on('events', () => markDirty('events'))
on('category', () => markDirty('dock'))
on('busy', () => markDirty('dock'))
on('note', () => { /* Eingabe steuert sich selbst */ })
on('source', () => markDirty('top'))

on('agent', () => {
  world.setAgentRunning(state.agent.running)
  markDirty('top')
})

on('detection', (detection) => {
  world.pulseDetection(detection.assetId, detection.source)
  markDirty('detections')
  if (state.overlays.heatmap) world.setHeatmap(true, state.detections)
})

on('overlays', () => {
  world.setGeofence(state.overlays.geofence)
  world.setHeatmap(state.overlays.heatmap, state.detections)
  markDirty('dock')
})

on('view', () => markDirty('top'))

/* ================================================================== */
/* Eingaben                                                            */
/* ================================================================== */

$('#agentToggle').addEventListener('click', () => {
  if (state.source === 'native' && native.available) native.toggleAgent()
  actions.toggleAgent()
})
$('#btnView').addEventListener('click', () => {
  const next = VIEWS[(VIEWS.indexOf(state.view) + 1) % VIEWS.length]
  setView(next)
})
$('#btnSource').addEventListener('click', () => toggleSource())

window.addEventListener('keydown', (event) => {
  const typing = ['INPUT', 'TEXTAREA'].includes(event.target.tagName)
  const meta = event.ctrlKey || event.metaKey

  if (meta && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    overlays.openPalette(buildPaletteItems())
    return
  }
  if (event.key === 'Escape') {
    if (overlays.isPaletteOpen()) return overlays.closePalette()
    actions.clearSelection()
    return
  }
  if (typing || overlays.isPaletteOpen() || overlays.isModalOpen()) return

  const key = event.key.toLowerCase()
  if (event.key === ' ') { event.preventDefault(); actions.toggleAgent(); return }
  if (key === 'l') return overlays.toggleLog()
  if (key === 'a') return overlays.toggleAlerts()
  if (key === 'v') return setView(VIEWS[(VIEWS.indexOf(state.view) + 1) % VIEWS.length])
  if (key === 'd') return toggleSource()
  if (key === 'h') return overlays.toast('Tasten: 1–8 Befehle · R Sweep · F Fokus · G Geofence · M Heatmap · V Ansicht · L Log · A Alarme · ⌘K Palette')

  const action = ACTIONS.find((a) => a.key === key)
  if (action) {
    event.preventDefault()
    executeAction(action)
  }
})

window.addEventListener('resize', () => world.resize())

/* ================================================================== */
/* Start                                                               */
/* ================================================================== */

setBoot('Lagebild wird aufgebaut …')

;(async () => {
  if (!startNative()) {
    const ok = await startBackend()
    if (!ok) {
      actions.setSource('simulation', false)
      startSimulation()
    }
  }
  world.setAgentRunning(state.agent.running)
  world.syncAssets(state.assets)
  world.setSelection([...state.selection])
  panels.renderAll()
  dock.renderAll()
  overlays.renderAlerts()
  overlays.renderLog()
  renderTopBar()

  setBoot('Bereit')
  setTimeout(() => $('#boot').classList.add('is-done'), 350)
  setTimeout(() => $('#hint').classList.add('is-hidden'), 12000)
})()

setInterval(() => {
  renderTopBar()
  panels.renderChannels()
}, 1000)

function loop () {
  flushRenders()
  world.update()
  requestAnimationFrame(loop)
}
requestAnimationFrame(loop)

// Für Debug/QA im WebView erreichbar
window.SecureGuardOps = { state, actions, world, overlays, executeAction, ACTION_MAP }
