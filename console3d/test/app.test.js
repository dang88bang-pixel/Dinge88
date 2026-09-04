import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  click, installFrameControl, installOfflineFetch, installWebSocketStub,
  mountIndexHtml, press, tick
} from './setup/boot.js'
import { resetWorldCalls, worldCalls } from './setup/world.mock.js'

/**
 * Ende-zu-Ende-Test der Bedienung: Das komplette HUD wird aus dem echten
 * `index.html` gebootet, `src/main.js` verdrahtet es, und die Tests fahren die
 * Interaktionsketten so, wie eine Bedienerin es täte – Klick, Tastendruck,
 * Bestätigungsdialog.
 *
 * Ersetzt ist nur die WebGL-Szene (jsdom hat keinen WebGL-Kontext).
 */

vi.mock('../src/scene/world.js', async () => await import('./setup/world.mock.js'))

let ops
let frames

beforeAll(async () => {
  mountIndexHtml()
  frames = installFrameControl()
  installOfflineFetch()
  installWebSocketStub()

  await import('../src/main.js')
  await tick(30)          // Boot-IIFE abwarten (probe schlägt fehl → Simulation)
  frames.pump(3)
  ops = window.SecureGuardOps
})

beforeEach(() => {
  resetWorldCalls()
  ops.state.busyAction = null
})

/** Wartet, bis ein Befehl im Protokoll einen Endzustand erreicht hat. */
async function waitForCommand (actionId, timeout = 6000) {
  const start = Date.now()
  while (Date.now() - start < timeout) {
    const cmd = ops.state.commands.find(
      (c) => c.action === actionId && c.state !== 'running'
    )
    if (cmd) return cmd
    await tick(25)
  }
  throw new Error(`Befehl ${actionId} hat keinen Endzustand erreicht`)
}

/* ================================================================== */

describe('Kette 1 – Start der Konsole', () => {
  it('exponiert die Debug-Schnittstelle für QA', () => {
    expect(ops).toBeTruthy()
    expect(ops.state).toBeTruthy()
    expect(typeof ops.executeAction).toBe('function')
  })

  it('fällt ohne Backend auf die Simulation zurück', () => {
    expect(ops.state.source).toBe('simulation')
    expect(document.querySelector('#sourceLabel').textContent).toBe('Simulation')
  })

  it('lädt eine vollständige Flotte, statt leer zu bleiben', () => {
    expect(ops.state.assets.length).toBe(12)
    expect(worldCalls.syncAssets.length + 1).toBeGreaterThan(0)
  })

  it('wählt automatisch ein erstes Ziel aus', () => {
    expect(ops.state.selection.size).toBeGreaterThan(0)
    expect(document.querySelector('#dockTarget').textContent).not.toBe('Kein Ziel')
  })

  it('meldet den Startvorgang als abgeschlossen', () => {
    expect(document.querySelector('#bootStatus').textContent).toBe('Bereit')
  })

  it('rendert Kennzahlen, Kanäle, Assets und Aktionen', () => {
    expect(document.querySelectorAll('#kpiGrid > *').length).toBeGreaterThan(0)
    expect(document.querySelectorAll('#channelList > *').length).toBeGreaterThan(0)
    expect(document.querySelectorAll('#assetList > *').length).toBeGreaterThan(0)
    expect(document.querySelectorAll('#actionGrid > *').length).toBeGreaterThan(0)
    expect(document.querySelectorAll('#actionTabs > *').length).toBe(5)
  })
})

describe('Kette 2 – Ziele auswählen', () => {
  it('wechselt das Ziel per Klick in der Asset-Liste', () => {
    const rows = document.querySelectorAll('#assetList .asset')
    expect(rows.length).toBeGreaterThan(1)
    rows[1].dispatchEvent(new window.MouseEvent('click', { bubbles: true }))
    frames.pump(2)
    expect(ops.state.selection.size).toBe(1)
  })

  it('filtert die Liste über das Suchfeld', () => {
    const search = document.querySelector('#assetSearch')
    search.value = 'CARGO'
    search.dispatchEvent(new window.Event('input', { bubbles: true }))
    frames.pump(2)
    const names = [...document.querySelectorAll('#assetList .asset')]
      .map((n) => n.textContent)
    expect(names.length).toBeGreaterThan(0)
    expect(names.every((t) => t.includes('CARGO'))).toBe(true)

    search.value = ''
    search.dispatchEvent(new window.Event('input', { bubbles: true }))
    frames.pump(2)
    expect(document.querySelectorAll('#assetList .asset').length).toBe(12)
  })

  it('wählt über "Alle" die sichtbaren Assets aus', () => {
    click('#selectAll')
    frames.pump(2)
    expect(ops.state.selection.size).toBe(12)
    expect(document.querySelector('#dockTarget').textContent).toBe('12 Ziele')
  })

  it('hebt die Auswahl mit Escape auf', () => {
    press('Escape')
    frames.pump(2)
    expect(ops.state.selection.size).toBe(0)
    expect(document.querySelector('#dockTarget').textContent).toBe('Kein Ziel')
  })

  it('meldet den Klick auf ein Asset an das Lagebild weiter', () => {
    const rows = document.querySelectorAll('#assetList .asset')
    rows[0].dispatchEvent(new window.MouseEvent('click', { bubbles: true }))
    frames.pump(2)
    expect(worldCalls.setSelection.length).toBeGreaterThan(0)
  })
})

