/**
 * Backend-Adapter für das SecureGuard-FastAPI-Backend.
 *
 * Der Browser spricht ausschließlich relative URLs an (`/api/...`); im
 * Entwicklungsbetrieb leitet Vite sie an das Backend weiter, im Container
 * übernimmt das der Reverse-Proxy. Ist kein Backend erreichbar, meldet
 * `probe()` `false` und die Konsole schaltet auf die Live-Simulation um.
 */

import { STATUS } from '../data/catalog.js'

const TIMEOUT_MS = 3500

async function request (path, options = {}) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), options.timeout || TIMEOUT_MS)
  const started = performance.now()
  try {
    const res = await fetch(path, {
      headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
      signal: controller.signal,
      method: options.method || 'GET',
      body: options.body ? JSON.stringify(options.body) : undefined
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const data = res.status === 204 ? null : await res.json()
    return { data, latency: Math.round(performance.now() - started) }
  } finally {
    clearTimeout(timer)
  }
}

function normaliseStatus (value) {
  const key = String(value || '').toUpperCase()
  return STATUS[key] ? key : 'UNKNOWN'
}

function toTimestamp (value) {
  if (!value) return Date.now()
  if (typeof value === 'number') return value < 1e12 ? value * 1000 : value
  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? Date.now() : parsed
}

export const api = {
  available: false,
  apiKey: null,

  /** Prüft, ob ein Backend erreichbar ist. */
  async probe () {
    try {
      const { data, latency } = await request('/api/health', { timeout: 2000 })
      this.available = data?.status === 'ok'
      return { ok: this.available, latency, info: data }
    } catch {
      this.available = false
      return { ok: false, latency: null, info: null }
    }
  },

  async assets () {
    const { data } = await request('/api/assets')
    return (data || []).map((row, i) => ({
      id: String(row.id ?? `asset-${i}`),
      name: row.name || row.short_name || `Asset ${i + 1}`,
      shortName: row.short_name || row.name || `A${i + 1}`,
      kind: row.kind || 'asset',
      mac: row.mac || '',
      status: normaliseStatus(row.status),
      rssi: Number(row.rssi ?? 0),
      battery: row.battery_level == null ? null : Number(row.battery_level),
      lat: row.latitude == null ? null : Number(row.latitude),
      lon: row.longitude == null ? null : Number(row.longitude),
      lastSeen: toTimestamp(row.last_seen),
      maintenanceDue: !!row.maintenance_due
    }))
  },

  async detections (limit = 120) {
    const { data } = await request(`/api/detections?limit=${limit}`)
    return (data || []).map((row, i) => ({
      id: String(row.id ?? i),
      assetId: String(row.asset_mac ?? ''),
      assetName: String(row.asset_mac ?? ''),
      source: String(row.source_type || 'UNKNOWN').toUpperCase(),
      rssi: Number(row.rssi ?? 0),
      lat: row.latitude == null ? null : Number(row.latitude),
      lon: row.longitude == null ? null : Number(row.longitude),
      ts: toTimestamp(row.timestamp)
    }))
  },

  async alerts () {
    const { data } = await request('/api/alerts')
    return (data || []).map((row, i) => ({
      id: String(row.id ?? i),
      assetId: String(row.asset_id ?? ''),
      assetName: String(row.asset_id ?? ''),
      type: String(row.type || 'INFO').toUpperCase(),
      severity: String(row.severity || 'INFO').toUpperCase(),
      message: row.message || '',
      acknowledged: !!row.resolved,
      ts: toTimestamp(row.timestamp)
    }))
  },

  async stats () {
    const { data } = await request('/api/stats')
    return data
  },

  /** Führt eine Aktion über das Backend aus (MQTT-Zustellung dahinter). */
  async execute (assetId, actionType, payload) {
    const { data, latency } = await request('/api/actions/execute', {
      method: 'POST',
      headers: this.apiKey ? { 'X-API-Key': this.apiKey } : {},
      body: { asset_id: assetId, action_type: actionType, payload: payload || null }
    })
    return { ...data, latency }
  },

  /**
   * WebSocket für Echtzeit-Ereignisse. Fällt still aus, wenn nicht verfügbar –
   * die Konsole bleibt über REST-Polling funktionsfähig.
   */
  connectSocket (onMessage) {
    try {
      const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
      const socket = new WebSocket(`${proto}//${location.host}/ws`)
      socket.onmessage = (event) => {
        try { onMessage(JSON.parse(event.data)) } catch { /* Nicht-JSON ignorieren */ }
      }
      socket.onerror = () => socket.close()
      return socket
    } catch {
      return null
    }
  }
}
