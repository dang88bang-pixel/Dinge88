import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { native } from '../src/core/native.js'

/**
 * Vertrag der WebView-Brücke zwischen 3D-Konsole und Android-App.
 *
 * Diese Tests sind die einzige Absicherung dieses Vertrags außerhalb eines
 * echten Geräts. Sie bilden die Kotlin-Seite (`OpsCenterViewModel`,
 * `OpsBridge`) nach: synchroner Snapshot, asynchrone Antwort über
 * `window.SecureGuardBridgeResolve`.
 */

const SNAPSHOT = {
  source: 'native',
  queue: 2,
  role: 'OPERATOR',
  canExecute: true,
  agent: { running: true, cycle: 42, startedAt: 1700000000000, intervalSec: 30, lastRunAt: null },
  assets: [{
    id: 'asset-001', name: 'E-Scooter Innenhafen', shortName: 'SCOOT-01', kind: 'asset',
    mac: 'DE:AD:B0:28:0B:00', status: 'ONLINE', rssi: -61, battery: 74,
    lat: 51.4344, lon: 6.7623, lastSeen: 1700000000000, maintenanceDue: false
  }],
  alerts: [{
    id: '7', assetId: 'asset-001', assetName: 'SCOOT-01', type: 'GEOFENCE',
    severity: 'CRITICAL', message: 'Zone verlassen', acknowledged: false, ts: 1700000000000
  }],
  detections: [{
    id: '11', assetId: 'asset-001', assetName: 'SCOOT-01', source: 'BLE',
    rssi: -63, lat: 51.4344, lon: 6.7623, ts: 1700000000000
  }]
}

function installBridge (overrides = {}) {
  const calls = { toggleAgent: 0, acknowledgeAlert: [], execute: [], flushQueue: [] }
  window.SecureGuardNative = {
    snapshot: () => JSON.stringify(SNAPSHOT),
    toggleAgent: () => { calls.toggleAgent++ },
    acknowledgeAlert: (id) => calls.acknowledgeAlert.push(id),
    execute: (requestId, assetId, wire, note) =>
      calls.execute.push({ requestId, assetId, wire, note }),
    flushQueue: (requestId) => calls.flushQueue.push({ requestId }),
    ...overrides
  }
  return calls
}

afterEach(() => {
  delete window.SecureGuardNative
  vi.useRealTimers()
})

describe('Brücke – Verfügbarkeit', () => {
  it('meldet ohne App-Kontext nicht verfügbar', () => {
    expect(native.available).toBeFalsy()
  })

  it('meldet mit vollständiger Brücke verfügbar', () => {
    installBridge()
    expect(native.available).toBe(true)
  })

  it('meldet unvollständige Brücken als nicht verfügbar', () => {
    window.SecureGuardNative = { toggleAgent: () => {} } // kein snapshot()
    expect(native.available).toBeFalsy()
  })
})

describe('Brücke – Snapshot', () => {
  it('liest den vollständigen Zustand', () => {
    installBridge()
    const snap = native.snapshot()
    expect(snap.source).toBe('native')
    expect(snap.queue).toBe(2)
    expect(snap.role).toBe('OPERATOR')
    expect(snap.canExecute).toBe(true)
    expect(snap.agent).toMatchObject({ running: true, cycle: 42, intervalSec: 30 })
    expect(snap.assets[0]).toMatchObject({ id: 'asset-001', status: 'ONLINE', battery: 74 })
    expect(snap.alerts[0]).toMatchObject({ severity: 'CRITICAL', acknowledged: false })
    expect(snap.detections[0]).toMatchObject({ source: 'BLE' })
  })

  it('liefert null statt zu werfen, wenn die App Unsinn schickt', () => {
    installBridge({ snapshot: () => 'kein json' })
    expect(native.snapshot()).toBeNull()
  })

  it('liefert null, wenn die App-Seite eine Ausnahme wirft', () => {
    installBridge({ snapshot: () => { throw new Error('Bridge tot') } })
    expect(native.snapshot()).toBeNull()
  })

  it('verwendet Epoch-Millisekunden für alle Zeitstempel', () => {
    installBridge()
    const snap = native.snapshot()
    for (const ts of [snap.agent.startedAt, snap.alerts[0].ts, snap.detections[0].ts, snap.assets[0].lastSeen]) {
      expect(typeof ts).toBe('number')
      expect(ts).toBeGreaterThan(1e12)
    }
  })
})