describe('Kette 3 – unkritische Aktion ausführen', () => {
  beforeEach(() => {
    // genau ein Online-Ziel, damit das Ergebnis eindeutig ist
    const online = ops.state.assets.find((a) => a.status === 'ONLINE')
    ops.actions.selectOnly(online.id)
    frames.pump(2)
  })

  it('führt LIGHT aus, protokolliert und meldet zurück', async () => {
    const before = ops.state.commands.length
    await ops.executeAction(ops.ACTION_MAP.LIGHT)

    expect(ops.state.commands.length).toBe(before + 1)
    const cmd = ops.state.commands[0]
    expect(cmd.action).toBe('LIGHT')
    expect(['delivered', 'queued', 'blocked']).toContain(cmd.state)

    frames.pump(2)
    expect(document.querySelectorAll('#toasts .toast').length).toBeGreaterThan(0)
    expect(ops.state.events[0].text).toContain('Blinken')
  })

  it('schreibt jeden Befehl ins Protokoll-Overlay', async () => {
    await ops.executeAction(ops.ACTION_MAP.POSITION)
    frames.pump(2)
    expect(document.querySelectorAll('#logList > *').length).toBeGreaterThan(0)
  })

  it('meldet fehlende Zielauswahl, statt still nichts zu tun', async () => {
    ops.actions.clearSelection()
    frames.pump(2)
    const before = ops.state.commands.length
    await ops.executeAction(ops.ACTION_MAP.ALARM)
    expect(ops.state.commands.length).toBe(before)
    const toasts = [...document.querySelectorAll('#toasts .toast')].map((t) => t.textContent)
    expect(toasts.some((t) => t.includes('Kein Ziel'))).toBe(true)
  })

  it('führt eine Aktion auf mehreren Zielen aus', async () => {
    const ids = ops.state.assets.slice(0, 3).map((a) => a.id)
    ops.actions.selectAllVisible(ids)
    frames.pump(2)
    const before = ops.state.commands.length
    await ops.executeAction(ops.ACTION_MAP.BATTERY)
    expect(ops.state.commands.length).toBe(before + 3)
  })
})

describe('Kette 4 – kritische Aktion mit Bestätigung', () => {
  beforeEach(() => {
    const online = ops.state.assets.find((a) => a.status === 'ONLINE')
    ops.actions.selectOnly(online.id)
    frames.pump(2)
  })

  it('öffnet den Dialog und führt erst nach Bestätigung aus', async () => {
    const modal = document.querySelector('#modal')
    expect(modal.hidden).toBe(true)

    const before = ops.state.commands.length
    const run = ops.executeAction(ops.ACTION_MAP.MOTOR_OFF)
    await tick(0)

    expect(modal.hidden, 'Dialog muss erscheinen').toBe(false)
    expect(document.querySelector('#modalTitle').textContent)
      .toBe('Motor wirklich abschalten?')
    expect(document.querySelector('#modalText').textContent).toContain('Unfallgefahr')
    expect(document.querySelector('#modalTargets').textContent).toContain('Ziele:')
    expect(ops.state.commands.length, 'vor der Bestätigung darf nichts laufen').toBe(before)

    click('#modalConfirm')
    await run
    expect(ops.state.commands.length).toBe(before + 1)
    expect(ops.state.commands[0].action).toBe('MOTOR_OFF')
    expect(document.querySelector('#modal').hidden).toBe(true)
  })

  it('bricht bei Abbruch folgenlos ab', async () => {
    const before = ops.state.commands.length
    const run = ops.executeAction(ops.ACTION_MAP.RESTART)
    await tick(0)
    expect(document.querySelector('#modal').hidden).toBe(false)

    click('#modalCancel')
    await run
    expect(ops.state.commands.length, 'kein Befehl nach Abbruch').toBe(before)
    const toasts = [...document.querySelectorAll('#toasts .toast')].map((t) => t.textContent)
    expect(toasts.some((t) => t.includes('Abgebrochen'))).toBe(true)
  })

  it('blockiert kritische Aktionen auf Offline-Zielen', async () => {
    const offline = ops.state.assets.find((a) => a.status === 'OFFLINE')
      || Object.assign(ops.state.assets[3], { status: 'OFFLINE', rssi: 0 })
    ops.actions.selectOnly(offline.id)
    frames.pump(2)

    const run = ops.executeAction(ops.ACTION_MAP.MOTOR_OFF)
    await tick(0)
    click('#modalConfirm')
    await run

    const cmd = await waitForCommand('MOTOR_OFF')
    expect(cmd.state).toBe('blocked')
  })
})

