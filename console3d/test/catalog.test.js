import { describe, expect, it } from 'vitest'
import { ACTIONS, ACTION_MAP, CATEGORIES, CHANNELS, CHANNEL_MAP, RISK_LABEL, STATUS } from '../src/data/catalog.js'

/**
 * Der Aktionskatalog ist der Vertrag zwischen Bedienoberfläche, App und
 * Firmware. Diese Prüfungen halten ihn konsistent – ein Katalogfehler fällt
 * sonst erst im Ernstfall auf.
 */

describe('Katalog – Struktur', () => {
  it('kennt genau die fünf Asset-Zustände der App', () => {
    expect(Object.keys(STATUS).sort())
      .toEqual(['MAINTENANCE', 'OFFLINE', 'ONLINE', 'SEARCHING', 'UNKNOWN'])
    for (const [key, value] of Object.entries(STATUS)) {
      expect(value.label, `${key} braucht ein Label`).toBeTruthy()
      expect(value.color).toMatch(/^#[0-9a-f]{6}$/i)
      expect(typeof value.rank).toBe('number')
    }
  })

  it('führt 12 Kanäle, davon 3 in Echtzeit', () => {
    expect(CHANNELS).toHaveLength(12)
    expect(CHANNELS.filter((c) => c.realtime).map((c) => c.id))
      .toEqual(['MQTT', 'WEBSOCKET', 'NFC'])
    expect(Object.keys(CHANNEL_MAP)).toHaveLength(12)
    for (const channel of CHANNELS) {
      expect(channel.color).toMatch(/^#[0-9a-f]{6}$/i)
      expect(CHANNEL_MAP[channel.id]).toBe(channel)
    }
  })

  it('vergibt eindeutige Aktions-IDs und Tastenkürzel', () => {
    const ids = ACTIONS.map((a) => a.id)
    expect(new Set(ids).size, 'IDs müssen eindeutig sein').toBe(ids.length)

    const keys = ACTIONS.map((a) => a.key).filter(Boolean)
    expect(new Set(keys).size, 'Tastenkürzel müssen eindeutig sein').toBe(keys.length)

    expect(Object.keys(ACTION_MAP)).toHaveLength(ACTIONS.length)
  })

  it('ordnet jede Aktion einer bekannten Kategorie zu', () => {
    const known = new Set(CATEGORIES.map((c) => c.id))
    for (const action of ACTIONS) {
      expect(known.has(action.category), `${action.id} hat Kategorie ${action.category}`).toBe(true)
    }
  })

  it('beschreibt jede Aktion vollständig', () => {
    for (const action of ACTIONS) {
      expect(action.title, `${action.id}: Titel`).toBeTruthy()
      expect(action.desc, `${action.id}: Beschreibung`).toBeTruthy()
      expect(action.icon, `${action.id}: Icon`).toBeTruthy()
      expect(action.color, `${action.id}: Farbe`).toMatch(/^#[0-9a-f]{6}$/i)
      expect(RISK_LABEL[action.risk], `${action.id}: Risikostufe`).toBeTruthy()
    }
  })
})

describe('Katalog – Sicherheitsregeln', () => {
  it('macht kritische Aktionen niemals einreihbar', () => {
    for (const action of ACTIONS.filter((a) => a.risk === 'critical')) {
      expect(action.queueable, `${action.id} darf nicht in die Offline-Queue`).toBe(false)
      expect(action.requiresOnline, `${action.id} braucht ein Online-Ziel`).toBe(true)
    }
  })

  it('hinterlegt für jede kritische Aktion einen Bestätigungstext', () => {
    for (const action of ACTIONS.filter((a) => a.risk === 'critical')) {
      expect(action.confirmTitle, `${action.id}: Dialogtitel`).toBeTruthy()
      expect(action.confirmText, `${action.id}: Dialogtext`).toBeTruthy()
      expect(action.confirmText.length, `${action.id}: Text erklärt die Folge`)
        .toBeGreaterThan(40)
    }
  })

  it('kennt genau MOTOR_OFF und RESTART als kritisch', () => {
    expect(ACTIONS.filter((a) => a.risk === 'critical').map((a) => a.id).sort())
      .toEqual(['MOTOR_OFF', 'RESTART'])
  })
})

describe('Katalog – Funkbefehle', () => {
  it('gibt jeder Nicht-Szenen-Aktion genau einen Wire-Befehl', () => {
    for (const action of ACTIONS.filter((a) => !a.local)) {
      expect(action.wire, `${action.id} braucht einen Wire-Befehl`).toBeTruthy()
      expect(action.wire).toMatch(/^[A-Z_]+$/)
    }
  })

  it('markiert Szenen-Aktionen als lokal und ohne Wire-Befehl', () => {
    const scene = ACTIONS.filter((a) => a.category === 'SCENE')
    expect(scene.map((a) => a.id).sort()).toEqual(['FOCUS', 'GEOFENCE', 'HEATMAP', 'SWEEP'])
    for (const action of scene) {
      expect(action.local).toBe(true)
      expect(action.wire).toBeUndefined()
    }
  })

  it('erlaubt Freitext ausschließlich bei MESSAGE', () => {
    expect(ACTIONS.filter((a) => a.acceptsNote).map((a) => a.id)).toEqual(['MESSAGE'])
  })

  it('deckt den Firmware-Befehlssatz ab', () => {
    const wires = ACTIONS.filter((a) => !a.local).map((a) => a.wire).sort()
    expect(wires).toEqual([
      'ALARM', 'BATTERY', 'LIGHT', 'MESSAGE', 'MOTOR_OFF', 'POSITION', 'RESTART', 'TELEMETRY'
    ])
  })
})
