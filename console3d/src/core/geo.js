/**
 * Projektion von WGS84-Koordinaten in das kartesische Szenen-Koordinatensystem.
 * Für Stadtgebiete (< 20 km) ist die äquirektanguläre Näherung ausreichend
 * genau und deutlich günstiger als eine echte Mercator-Projektion.
 */

const EARTH_R = 6378137
/** Wie viele Meter entsprechen einer Szenen-Einheit. */
export const METERS_PER_UNIT = 42

export function createProjector (origin) {
  const latRad = (origin.lat * Math.PI) / 180
  const mPerDegLat = (Math.PI / 180) * EARTH_R
  const mPerDegLon = mPerDegLat * Math.cos(latRad)

  return {
    origin,
    /** @returns {{x:number, z:number}} Szenen-Koordinaten */
    toScene (lat, lon) {
      if (lat == null || lon == null) return { x: 0, z: 0 }
      const x = ((lon - origin.lon) * mPerDegLon) / METERS_PER_UNIT
      const z = (-(lat - origin.lat) * mPerDegLat) / METERS_PER_UNIT
      return { x, z }
    },
    /** Meter zwischen zwei Punkten (Haversine). */
    distanceMeters (a, b) {
      const dLat = ((b.lat - a.lat) * Math.PI) / 180
      const dLon = ((b.lon - a.lon) * Math.PI) / 180
      const s = Math.sin(dLat / 2) ** 2 +
        Math.cos((a.lat * Math.PI) / 180) * Math.cos((b.lat * Math.PI) / 180) *
        Math.sin(dLon / 2) ** 2
      return 2 * EARTH_R * Math.asin(Math.min(1, Math.sqrt(s)))
    }
  }
}
