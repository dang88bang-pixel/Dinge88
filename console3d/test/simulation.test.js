import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ORIGIN, Simulation, buildFleet } from '../src/core/simulation.js'
import { actions, state } from '../src/core/store.js'
import { ACTIONS } from '../src/data/catalog.js'

/**
 * Die Simulation ist die Rückfallebene, wenn weder App-Bridge noch Backend
 * antworten. Sie darf deshalb keine unrealistischen oder unmöglichen Zustände
 * erzeugen – ein Demo-Termin läuft ausschließlich auf diesen Daten.
 */

const byId = Object.fromEntries(ACTIONS.map((a) => [a.id, a]))

/** Ersetzt Math.random deterministisch durch eine feste Folge. */
function stubRandom (...values) {
  let i = 0
  vi.spyOn(Math, 'random').mockImplementation(() => values[Math.min(i++, values.length - 1)])
}

beforeEach(() => {
  actions.setAssets([])
  actions.setQueue(0)
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.useRealTimers()
})

describe('Flottenaufbau', () => {
  it('erzeugt zwölf Assets mit stabilen Kennungen', () => {
    const fleet = buildFleet()
    expect(fleet).toHaveLength(12)
    expect(fleet[0].id).toBe('asset-001')
    expect(fleet[11].id).toBe('asset-012')
    expect(new Set(fleet.map((a) => a.id)).size).toBe(12)
    expect(new Set(fleet.map((a) => a.mac)).size).toBe(12)
  })

  it('deckt bewusst mehrere Betriebszustände ab', () => {
    const status = buildFleet().map((a) => a.status)
    expect(status[4]).toBe('MAINTENANCE')
    expect(status[7]).toBe('OFFLINE')
    expect(status[10]).toBe('SEARCHING')
    expect(new Set(status).size).toBeGreaterThanOrEqual(4)
  })

  it('gibt Offline-Assets keine Signalstärke vor', () => {
    for (const a of buildFleet().filter((x) => x.status === 'OFFLINE')) {
      expect(a.rssi).toBe(0)
    }
  })

  it('hält alle Werte in plausiblen Grenzen', () => {
    for (const a of buildFleet()) {
      expect(a.battery).toBeGreaterThanOrEqual(0)
      expect(a.battery).toBeLessThanOrEqual(100)
      expect(Math.abs(a.lat - ORIGIN.lat)).toBeLessThanOrEqual(0.011)
      expect(Math.abs(a.lon - ORIGIN.lon)).toBeLessThanOrEqual(0.017)
      expect(a.lastSeen).toBeLessThanOrEqual(Date.now())
      expect(a.mac).toMatch(/^([0-9A-F]{2}:){5}[0-9A-F]{2}$/)
    }
  })

  it('markiert Wartungsfälle konsistent', () => {
    for (const a of buildFleet()) {
      expect(a.maintenanceDue).toBe(a.status === 'MAINTENANCE')
    }
  })
})

describe('Befehlszustellung', () => {
  const online = { id: 'a1', shortName: 'X', status: 'ONLINE' }
  const offline = { id: 'a2', shortName: 'Y', status: 'OFFLINE' }
  const maintenance = { id: 'a3', shortName: 'Z', status: 'MAINTENANCE' }

  it('stellt an ein Online-Asset zu', async () => {
    stubRandom(0.5, 0.9)
    const res = await new Simulation().dispatch(byId.LIGHT, online)
    expect(res.state).toBe('delivered')
    expect(['MQTT', 'WebSocket', 'BLE/GATT']).toContain(res.detail)
  })

  it('blockiert Aktionen mit Online-Zwang auf Offline-Zielen', async () => {
    const res = await new Simulation().dispatch(byId.MOTOR_OFF, offline)
    expect(res.state).toBe('blocked')
    expect(res.detail).toContain('offline')
  })

  it('nennt bei Wartung den tatsächlichen Zustand', async () => {
    const res = await new Simulation().dispatch(byId.RESTART, maintenance)
    expect(res.state).toBe('blocked')
    expect(res.detail).toContain('wartung')
  })

  it('reiht wartefähige Befehle ein und erhöht die Warteschlange', async () => {
    stubRandom(0.5, 0.5) // Verzögerung, dann Zustellung schlägt fehl
    expect(state.queue).toBe(0)
    const res = await new Simulation().dispatch(byId.ALARM, offline)
    expect(res.state).toBe('queued')
    expect(state.queue).toBe(1)
  })

  it('verwirft nicht wartefähige Befehle, statt sie zu sammeln', async () => {
    const res = await new Simulation().dispatch(byId.MOTOR_OFF, offline)
    expect(res.state).toBe('blocked')
    expect(state.queue, 'darf die Warteschlange nicht füllen').toBe(0)
  })

  it('meldet auch bei Funkausfall eines Online-Assets sauber zurück', async () => {
    stubRandom(0.5, 0.01) // 0.01 < 0.08 → Zustellung misslingt
    const res = await new Simulation().dispatch(byId.ALARM, online)
    expect(res.state).toBe('queued')
  })

  it('antwortet nie ohne Begründung', async () => {
    for (const asset of [online, offline, maintenance]) {
      for (const action of [byId.ALARM, byId.LIGHT, byId.MOTOR_OFF]) {
        const res = await new Simulation().dispatch(action, asset)
        expect(['delivered', 'queued', 'blocked']).toContain(res.state)
        expect(typeof res.detail).toBe('string')
        expect(res.detail.length).toBeGreaterThan(0)
      }
    }
  })
})

