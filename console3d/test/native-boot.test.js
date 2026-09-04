import { beforeAll, describe, expect, it, vi } from 'vitest'
import {
  click, installFrameControl, installOfflineFetch, installWebSocketStub,
  mountIndexHtml, tick
} from './setup/boot.js'
import { resetWorldCalls } from './setup/world.mock.js'

/**
 * Betrieb in der Android-App: Das HUD läuft in einer WebView und bezieht seine
 * Daten über `window.SecureGuardNative` statt über HTTP. Dieser Pfad ist im
 * Feld der Normalfall – er wird hier vollständig durchgespielt, inklusive
 * Befehlszustellung und Quittierung über die Brücke.
 *
 * Eigene Datei, weil `main.js` seinen Bootvorgang genau einmal pro
 * Modulregistrierung ausführt: Die Brücke muss vor dem Import stehen.
 */

vi.mock('../src/scene/world.js', async () => await import('./setup/world.mock.js'))

const NOW = Date.now()

/** Nachbau der Kotlin-Brücke (SecureGuardBridge.kt) für den Test. */
function makeBridge () {
  const calls = { execute: [], flush: [], ack: [], toggle: 0 }
  let queue = 3
  const snapshot = () => JSON.stringify({
    source: 'native',
    queue,
    role: 'OPERATOR',
    canExecute: true,
    agent: { running: true, cycle: 7, startedAt: NOW - 60000, intervalSec: 30, lastRunAt: NOW },
    assets: [
      {
        id: 'asset-001', name: 'E-Scooter Innenhafen', shortName: 'SCOOT-01',
        kind: 'scooter', mac: 'DE:AD:B0:28:0B:00', status: 'ONLINE', rssi: -58,
        battery: 74, lat: 51.4344, lon: 6.7623, lastSeen: NOW - 4000,
        maintenanceDue: false
      },
      {
        id: 'asset-002', name: 'Lastenrad Werkstatt', shortName: 'CARGO-02',
        kind: 'cargo', mac: 'DE:AD:B1:2F:18:01', status: 'OFFLINE', rssi: 0,
        battery: null, lat: null, lon: null, lastSeen: NOW - 900000,
        maintenanceDue: true
      }
    ],
    alerts: [{
      id: 'alert-1', assetId: 'asset-002', assetName: 'CARGO-02',
      type: 'SECURITY', severity: 'WARNING', acknowledged: false,
      message: 'CARGO-02 über alle Kanäle nicht erreichbar', ts: NOW - 30000
    }],
    detections: [{
      id: 'det-1', assetId: 'asset-001', assetName: 'SCOOT-01',
      source: 'BLE', rssi: -60, lat: 51.4344, lon: 6.7623, ts: NOW - 5000
    }]
  })

  return {
    calls,
    setQueue (n) { queue = n },
    bridge: {
      snapshot,
      toggleAgent () { calls.toggle++ },
      acknowledgeAlert (id) { calls.ack.push(id) },
      execute (requestId, assetId, wire, note) {
        calls.execute.push({ requestId, assetId, wire, note })
        // Die App antwortet asynchron – genau wie Kotlin es tut.
        setTimeout(() => {
          window.SecureGuardBridgeResolve(requestId, 'delivered', 'MQTT')
        }, 10)
      },
      flushQueue (requestId) {
        calls.flush.push(requestId)
        setTimeout(() => {
          queue = 0
          window.SecureGuardBridgeResolve(requestId, 'delivered', '3 zugestellt')
        }, 10)
      }
    }
  }
}

let ops
let harness
let frames

beforeAll(async () => {
  mountIndexHtml()
  frames = installFrameControl()
  installOfflineFetch()
  installWebSocketStub()

  harness = makeBridge()
  window.SecureGuardNative = harness.bridge

  await import('../src/main.js')
  await tick(30)
  frames.pump(3)
  ops = window.SecureGuardOps
  resetWorldCalls()
})

describe('Start in der App', () => {
  it('bevorzugt die App-Brücke gegenüber Backend und Simulation', () => {
    expect(ops.state.source).toBe('native')
    expect(document.querySelector('#sourceLabel').textContent).toBe('App-Daten live')
  })

  it('übernimmt die Flotte aus der App', () => {
    expect(ops.state.assets).toHaveLength(2)
    expect(ops.state.assets[0].shortName).toBe('SCOOT-01')
    expect(ops.state.assets[1].status).toBe('OFFLINE')
  })

  it('übernimmt Agentenzustand und Warteschlange', () => {
    expect(ops.state.agent.running).toBe(true)
    expect(ops.state.agent.cycle).toBe(7)
    expect(ops.state.queue).toBe(3)
    expect(document.querySelector('#queueTag').textContent).toBe('Queue 3')
  })

  it('übernimmt Alarme und Detektionen', () => {
    expect(ops.state.alerts.some((a) => a.id === 'alert-1')).toBe(true)
    expect(ops.state.detections.some((d) => d.id === 'det-1')).toBe(true)
    expect(Number(document.querySelector('#alertBadge').textContent)).toBeGreaterThan(0)
  })

  it('verkraftet Assets ohne Koordinaten und ohne Akkuwert', () => {
    const offline = ops.state.assets.find((a) => a.id === 'asset-002')
    expect(offline.lat).toBeNull()
    expect(offline.battery).toBeNull()
    expect(() => frames.pump(3)).not.toThrow()
  })
})

