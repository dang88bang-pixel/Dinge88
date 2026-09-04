import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../src/core/api.js'

/**
 * Vertrag zum FastAPI-Backend. Die Datenbank liefert snake_case und
 * SQLite-Zeitstempel; das HUD erwartet camelCase und Millisekunden. Bricht
 * diese Übersetzung, zeigt die Konsole leere Karten und Zeitangaben von 1970 –
 * ohne dass irgendetwas eine Fehlermeldung wirft.
 *
 * Die Antworten sind exakt die Strukturen, die `backend/main.py` zurückgibt
 * (siehe backend/tests/test_contract.py, dort real erzeugt).
 */

const ok = (body) => Promise.resolve({
  ok: true, status: 200, json: () => Promise.resolve(body)
})

let calls

beforeEach(() => {
  calls = []
  api.available = false
  api.apiKey = null
})

afterEach(() => {
  vi.restoreAllMocks()
})

function stubFetch (handler) {
  globalThis.fetch = (url, options = {}) => {
    calls.push({ url, options })
    return handler(url, options)
  }
}

describe('Erreichbarkeitsprüfung', () => {
  it('erkennt ein gesundes Backend', async () => {
    stubFetch(() => ok({ status: 'ok', assets: 12, detections: 75, alerts: 4 }))
    const res = await api.probe()
    expect(res.ok).toBe(true)
    expect(api.available).toBe(true)
    expect(res.info.assets).toBe(12)
    expect(typeof res.latency).toBe('number')
  })

  it('wertet einen degradierten Zustand als nicht verfügbar', async () => {
    stubFetch(() => ok({ status: 'degraded' }))
    expect((await api.probe()).ok).toBe(false)
    expect(api.available).toBe(false)
  })

  it('meldet einen HTTP-Fehler als nicht verfügbar, statt zu werfen', async () => {
    stubFetch(() => Promise.resolve({ ok: false, status: 503, json: () => ({}) }))
    await expect(api.probe()).resolves.toEqual({ ok: false, latency: null, info: null })
  })

  it('meldet einen Netzwerkabbruch als nicht verfügbar, statt zu werfen', async () => {
    stubFetch(() => Promise.reject(new TypeError('Failed to fetch')))
    await expect(api.probe()).resolves.toMatchObject({ ok: false })
  })

  it('bricht eine hängende Prüfung ab, statt den Start zu blockieren', async () => {
    vi.useFakeTimers()
    stubFetch((url, options) => new Promise((_, reject) => {
      options.signal.addEventListener('abort', () => reject(new Error('AbortError')))
    }))
    const pending = api.probe()
    await vi.advanceTimersByTimeAsync(2500)
    await expect(pending).resolves.toMatchObject({ ok: false })
    vi.useRealTimers()
  })
})

describe('Assets übersetzen', () => {
  const row = {
    id: 'asset-001', name: 'E-Scooter Innenhafen', mac: 'DE:AD:B0:28:0B:00',
    short_name: 'SCOOT-01', status: 'online', latitude: 51.4344,
    longitude: 6.7623, rssi: -61, last_seen: '2026-09-04T10:15:30'
  }

  it('bildet snake_case auf das HUD-Modell ab', async () => {
    stubFetch(() => ok([row]))
    const [a] = await api.assets()
    expect(a.id).toBe('asset-001')
    expect(a.shortName).toBe('SCOOT-01')
    expect(a.mac).toBe('DE:AD:B0:28:0B:00')
    expect(a.lat).toBeCloseTo(51.4344)
    expect(a.lon).toBeCloseTo(6.7623)
    expect(a.rssi).toBe(-61)
  })

  it('normalisiert die Statusschreibweise', async () => {
    stubFetch(() => ok([{ ...row, status: 'online' }, { ...row, id: 'b', status: 'Wartung' }]))
    const [a, b] = await api.assets()
    expect(a.status).toBe('ONLINE')
    expect(b.status, 'unbekannter Status darf nicht durchrutschen').toBe('UNKNOWN')
  })

  it('wandelt den Zeitstempel in Millisekunden', async () => {
    stubFetch(() => ok([row]))
    const [a] = await api.assets()
    expect(a.lastSeen).toBe(Date.parse('2026-09-04T10:15:30'))
  })

  it('unterscheidet fehlende Werte von Null', async () => {
    stubFetch(() => ok([{ ...row, latitude: null, longitude: null, battery_level: null, rssi: 0 }]))
    const [a] = await api.assets()
    expect(a.lat).toBeNull()
    expect(a.lon).toBeNull()
    expect(a.battery).toBeNull()
    expect(a.rssi, '0 dBm ist ein Messwert, kein fehlender Wert').toBe(0)
  })

  it('erfindet für unvollständige Datensätze brauchbare Bezeichner', async () => {
    stubFetch(() => ok([{}]))
    const [a] = await api.assets()
    expect(a.id).toBe('asset-0')
    expect(a.name).toBe('Asset 1')
    expect(a.shortName).toBe('A1')
    expect(a.status).toBe('UNKNOWN')
    expect(Number.isFinite(a.lastSeen)).toBe(true)
  })

  it('verträgt eine leere Antwort', async () => {
    stubFetch(() => ok(null))
    await expect(api.assets()).resolves.toEqual([])
  })
})

