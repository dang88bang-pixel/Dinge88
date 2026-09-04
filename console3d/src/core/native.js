/**
 * Native-Brücke zur Android-App.
 *
 * Wird die Konsole im WebView von SecureGuard geladen, stellt die App das
 * Objekt `window.SecureGuardNative` bereit. Dann arbeitet das Lagebild direkt
 * auf der verschlüsselten Room-Datenbank und schickt Aktionen über den
 * echten AgentService (MQTT / WebSocket / BLE-GATT / Offline-Queue).
 *
 * Ohne WebView-Kontext ist `available === false` und die Konsole fällt
 * automatisch auf Backend bzw. Simulation zurück.
 */

const pending = new Map()

export const native = {
  get available () {
    return typeof window !== 'undefined' &&
      window.SecureGuardNative &&
      typeof window.SecureGuardNative.snapshot === 'function'
  },

  /** Vollständiger Zustand (Assets, Alarme, Detektionen, Agent, Queue). */
  snapshot () {
    try {
      return JSON.parse(window.SecureGuardNative.snapshot())
    } catch {
      return null
    }
  },

  toggleAgent () {
    try { window.SecureGuardNative.toggleAgent() } catch { /* ignorieren */ }
  },

  acknowledgeAlert (id) {
    try { window.SecureGuardNative.acknowledgeAlert(String(id)) } catch { /* ignorieren */ }
  },

  flushQueue () {
    return new Promise((resolve) => {
      const requestId = `q${Date.now()}${Math.random().toString(36).slice(2, 6)}`
      pending.set(requestId, resolve)
      try { window.SecureGuardNative.flushQueue(requestId) } catch { resolve({ state: 'blocked', detail: 'Bridge-Fehler' }) }
      setTimeout(() => {
        if (pending.has(requestId)) { pending.delete(requestId); resolve({ state: 'blocked', detail: 'Zeitüberschreitung' }) }
      }, 12000)
    })
  },

  /** Führt einen Befehl aus; das Ergebnis kommt asynchron über `resolve()`. */
  execute (assetId, wire, note) {
    return new Promise((resolve) => {
      const requestId = `r${Date.now()}${Math.random().toString(36).slice(2, 6)}`
      pending.set(requestId, resolve)
      try {
        window.SecureGuardNative.execute(requestId, String(assetId), String(wire), note || '')
      } catch (error) {
        pending.delete(requestId)
        resolve({ state: 'blocked', detail: error.message || 'Bridge-Fehler' })
        return
      }
      setTimeout(() => {
        if (pending.has(requestId)) {
          pending.delete(requestId)
          resolve({ state: 'blocked', detail: 'Zeitüberschreitung' })
        }
      }, 15000)
    })
  },

  /** Von Kotlin aufgerufen (evaluateJavascript). */
  resolve (requestId, resultState, detail) {
    const fn = pending.get(requestId)
    if (!fn) return
    pending.delete(requestId)
    fn({ state: resultState, detail })
  }
}

// Rückkanal für die Android-Seite global verfügbar machen.
if (typeof window !== 'undefined') {
  window.SecureGuardBridgeResolve = (requestId, resultState, detail) =>
    native.resolve(requestId, resultState, detail)
}
