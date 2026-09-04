import { beforeEach, describe, expect, it, vi } from 'vitest'
import { actions, on, selectors, state } from '../src/core/store.js'

/**
 * Der Store ist die einzige Wahrheit der Konsole. Jede Mutation muss ein
 * Ereignis auslösen, sonst bleibt die Oberfläche stehen – ein Fehler, der im
 * Betrieb schwer zu erkennen ist.
 */

function makeAsset (i, patch = {}) {
  return {
    id: `asset-${i}`,
    name: `Testgerät ${i}`,
    shortName: `T-${i}`,
    mac: `AA:BB:CC:00:00:0${i}`,
    status: 'ONLINE',
    rssi: -60,
    battery: 80,
    lat: 51.4344,
    lon: 6.7623,
    lastSeen: Date.now(),
    ...patch
  }
}

function reset () {
  state.assets = []
  state.detections = []
  state.alerts = []
  state.commands = []
  state.events = []
  state.queue = 0
  state.selection = new Set()
  state.filters = { query: '', status: 'ALL' }
  state.feedPaused = false
  state.busyAction = null
  state.overlays = { geofence: false, heatmap: false }
}

beforeEach(reset)

describe('Store – Ereignisbus', () => {
  it('meldet Abonnenten und lässt sich abbestellen', () => {
    const seen = []
    const off = on('assets', () => seen.push('assets'))
    actions.setAssets([makeAsset(1)])
    expect(seen).toEqual(['assets'])
    off()
    actions.setAssets([makeAsset(2)])
    expect(seen, 'nach dem Abbestellen darf nichts mehr ankommen').toEqual(['assets'])
  })

  it('leitet jedes Ereignis zusätzlich an den Platzhalter-Kanal', () => {
    const seen = []
    const off = on('*', (payload) => seen.push(payload.event))
    actions.setAssets([makeAsset(1)])
    off()
    expect(seen).toContain('assets')
    expect(seen).toContain('selection')
  })
})

describe('Store – Assets und Auswahl', () => {
  it('wählt beim ersten Laden automatisch ein Ziel', () => {
    actions.setAssets([makeAsset(1), makeAsset(2)])
    expect([...state.selection]).toEqual(['asset-1'])
  })

  it('entfernt verschwundene Assets aus der Auswahl', () => {
    actions.setAssets([makeAsset(1), makeAsset(2)])
    actions.selectAllVisible(['asset-1', 'asset-2'])
    actions.setAssets([makeAsset(2)])
    expect([...state.selection]).toEqual(['asset-2'])
  })

  it('verhindert eine leere Auswahl bei vorhandenen Assets', () => {
    actions.setAssets([makeAsset(1)])
    actions.clearSelection()
    actions.setAssets([makeAsset(1)])
    expect(state.selection.size).toBe(1)
  })

  it('schaltet einzelne Ziele um', () => {
    actions.setAssets([makeAsset(1), makeAsset(2)])
    actions.selectOnly('asset-1')
    actions.toggleSelection('asset-2')
    expect([...state.selection].sort()).toEqual(['asset-1', 'asset-2'])
    actions.toggleSelection('asset-2')
    expect([...state.selection]).toEqual(['asset-1'])
  })

  it('ignoriert patchAsset für unbekannte IDs, statt zu werfen', () => {
    actions.setAssets([makeAsset(1)])
    expect(() => actions.patchAsset('gibt-es-nicht', { rssi: -10 })).not.toThrow()
    expect(state.assets[0].rssi).toBe(-60)
  })
})

describe('Store – Filter und Selektoren', () => {
  beforeEach(() => {
    actions.setAssets([
      makeAsset(1, { shortName: 'SCOOT-01', name: 'E-Scooter Hafen', status: 'ONLINE' }),
      makeAsset(2, { shortName: 'CARGO-02', name: 'Lastenrad Werkstatt', status: 'MAINTENANCE' }),
      makeAsset(3, { shortName: 'TAG-114', name: 'Schlüsselfinder', status: 'OFFLINE' })
    ])
  })

  it('sucht über Kurzname, Name, MAC und ID', () => {
    actions.setFilter({ query: 'lastenrad' })
    expect(selectors.visibleAssets().map((a) => a.shortName)).toEqual(['CARGO-02'])

    actions.setFilter({ query: 'AA:BB:CC:00:00:03' })
    expect(selectors.visibleAssets().map((a) => a.shortName)).toEqual(['TAG-114'])

    actions.setFilter({ query: 'asset-1' })
    expect(selectors.visibleAssets().map((a) => a.shortName)).toEqual(['SCOOT-01'])
  })

  it('filtert nach Status und sortiert alphabetisch', () => {
    actions.setFilter({ query: '', status: 'ALL' })
    expect(selectors.visibleAssets().map((a) => a.shortName))
      .toEqual(['CARGO-02', 'SCOOT-01', 'TAG-114'])

    actions.setFilter({ status: 'OFFLINE' })
    expect(selectors.visibleAssets().map((a) => a.shortName)).toEqual(['TAG-114'])
  })

  it('zählt Zustände korrekt', () => {
    const counts = selectors.counts()
    expect(counts.total).toBe(3)
    expect(counts.ONLINE).toBe(1)
    expect(counts.MAINTENANCE).toBe(1)
    expect(counts.OFFLINE).toBe(1)
  })

  it('liefert Ziele und das primäre Ziel passend zur Auswahl', () => {
    actions.selectAllVisible(['asset-1', 'asset-3'])
    expect(selectors.targets().map((a) => a.id).sort()).toEqual(['asset-1', 'asset-3'])
    expect(selectors.primary().id).toBe('asset-1')

    actions.clearSelection()
    expect(selectors.primary()).toBeNull()
  })
})