describe('Kette 5 – Freitext-Nachricht', () => {
  it('überträgt die Notiz und leert das Feld danach', async () => {
    const online = ops.state.assets.find((a) => a.status === 'ONLINE')
    ops.actions.selectOnly(online.id)
    ops.actions.setCategory('SIGNAL')
    frames.pump(2)

    const input = document.querySelector('#noteInput')
    expect(document.querySelector('#noteRow').hidden,
      'Notizfeld muss bei SIGNAL sichtbar sein').toBe(false)

    input.value = 'Bitte am Hafentor abstellen'
    input.dispatchEvent(new window.Event('input', { bubbles: true }))
    expect(ops.state.note).toBe('Bitte am Hafentor abstellen')
    expect(document.querySelector('#noteCount').textContent).toBe('27/120')

    await ops.executeAction(ops.ACTION_MAP.MESSAGE)
    expect(ops.state.note, 'Notiz wird nach dem Senden geleert').toBe('')
    expect(document.querySelector('#noteInput').value).toBe('')
  })

  it('blendet das Notizfeld in Kategorien ohne Freitext aus', () => {
    ops.actions.setCategory('QUERY')
    frames.pump(2)
    expect(document.querySelector('#noteRow').hidden).toBe(true)
    ops.actions.setCategory('SIGNAL')
    frames.pump(2)
  })
})

describe('Kette 6 – Lagebild-Aktionen', () => {
  it('startet den Radar-Sweep und erzeugt Detektionen', async () => {
    const before = ops.state.detections.length
    await ops.executeAction(ops.ACTION_MAP.SWEEP)
    expect(worldCalls.runSweep).toBe(1)
    await tick(400)
    expect(ops.state.detections.length).toBeGreaterThan(before)
  })

  it('fliegt das primäre Ziel an', async () => {
    const online = ops.state.assets.find((a) => a.status !== 'OFFLINE')
    ops.actions.selectOnly(online.id)
    frames.pump(2)
    await ops.executeAction(ops.ACTION_MAP.FOCUS)
    expect(worldCalls.focus).toContain(online.id)
  })

  it('schaltet Geofence und Heatmap um', async () => {
    const geoBefore = ops.state.overlays.geofence
    await ops.executeAction(ops.ACTION_MAP.GEOFENCE)
    expect(ops.state.overlays.geofence).toBe(!geoBefore)
    expect(worldCalls.setGeofence.length).toBeGreaterThan(0)

    const heatBefore = ops.state.overlays.heatmap
    await ops.executeAction(ops.ACTION_MAP.HEATMAP)
    expect(ops.state.overlays.heatmap).toBe(!heatBefore)
    expect(worldCalls.setHeatmap.length).toBeGreaterThan(0)
  })

  it('erzeugt für Lagebild-Aktionen keinen Funkbefehl', async () => {
    const before = ops.state.commands.length
    await ops.executeAction(ops.ACTION_MAP.SWEEP)
    expect(ops.state.commands.length).toBe(before)
  })
})

