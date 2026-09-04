import { describe, expect, it } from 'vitest'
import { METERS_PER_UNIT, createProjector } from '../src/core/geo.js'

/**
 * Fehler in der Projektion sind besonders tückisch: Ein NaN in einer Position
 * lässt in Three.js die gesamte Szene verschwinden, ohne dass eine Ausnahme
 * geworfen wird.
 */

const ORIGIN = { lat: 51.4344, lon: 6.7623 } // Duisburg Innenhafen

describe('Projektion', () => {
  const p = createProjector(ORIGIN)

  it('bildet den Ursprung auf den Nullpunkt ab', () => {
    const out = p.toScene(ORIGIN.lat, ORIGIN.lon)
    // Hinweis: z ist hier -0 (Vorzeichenumkehr für die Nordrichtung). Für
    // Three.js ist das gleichwertig zu 0, deshalb wird numerisch verglichen
    // und nicht mit Object.is.
    expect(out.x).toBeCloseTo(0, 12)
    expect(out.z).toBeCloseTo(0, 12)
  })

  it('liefert für fehlende Koordinaten den Nullpunkt statt NaN', () => {
    for (const bad of [[null, null], [undefined, 6.76], [51.43, undefined], [null, 6.76]]) {
      const out = p.toScene(bad[0], bad[1])
      expect(Number.isNaN(out.x), `x für ${bad}`).toBe(false)
      expect(Number.isNaN(out.z), `z für ${bad}`).toBe(false)
    }
  })

  it('orientiert Norden nach -z und Osten nach +x', () => {
    const north = p.toScene(ORIGIN.lat + 0.01, ORIGIN.lon)
    const east = p.toScene(ORIGIN.lat, ORIGIN.lon + 0.01)
    expect(north.z).toBeLessThan(0)
    expect(north.x).toBeCloseTo(0, 10)
    expect(east.x).toBeGreaterThan(0)
    expect(east.z).toBeCloseTo(0, 10)
  })

  it('rechnet mit dem vereinbarten Maßstab', () => {
    // 0,01° Breite entsprechen rund 1112 m; geteilt durch 42 m/Einheit ≈ 26,5
    const north = p.toScene(ORIGIN.lat + 0.01, ORIGIN.lon)
    expect(Math.abs(north.z)).toBeGreaterThan(25)
    expect(Math.abs(north.z)).toBeLessThan(28)
    expect(METERS_PER_UNIT).toBe(42)
  })

  it('staucht Längengrade mit dem Kosinus der Breite', () => {
    const north = p.toScene(ORIGIN.lat + 0.01, ORIGIN.lon)
    const east = p.toScene(ORIGIN.lat, ORIGIN.lon + 0.01)
    // Auf 51,4° Nord ist ein Längengrad ca. 62 % eines Breitengrades.
    const ratio = Math.abs(east.x) / Math.abs(north.z)
    expect(ratio).toBeGreaterThan(0.58)
    expect(ratio).toBeLessThan(0.66)
  })
})

describe('Entfernungsberechnung', () => {
  const p = createProjector(ORIGIN)

  it('misst null Meter für denselben Punkt', () => {
    expect(p.distanceMeters(ORIGIN, ORIGIN)).toBeCloseTo(0, 6)
  })

  it('misst einen Breitengrad mit rund 111 km', () => {
    const d = p.distanceMeters({ lat: 51, lon: 7 }, { lat: 52, lon: 7 })
    expect(d).toBeGreaterThan(111000)
    expect(d).toBeLessThan(111400)
  })

  it('stimmt mit einer bekannten Strecke überein (Duisburg – Köln ≈ 60 km)', () => {
    const koeln = { lat: 50.9375, lon: 6.9603 }
    const d = p.distanceMeters(ORIGIN, koeln)
    expect(d).toBeGreaterThan(53000)
    expect(d).toBeLessThan(60000)
  })

  it('ist symmetrisch', () => {
    const a = { lat: 51.43, lon: 6.75 }
    const b = { lat: 51.45, lon: 6.79 }
    expect(p.distanceMeters(a, b)).toBeCloseTo(p.distanceMeters(b, a), 6)
  })
})