describe('Warteschlange leeren', () => {
  it('meldet null bei leerer Warteschlange', async () => {
    expect(await new Simulation().flushQueue()).toBe(0)
    expect(state.queue).toBe(0)
  })

  it('stellt im Regelfall alles zu', async () => {
    actions.setQueue(6)
    stubRandom(0.5)
    expect(await new Simulation().flushQueue()).toBe(6)
    expect(state.queue).toBe(0)
  })

  it('stellt bei schlechter Verbindung nur die Hälfte zu', async () => {
    actions.setQueue(7)
    stubRandom(0.9)
    expect(await new Simulation().flushQueue()).toBe(3)
    expect(state.queue).toBe(4)
  })

  it('lässt die Warteschlange nie negativ werden', async () => {
    actions.setQueue(1)
    stubRandom(0.9) // floor(1/2) = 0
    const sent = await new Simulation().flushQueue()
    expect(sent).toBe(0)
    expect(state.queue).toBe(1)
  })
})

describe('Simulationsschleife', () => {
  it('startet Flotte und Quelle und räumt Timer wieder ab', () => {
    vi.useFakeTimers()
    const sim = new Simulation()
    sim.start()
    expect(state.assets).toHaveLength(12)
    expect(state.source).toBe('simulation')
    expect(sim.timers).toHaveLength(4)
    sim.stop()
    expect(sim.timers).toHaveLength(0)
  })

  it('bleibt bei gestopptem Agenten vollständig ruhig', () => {
    const sim = new Simulation()
    actions.setAssets(buildFleet())
    if (state.agent.running) actions.toggleAgent()
    const before = {
      detections: state.detections.length,
      cycle: state.agent.cycle,
      alerts: state.alerts.length
    }
    sim.stepPhysics(); sim.stepDetections(); sim.stepAgentCycle(); sim.stepIncidents()
    expect(state.detections.length).toBe(before.detections)
    expect(state.agent.cycle).toBe(before.cycle)
    expect(state.alerts.length).toBe(before.alerts)
  })

  it('erzeugt bei laufendem Agenten Detektionen mit gültigem Kanal', () => {
    const sim = new Simulation()
    actions.setAssets(buildFleet())
    if (!state.agent.running) actions.toggleAgent()
    sim.stepDetections()
    expect(state.detections.length).toBeGreaterThan(0)
    const d = state.detections[0]
    expect(d.assetId).toBeTruthy()
    expect(typeof d.source).toBe('string')
    expect(Number.isFinite(d.rssi)).toBe(true)
    if (state.agent.running) actions.toggleAgent()
  })

  it('hält Signalstärke und Akku beim Fortschreiben in den Grenzen', () => {
    const sim = new Simulation()
    actions.setAssets(buildFleet())
    if (!state.agent.running) actions.toggleAgent()
    for (let i = 0; i < 50; i++) sim.stepPhysics()
    for (const a of state.assets) {
      expect(a.battery).toBeGreaterThanOrEqual(0)
      expect(a.rssi).toBeGreaterThanOrEqual(-95)
      expect(a.rssi).toBeLessThanOrEqual(a.status === 'OFFLINE' ? 0 : -38)
      expect(Number.isFinite(a.lat) && Number.isFinite(a.lon)).toBe(true)
    }
    if (state.agent.running) actions.toggleAgent()
  })

  it('zählt Agentenzyklen hoch und misst eine plausible Latenz', () => {
    const sim = new Simulation()
    if (!state.agent.running) actions.toggleAgent()
    const before = state.agent.cycle
    sim.stepAgentCycle()
    expect(state.agent.cycle).toBe(before + 1)
    expect(state.latencyMs).toBeGreaterThanOrEqual(18)
    expect(state.latencyMs).toBeLessThanOrEqual(140)
    if (state.agent.running) actions.toggleAgent()
  })

  it('überlebt Zwischenfälle auf leerer Flotte', () => {
    const sim = new Simulation()
    actions.setAssets([])
    if (!state.agent.running) actions.toggleAgent()
    expect(() => sim.stepIncidents()).not.toThrow()
    expect(() => sim.stepDetections()).not.toThrow()
    if (state.agent.running) actions.toggleAgent()
  })
})