describe('Befehle über die Brücke', () => {
  it('schickt Wire-Befehl und Ziel-ID an die App und wertet die Antwort aus', async () => {
    ops.actions.selectOnly('asset-001')
    frames.pump(2)

    await ops.executeAction(ops.ACTION_MAP.ALARM)

    expect(harness.calls.execute).toHaveLength(1)
    const call = harness.calls.execute[0]
    expect(call.assetId).toBe('asset-001')
    expect(call.wire).toBe('ALARM')
    expect(call.note).toBe('')
    expect(typeof call.requestId).toBe('string')

    const cmd = ops.state.commands[0]
    expect(cmd.action).toBe('ALARM')
    expect(cmd.state).toBe('delivered')
    expect(cmd.detail).toBe('MQTT')
  })

  it('reicht den Freitext einer Nachricht durch', async () => {
    ops.actions.selectOnly('asset-001')
    ops.actions.setNote('Bitte am Hafentor abstellen')
    frames.pump(2)

    await ops.executeAction(ops.ACTION_MAP.MESSAGE)

    const call = harness.calls.execute.at(-1)
    expect(call.wire).toBe('MESSAGE')
    expect(call.note).toBe('Bitte am Hafentor abstellen')
    expect(ops.state.note).toBe('')
  })

  it('vergibt für jeden Befehl eine eigene Anfrage-ID', async () => {
    ops.actions.selectAllVisible(['asset-001', 'asset-002'])
    frames.pump(2)
    const before = harness.calls.execute.length
    await ops.executeAction(ops.ACTION_MAP.POSITION)
    const ids = harness.calls.execute.slice(before).map((c) => c.requestId)
    expect(ids).toHaveLength(2)
    expect(new Set(ids).size).toBe(2)
  })

  it('leert die Warteschlange über die App', async () => {
    click('#btnQueueFlush')
    await tick(60)
    expect(harness.calls.flush).toHaveLength(1)
    expect(ops.state.events[0].text).toContain('Offline-Queue')
  })

  it('quittiert einen Alarm auch in der App, nicht nur in der Anzeige', () => {
    ops.actions.ackAlert('alert-1')
    expect(harness.calls.ack).toContain('alert-1')
    expect(ops.state.alerts.find((a) => a.id === 'alert-1').acknowledged).toBe(true)
  })

  it('quittiert per Sammelquittierung jeden offenen Alarm einzeln in der App', () => {
    ops.actions.addAlert({
      id: 'alert-2', assetId: 'asset-001', assetName: 'SCOOT-01', type: 'GEOFENCE',
      severity: 'CRITICAL', acknowledged: false, message: 'Zweiter Alarm', ts: Date.now()
    })
    const before = harness.calls.ack.length
    ops.actions.ackAllAlerts()
    expect(harness.calls.ack.length).toBeGreaterThan(before)
    expect(harness.calls.ack).toContain('alert-2')
    expect(ops.state.alerts.every((a) => a.acknowledged)).toBe(true)
  })

  it('schaltet den Agenten in der App statt lokal', () => {
    const before = harness.calls.toggle
    click('#agentToggle')
    expect(harness.calls.toggle).toBe(before + 1)
  })
})

describe('Störungen der Brücke', () => {
  it('verkraftet eine Antwort auf eine unbekannte Anfrage', () => {
    expect(() => window.SecureGuardBridgeResolve('gibt-es-nicht', 'delivered', 'x'))
      .not.toThrow()
  })

  it('verwirft eine doppelte Antwort auf dieselbe Anfrage', async () => {
    ops.actions.selectOnly('asset-001')
    const run = ops.executeAction(ops.ACTION_MAP.LIGHT)
    const id = harness.calls.execute.at(-1).requestId
    await run
    const commands = ops.state.commands.length
    window.SecureGuardBridgeResolve(id, 'blocked', 'zu spät')
    expect(ops.state.commands.length).toBe(commands)
    expect(ops.state.commands[0].state).toBe('delivered')
  })

  it('bleibt bei fehlerhaftem Schnappschuss auf dem letzten guten Stand', () => {
    const original = harness.bridge.snapshot
    harness.bridge.snapshot = () => 'kein json'
    const assets = ops.state.assets.length
    expect(() => frames.pump(2)).not.toThrow()
    expect(ops.state.assets).toHaveLength(assets)
    harness.bridge.snapshot = original
  })
})