describe('Detektionen und Alarme übersetzen', () => {
  it('übernimmt Kanal und Signalstärke einer Detektion', async () => {
    stubFetch(() => ok([{
      id: 7, asset_mac: 'DE:AD:B0:28:0B:00', source_type: 'ble',
      rssi: -70, latitude: 51.43, longitude: 6.76,
      timestamp: '2026-09-04 10:15:30'
    }]))
    const [d] = await api.detections()
    expect(d.id).toBe('7')
    expect(d.source).toBe('BLE')
    expect(d.rssi).toBe(-70)
    expect(d.assetId).toBe('DE:AD:B0:28:0B:00')
    expect(d.ts).toBe(Date.parse('2026-09-04 10:15:30'))
  })

  it('reicht das Limit an das Backend durch', async () => {
    stubFetch(() => ok([]))
    await api.detections(25)
    expect(calls[0].url).toBe('/api/detections?limit=25')
  })

  it('übersetzt resolved in den Quittierungszustand', async () => {
    stubFetch(() => ok([
      { id: 1, asset_id: 'asset-001', type: 'geofence', severity: 'critical', message: 'x', resolved: 0 },
      { id: 2, asset_id: 'asset-001', type: 'security', severity: 'warning', message: 'y', resolved: 1 }
    ]))
    const [open, done] = await api.alerts()
    expect(open.acknowledged).toBe(false)
    expect(open.type).toBe('GEOFENCE')
    expect(open.severity).toBe('CRITICAL')
    expect(done.acknowledged).toBe(true)
  })
})

describe('Befehle senden', () => {
  it('schickt das vom Backend erwartete Format', async () => {
    stubFetch(() => ok({ status: 'queued', action_id: 'asset-001' }))
    const res = await api.execute('asset-001', 'ALARM')
    const { url, options } = calls[0]
    expect(url).toBe('/api/actions/execute')
    expect(options.method).toBe('POST')
    expect(JSON.parse(options.body)).toEqual({
      asset_id: 'asset-001', action_type: 'ALARM', payload: null
    })
    expect(res.status).toBe('queued')
    expect(typeof res.latency).toBe('number')
  })

  it('sendet den API-Schlüssel nur, wenn einer gesetzt ist', async () => {
    stubFetch(() => ok({ status: 'queued' }))
    await api.execute('a', 'LIGHT')
    expect(calls[0].options.headers['X-API-Key']).toBeUndefined()

    api.apiKey = 'geheim-123'
    await api.execute('a', 'LIGHT')
    expect(calls[1].options.headers['X-API-Key']).toBe('geheim-123')
  })

  it('meldet einen abgelehnten Befehl als Fehler, statt Erfolg vorzutäuschen', async () => {
    stubFetch(() => Promise.resolve({ ok: false, status: 401, json: () => ({}) }))
    await expect(api.execute('a', 'ALARM')).rejects.toThrow('HTTP 401')
  })

  it('überträgt einen Freitext als Wire-Befehl', async () => {
    stubFetch(() => ok({ status: 'queued' }))
    await api.execute('asset-001', 'MESSAGE:Bitte abstellen')
    expect(JSON.parse(calls[0].options.body).action_type).toBe('MESSAGE:Bitte abstellen')
  })
})

describe('Echtzeit-Kanal', () => {
  class FakeSocket {
    constructor (url) { this.url = url; FakeSocket.last = this }
    close () { this.closed = true }
  }

  it('verbindet relativ zum ausliefernden Host', () => {
    globalThis.WebSocket = FakeSocket
    const socket = api.connectSocket(() => {})
    expect(socket.url).toBe(`ws://${location.host}/ws`)
  })

  it('reicht nur gültiges JSON an die Konsole weiter', () => {
    globalThis.WebSocket = FakeSocket
    const seen = []
    api.connectSocket((msg) => seen.push(msg))
    FakeSocket.last.onmessage({ data: '{"type":"ack","delivered":true}' })
    FakeSocket.last.onmessage({ data: 'kein json' })
    expect(seen).toEqual([{ type: 'ack', delivered: true }])
  })

  it('gibt bei einem Fehler null zurück, statt den Start abzubrechen', () => {
    globalThis.WebSocket = function () { throw new Error('blockiert') }
    expect(api.connectSocket(() => {})).toBeNull()
  })
})