describe('Kette 7 – Tastatur', () => {
  it('startet und stoppt den Agenten mit der Leertaste', () => {
    const before = ops.state.agent.running
    press(' ')
    expect(ops.state.agent.running).toBe(!before)
    press(' ')
    expect(ops.state.agent.running).toBe(before)
  })

  it('öffnet und schließt Protokoll und Alarme', () => {
    // Die Schubladen werden über das hidden-Attribut geschaltet, nicht über
    // eine Klasse – so bleiben sie auch für Screenreader korrekt ausgeblendet.
    const log = document.querySelector('#logDrawer')
    const alerts = document.querySelector('#alertDrawer')
    expect(log.hidden).toBe(true)

    press('l')
    expect(log.hidden).toBe(false)
    press('l')
    expect(log.hidden).toBe(true)

    press('a')
    expect(alerts.hidden).toBe(false)
    press('a')
    expect(alerts.hidden).toBe(true)
  })

  it('schließt eine Schublade über ihren Schließen-Knopf', () => {
    press('l')
    expect(document.querySelector('#logDrawer').hidden).toBe(false)
    click('#logClose')
    expect(document.querySelector('#logDrawer').hidden).toBe(true)

    click('#btnAlerts')
    expect(document.querySelector('#alertDrawer').hidden).toBe(false)
    click('#alertClose')
    expect(document.querySelector('#alertDrawer').hidden).toBe(true)
  })

  it('wechselt die Ansicht mit V', () => {
    const before = ops.state.view
    press('v')
    expect(ops.state.view).not.toBe(before)
    expect(worldCalls.setView.length).toBe(1)
  })

  it('zeigt mit H die Tastenübersicht', () => {
    press('h')
    const toasts = [...document.querySelectorAll('#toasts .toast')].map((t) => t.textContent)
    expect(toasts.some((t) => t.includes('⌘K Palette'))).toBe(true)
  })

  it('löst Zifferntasten als Befehle aus', async () => {
    const online = ops.state.assets.find((a) => a.status === 'ONLINE')
    ops.actions.selectOnly(online.id)
    frames.pump(2)
    const before = ops.state.commands.length
    press('2') // LIGHT
    await tick(800)
    expect(ops.state.commands.length).toBeGreaterThan(before)
    expect(ops.state.commands[0].action).toBe('LIGHT')
  })

  it('ignoriert Tastenkürzel während einer Texteingabe', () => {
    const input = document.querySelector('#noteInput')
    const before = ops.state.agent.running
    press(' ', { target: input })
    expect(ops.state.agent.running, 'Leertaste im Textfeld darf den Agenten nicht schalten')
      .toBe(before)
  })
})

describe('Kette 8 – Befehlspalette', () => {
  it('öffnet sich mit Strg+K und schließt mit Escape', () => {
    press('k', { ctrlKey: true })
    expect(document.querySelector('#palette').hidden).toBe(false)
    press('Escape')
    expect(document.querySelector('#palette').hidden).toBe(true)
  })

  it('listet Aktionen, Ziele und Ansichten', () => {
    press('k', { ctrlKey: true })
    const entries = [...document.querySelectorAll('#paletteList > *')].map((n) => n.textContent)
    expect(entries.length).toBeGreaterThan(10)
    expect(entries.some((t) => t.includes('Alarm auslösen'))).toBe(true)
    expect(entries.some((t) => t.includes('Ziel:'))).toBe(true)
    expect(entries.some((t) => t.includes('Ansicht'))).toBe(true)
    press('Escape')
  })

  it('filtert die Einträge über die Eingabe', () => {
    press('k', { ctrlKey: true })
    const input = document.querySelector('#paletteInput')
    input.value = 'geofence'
    input.dispatchEvent(new window.Event('input', { bubbles: true }))
    const entries = [...document.querySelectorAll('#paletteList > *')].map((n) => n.textContent)
    expect(entries.length).toBeGreaterThan(0)
    expect(entries.every((t) => t.toLowerCase().includes('geofence'))).toBe(true)
    press('Escape')
  })

  it('führt einen Eintrag per Klick aus', () => {
    const before = ops.state.overlays.heatmap
    press('k', { ctrlKey: true })
    const input = document.querySelector('#paletteInput')
    input.value = 'heatmap'
    input.dispatchEvent(new window.Event('input', { bubbles: true }))
    const first = document.querySelector('#paletteList > *')
    first.dispatchEvent(new window.MouseEvent('click', { bubbles: true }))
    expect(document.querySelector('#palette').hidden).toBe(true)
    expect(ops.state.overlays.heatmap).toBe(!before)
  })
})