describe('Brücke – einfache Aufrufe', () => {
  it('reicht toggleAgent durch', () => {
    const calls = installBridge()
    native.toggleAgent()
    expect(calls.toggleAgent).toBe(1)
  })

  it('übergibt Alarm-IDs immer als Zeichenkette', () => {
    const calls = installBridge()
    native.acknowledgeAlert(7)
    native.acknowledgeAlert('8')
    expect(calls.acknowledgeAlert).toEqual(['7', '8'])
  })

  it('schluckt Fehler der App-Seite, statt die Konsole abstürzen zu lassen', () => {
    installBridge({ toggleAgent: () => { throw new Error('kaputt') } })
    expect(() => native.toggleAgent()).not.toThrow()
  })
})

describe('Brücke – execute mit Rückkanal', () => {
  it('übergibt requestId, Ziel, Wire-Befehl und Notiz', async () => {
    const calls = installBridge()
    const promise = native.execute('asset-001', 'ALARM', 'Testnotiz')
    expect(calls.execute).toHaveLength(1)
    const { requestId, assetId, wire, note } = calls.execute[0]
    expect(assetId).toBe('asset-001')
    expect(wire).toBe('ALARM')
    expect(note).toBe('Testnotiz')

    window.SecureGuardBridgeResolve(requestId, 'delivered', 'MQTT')
    await expect(promise).resolves.toEqual({ state: 'delivered', detail: 'MQTT' })
  })

  it('kennt alle vier Ergebniszustände', async () => {
    const calls = installBridge()
    for (const [i, expected] of ['delivered', 'queued', 'blocked', 'denied'].entries()) {
      const promise = native.execute(`asset-${i}`, 'LIGHT', '')
      window.SecureGuardBridgeResolve(calls.execute[i].requestId, expected, 'x')
      await expect(promise).resolves.toMatchObject({ state: expected })
    }
  })

  it('normalisiert eine fehlende Notiz zu einer leeren Zeichenkette', () => {
    const calls = installBridge()
    native.execute('asset-001', 'POSITION')
    expect(calls.execute[0].note).toBe('')
  })

  it('vergibt für jeden Aufruf eine eigene requestId', () => {
    const calls = installBridge()
    native.execute('a', 'ALARM', '')
    native.execute('b', 'ALARM', '')
    expect(calls.execute[0].requestId).not.toBe(calls.execute[1].requestId)
  })

  it('meldet blockiert, wenn die App-Seite sofort wirft', async () => {
    installBridge({ execute: () => { throw new Error('Interface weg') } })
    await expect(native.execute('asset-001', 'ALARM', ''))
      .resolves.toMatchObject({ state: 'blocked' })
  })

  it('meldet nach 15 Sekunden ohne Antwort blockiert', async () => {
    vi.useFakeTimers()
    installBridge()
    const promise = native.execute('asset-001', 'ALARM', '')
    vi.advanceTimersByTime(15000)
    await expect(promise).resolves.toEqual({ state: 'blocked', detail: 'Zeitüberschreitung' })
  })

  it('ignoriert eine Antwort auf eine unbekannte requestId', () => {
    installBridge()
    expect(() => window.SecureGuardBridgeResolve('gibt-es-nicht', 'delivered', '')).not.toThrow()
  })

  it('löst dieselbe requestId nur einmal auf', async () => {
    const calls = installBridge()
    const promise = native.execute('asset-001', 'ALARM', '')
    const { requestId } = calls.execute[0]
    window.SecureGuardBridgeResolve(requestId, 'delivered', 'erste')
    window.SecureGuardBridgeResolve(requestId, 'blocked', 'zweite')
    await expect(promise).resolves.toEqual({ state: 'delivered', detail: 'erste' })
  })
})

describe('Brücke – flushQueue', () => {
  it('gibt die Anzahl zugestellter Befehle im Detail zurück', async () => {
    const calls = installBridge()
    const promise = native.flushQueue()
    window.SecureGuardBridgeResolve(calls.flushQueue[0].requestId, 'delivered', '3')
    await expect(promise).resolves.toEqual({ state: 'delivered', detail: '3' })
  })

  it('läuft nach 12 Sekunden in die Zeitüberschreitung', async () => {
    vi.useFakeTimers()
    installBridge()
    const promise = native.flushQueue()
    vi.advanceTimersByTime(12000)
    await expect(promise).resolves.toEqual({ state: 'blocked', detail: 'Zeitüberschreitung' })
  })
})
