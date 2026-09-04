/**
 * Live-Simulation des SecureGuard-Feldbetriebs.
 *
 * Wird verwendet, solange kein FastAPI-Backend erreichbar ist. Die Daten sind
 * bewusst realistisch (Bewegung, Akkuverlauf, Kanal-Trefferquoten,
 * Alarmauslösung), damit Bedienung und Lagebild ohne Feldhardware
 * vollständig bewertbar sind.
 */

import { CHANNELS, STATUS } from '../data/catalog.js'
import { actions, nextId, state } from './store.js'

/** Referenzpunkt: Duisburg Innenhafen. */
export const ORIGIN = { lat: 51.4344, lon: 6.7623 }

const FLEET = [
  { shortName: 'SCOOT-01', name: 'E-Scooter Innenhafen',      kind: 'scooter' },
  { shortName: 'SCOOT-02', name: 'E-Scooter Hauptbahnhof',    kind: 'scooter' },
  { shortName: 'SCOOT-03', name: 'E-Scooter Rheinpark',       kind: 'scooter' },
  { shortName: 'CARGO-01', name: 'Lastenrad Logistik Nord',   kind: 'cargo' },
  { shortName: 'CARGO-02', name: 'Lastenrad Werkstatt',       kind: 'cargo' },
  { shortName: 'BIKE-01',  name: 'Dienstrad Verwaltung',      kind: 'bike' },
  { shortName: 'BIKE-02',  name: 'Dienstrad Außendienst',     kind: 'bike' },
  { shortName: 'TAG-114',  name: 'Schlüsselfinder Depot',     kind: 'tag' },
  { shortName: 'TAG-207',  name: 'Schlüsselfinder Leitstand', kind: 'tag' },
  { shortName: 'PAD-CT45', name: 'Honeywell CT45P XON',       kind: 'tablet' },
  { shortName: 'PAD-CT45B','name': 'Honeywell CT45P Ersatz',  kind: 'tablet' },
  { shortName: 'NODE-ESP', name: 'ESP32-Gateway Hafentor',    kind: 'node' }
]

const rand = (min, max) => min + Math.random() * (max - min)
const pick = (arr) => arr[Math.floor(Math.random() * arr.length)]

function mac (i) {
  const h = (n) => n.toString(16).padStart(2, '0').toUpperCase()
  return `DE:AD:${h(0xb0 + i)}:${h(40 + i * 7)}:${h(11 + i * 13)}:${h(i)}`
}

export function buildFleet () {
  return FLEET.map((tpl, i) => {
    const status = i === 4 ? 'MAINTENANCE'
      : i === 7 ? 'OFFLINE'
        : i === 10 ? 'SEARCHING'
          : 'ONLINE'
    return {
      id: `asset-${String(i + 1).padStart(3, '0')}`,
      name: tpl.name,
      shortName: tpl.shortName,
      kind: tpl.kind,
      mac: mac(i),
      status,
      rssi: status === 'OFFLINE' ? 0 : Math.round(rand(-88, -42)),
      battery: Math.round(rand(18, 99)),
      lat: ORIGIN.lat + rand(-0.010, 0.010),
      lon: ORIGIN.lon + rand(-0.016, 0.016),
      lastSeen: Date.now() - Math.round(rand(0, 900000)),
      maintenanceDue: status === 'MAINTENANCE',
      // interne Simulationsdaten
      _vx: rand(-1, 1) * 0.00002,
      _vy: rand(-1, 1) * 0.00002
    }
  })
}

export class Simulation {
  constructor () {
    this.timers = []
    this.tick = 0
  }

  start () {
    actions.setAssets(buildFleet())
    actions.setSource('simulation', false)
    actions.pushEvent('Simulationsdaten geladen – 12 Assets im Lagebild', '#00d4ff')

    this.timers.push(setInterval(() => this.stepPhysics(), 900))
    this.timers.push(setInterval(() => this.stepDetections(), 750))
    this.timers.push(setInterval(() => this.stepAgentCycle(), 4000))
    this.timers.push(setInterval(() => this.stepIncidents(), 11000))
  }

  stop () {
    this.timers.forEach(clearInterval)
    this.timers = []
  }

  /** Bewegung, Akku und Signalqualität fortschreiben. */
  stepPhysics () {
    if (!state.agent.running) return
    for (const a of state.assets) {
      if (a.status === 'OFFLINE') continue
      a._vx += rand(-0.000004, 0.000004)
      a._vy += rand(-0.000004, 0.000004)
      a._vx = Math.max(-0.00004, Math.min(0.00004, a._vx))
      a._vy = Math.max(-0.00004, Math.min(0.00004, a._vy))
      a.lon += a._vx
      a.lat += a._vy
      // sanft zum Zentrum zurückziehen
      a.lon += (ORIGIN.lon - a.lon) * 0.004
      a.lat += (ORIGIN.lat - a.lat) * 0.004
      a.rssi = Math.max(-95, Math.min(-38, a.rssi + Math.round(rand(-3, 3))))
      if (Math.random() < 0.06) a.battery = Math.max(0, a.battery - 1)
    }
    actions.patchAsset(state.assets[0]?.id, {})
  }