describe('Kette 9 – Alarme', () => {
  it('zeigt offene Alarme mit Zähler und quittiert einzeln', () => {
    ops.actions.addAlert({
      id: 'test-alert-1', assetId: ops.state.assets[0].id, assetName: 'TEST',
      type: 'GEOFENCE', severity: 'CRITICAL', acknowledged: false,
      message: 'Testalarm für die Quittierung', ts: Date.now()
    })
    frames.pump(2)

    const badge = document.querySelector('#alertBadge')
    expect(Number(badge.textContent)).toBeGreaterThan(0)

    press('a')
    const items = [...document.querySelectorAll('#alertList > *')]
    expect(items.some((n) => n.textContent.includes('Testalarm'))).toBe(true)
    press('a')
  })

  it('quittiert alle Alarme auf einmal', () => {
    ops.actions.addAlert({
      id: 'test-alert-2', assetId: ops.state.assets[0].id, assetName: 'TEST',
      type: 'SECURITY', severity: 'WARNING', acknowledged: false,
      message: 'Zweiter Testalarm', ts: Date.now()
    })
    frames.pump(2)
    expect(ops.state.alerts.filter((a) => !a.acknowledged).length).toBeGreaterThan(0)

    press('a')
    click('#alertAckAll')
    frames.pump(2)
    expect(ops.state.alerts.filter((a) => !a.acknowledged).length).toBe(0)
    press('a')
  })
})

describe('Kette 10 – Offline-Warteschlange', () => {
  it('meldet eine leere Warteschlange, statt nichts zu tun', async () => {
    ops.actions.setQueue(0)
    click('#btnQueueFlush')
    await tick(50)
    const toasts = [...document.querySelectorAll('#toasts .toast')].map((t) => t.textContent)
    expect(toasts.some((t) => t.includes('Warteschlange ist leer'))).toBe(true)
  })

  it('stellt eingereihte Befehle zu und aktualisiert die Anzeige', async () => {
    ops.actions.setQueue(4)
    frames.pump(2)
    expect(document.querySelector('#queueTag').textContent).toBe('Queue 4')

    click('#btnQueueFlush')
    await tick(700)
    frames.pump(2)
    expect(ops.state.queue).toBeLessThan(4)
    expect(ops.state.events[0].text).toContain('Offline-Queue')
  })
})

describe('Kette 11 – Kopfzeile und Ansichten', () => {
  it('schaltet den Agenten über die Kopfzeile', () => {
    const before = ops.state.agent.running
    click('#agentToggle')
    frames.pump(2)
    expect(ops.state.agent.running).toBe(!before)
    expect(document.querySelector('#agentLabel').textContent)
      .toBe(ops.state.agent.running ? 'Agent aktiv' : 'Agent gestoppt')
    click('#agentToggle')
    frames.pump(2)
  })

  it('durchläuft alle drei Ansichten', () => {
    const seen = new Set([ops.state.view])
    click('#btnView'); seen.add(ops.state.view)
    click('#btnView'); seen.add(ops.state.view)
    expect([...seen].sort()).toEqual(['orbit', 'tactical', 'top'])
  })

  it('meldet beim Quellenwechsel ohne Backend zurück in die Simulation', async () => {
    click('#btnSource')
    await tick(120)
    expect(ops.state.source).toBe('simulation')
    const toasts = [...document.querySelectorAll('#toasts .toast')].map((t) => t.textContent)
    expect(toasts.some((t) => t.includes('Kein Backend erreichbar'))).toBe(true)
  })
})

describe('Kette 12 – Robustheit', () => {
  it('überlebt einen Befehl auf ein Asset ohne Koordinaten', async () => {
    const asset = ops.state.assets[0]
    const lat = asset.lat; const lon = asset.lon
    asset.lat = null; asset.lon = null
    ops.actions.selectOnly(asset.id)
    frames.pump(2)
    await expect(ops.executeAction(ops.ACTION_MAP.POSITION)).resolves.toBeUndefined()
    asset.lat = lat; asset.lon = lon
  })

  it('zeichnet das Lagebild ohne Ausnahme neu', () => {
    expect(() => frames.pump(5)).not.toThrow()
    expect(worldCalls.update).toBeGreaterThan(0)
  })

  it('verliert die Bildlaufposition der Asset-Liste nicht beim Neuzeichnen', () => {
    const list = document.querySelector('#assetList')
    list.scrollTop = 40
    ops.actions.setAssets([...ops.state.assets])
    frames.pump(2)
    expect(list.scrollTop).toBe(40)
  })

  it('hält den Sperrzustand busyAction sauber', async () => {
    const online = ops.state.assets.find((a) => a.status === 'ONLINE')
    ops.actions.selectOnly(online.id)
    frames.pump(2)
    const run = ops.executeAction(ops.ACTION_MAP.TELEMETRY)
    await tick(10)
    expect(ops.state.busyAction).toBe('TELEMETRY')
    await run
    expect(ops.state.busyAction).toBeNull()
  })
})