describe('Store – Agent', () => {
  it('setzt beim Start einen Zeitstempel und beim Stopp keinen', () => {
    state.agent.running = false
    actions.toggleAgent()
    expect(state.agent.running).toBe(true)
    expect(state.agent.startedAt).toBeGreaterThan(0)

    actions.toggleAgent()
    expect(state.agent.running).toBe(false)
    expect(state.agent.startedAt).toBeNull()
  })

  it('protokolliert Start und Stopp im Feed', () => {
    state.agent.running = false
    actions.toggleAgent()
    expect(state.events[0].text).toBe('Agent gestartet')
    actions.toggleAgent()
    expect(state.events[0].text).toBe('Agent gestoppt')
  })
})

describe('Store – Detektionen, Alarme, Befehle', () => {
  it('begrenzt Detektionen auf 400 und hält die jüngste vorn', () => {
    for (let i = 0; i < 450; i++) {
      actions.addDetection({ id: `d${i}`, assetId: 'asset-1', source: 'BLE', ts: Date.now() })
    }
    expect(state.detections).toHaveLength(400)
    expect(state.detections[0].id).toBe('d449')
  })

  it('begrenzt Alarme auf 200 und Befehle auf 300', () => {
    for (let i = 0; i < 260; i++) actions.addAlert({ id: `a${i}`, acknowledged: false })
    for (let i = 0; i < 360; i++) actions.addCommand({ id: `c${i}`, state: 'running' })
    expect(state.alerts).toHaveLength(200)
    expect(state.commands).toHaveLength(300)
  })

  it('quittiert einzelne und alle Alarme', () => {
    actions.addAlert({ id: 'a1', acknowledged: false })
    actions.addAlert({ id: 'a2', acknowledged: false })
    expect(selectors.unacknowledgedAlerts()).toHaveLength(2)

    actions.ackAlert('a1')
    expect(selectors.unacknowledgedAlerts().map((a) => a.id)).toEqual(['a2'])

    actions.ackAllAlerts()
    expect(selectors.unacknowledgedAlerts()).toHaveLength(0)
  })

  it('aktualisiert Befehle über updateCommand', () => {
    actions.addCommand({ id: 'c1', state: 'running', detail: '' })
    actions.updateCommand('c1', { state: 'delivered', detail: 'MQTT' })
    expect(state.commands[0]).toMatchObject({ state: 'delivered', detail: 'MQTT' })
  })

  it('lässt die Warteschlange nie negativ werden', () => {
    actions.setQueue(3)
    expect(state.queue).toBe(3)
    actions.setQueue(-5)
    expect(state.queue).toBe(0)
  })
})

describe('Store – Feed', () => {
  it('pausiert die Aufzeichnung', () => {
    actions.pushEvent('vorher')
    actions.toggleFeedPause()
    actions.pushEvent('während der Pause')
    expect(state.events.map((e) => e.text)).toEqual(['vorher'])
    actions.toggleFeedPause()
    actions.pushEvent('danach')
    expect(state.events[0].text).toBe('danach')
  })

  it('begrenzt den Feed auf 60 Einträge', () => {
    for (let i = 0; i < 80; i++) actions.pushEvent(`E${i}`)
    expect(state.events).toHaveLength(60)
  })
})

describe('Store – Kennzahlen', () => {
  it('zählt Kanalaktivität nur im Zeitfenster', () => {
    const now = Date.now()
    actions.addDetection({ id: 'alt', source: 'LORA', ts: now - 300000 })
    actions.addDetection({ id: 'neu2', source: 'BLE', ts: now - 1000 })
    actions.addDetection({ id: 'neu1', source: 'BLE', ts: now })
    const activity = selectors.channelActivity(120000)
    expect(activity.BLE).toBe(2)
    expect(activity.LORA).toBeUndefined()
  })

  it('verteilt Detektionen auf Zeit-Buckets für die Sparkline', () => {
    const now = Date.now()
    actions.addDetection({ id: 'd1', source: 'BLE', ts: now })
    actions.addDetection({ id: 'd2', source: 'BLE', ts: now - 16000 })
    const series = selectors.series(20, 15000)
    expect(series).toHaveLength(20)
    expect(series[19]).toBe(1)
    expect(series[18]).toBe(1)
    expect(series.reduce((a, b) => a + b, 0)).toBe(2)
  })
})

describe('Store – Favoriten', () => {
  it('schreibt Favoriten in den lokalen Speicher', () => {
    const spy = vi.spyOn(Storage.prototype, 'setItem')
    const before = state.favorites.has('LIGHT')
    actions.toggleFavorite('LIGHT')
    expect(state.favorites.has('LIGHT')).toBe(!before)
    expect(spy).toHaveBeenCalledWith('sg.favorites', expect.any(String))
    actions.toggleFavorite('LIGHT')
    spy.mockRestore()
  })
})