  /** Zufällige Detektionen über die aktiven Kanäle erzeugen. */
  stepDetections () {
    if (!state.agent.running) return
    const candidates = state.assets.filter((a) => a.status !== 'OFFLINE')
    if (!candidates.length) return
    const count = 1 + (Math.random() < 0.35 ? 1 : 0)
    for (let i = 0; i < count; i++) {
      const asset = pick(candidates)
      const channel = pick(CHANNELS)
      const detection = {
        id: nextId(),
        assetId: asset.id,
        assetName: asset.shortName,
        source: channel.id,
        rssi: asset.rssi + Math.round(rand(-6, 6)),
        lat: asset.lat,
        lon: asset.lon,
        ts: Date.now()
      }
      asset.lastSeen = detection.ts
      actions.addDetection(detection)
    }
  }

  stepAgentCycle () {
    if (!state.agent.running) return
    actions.setAgent({ cycle: state.agent.cycle + 1, lastRunAt: Date.now() })
    actions.setLatency(Math.round(rand(18, 140)))
  }

  /** Statuswechsel, Alarme und Wartungsereignisse. */
  stepIncidents () {
    if (!state.agent.running || !state.assets.length) return
    const asset = pick(state.assets)
    const roll = Math.random()

    if (roll < 0.22) {
      const next = asset.status === 'OFFLINE' ? 'SEARCHING' : 'OFFLINE'
      asset.status = next
      if (next === 'OFFLINE') asset.rssi = 0
      actions.pushEvent(
        `${asset.shortName} → ${STATUS[next].label}`,
        STATUS[next].color, 'STATUS'
      )
      if (next === 'OFFLINE') {
        actions.addAlert({
          id: nextId(), assetId: asset.id, assetName: asset.shortName,
          type: 'SECURITY', severity: 'WARNING', acknowledged: false,
          message: `${asset.shortName} über alle Kanäle nicht erreichbar`,
          ts: Date.now()
        })
      }
    } else if (roll < 0.4 && asset.battery < 30) {
      actions.addAlert({
        id: nextId(), assetId: asset.id, assetName: asset.shortName,
        type: 'LOW_BATTERY', severity: 'WARNING', acknowledged: false,
        message: `Akku ${asset.battery}% – ${asset.shortName} bald wechseln`,
        ts: Date.now()
      })
    } else if (roll < 0.5) {
      actions.addAlert({
        id: nextId(), assetId: asset.id, assetName: asset.shortName,
        type: 'GEOFENCE', severity: 'CRITICAL', acknowledged: false,
        message: `Geofence verlassen: ${asset.shortName} bewegt sich Richtung Stadtgrenze`,
        ts: Date.now()
      })
      actions.pushEvent(`Geofence-Verletzung ${asset.shortName}`, '#ff4d6a', 'ALARM')
    } else if (roll < 0.62) {
      asset.maintenanceDue = true
      asset.status = 'MAINTENANCE'
      actions.pushEvent(`${asset.shortName} zur Wartung markiert`, '#ff9100', 'WARTUNG')
    }
    actions.setAssets([...state.assets])
  }

  /**
   * Simulierte Zustellung eines Befehls: Online-Assets werden direkt
   * erreicht, sonst landet der Befehl in der Offline-Queue.
   */
  async dispatch (action, asset) {
    await new Promise((r) => setTimeout(r, 180 + Math.random() * 420))
    if (action.requiresOnline && asset.status !== 'ONLINE') {
      return { state: 'blocked', detail: `Asset ist ${STATUS[asset.status].label.toLowerCase()}` }
    }
    if (asset.status === 'ONLINE' && Math.random() > 0.08) {
      return { state: 'delivered', detail: pick(['MQTT', 'WebSocket', 'BLE/GATT']) }
    }
    if (action.queueable) {
      actions.setQueue(state.queue + 1)
      return { state: 'queued', detail: 'Kein Direktkanal – Offline-Queue' }
    }
    return { state: 'blocked', detail: 'Kein Kanal erreichbar' }
  }

  async flushQueue () {
    const n = state.queue
    await new Promise((r) => setTimeout(r, 500))
    if (!n) return 0
    const sent = Math.random() < 0.75 ? n : Math.floor(n / 2)
    actions.setQueue(n - sent)
    return sent
  }
}
