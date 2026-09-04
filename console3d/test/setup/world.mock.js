/**
 * Ersatz für `src/scene/world.js` in der Testumgebung.
 *
 * jsdom hat keinen WebGL-Kontext. Die Szene ist in der Anwendung bewusst hinter
 * einer schmalen Schnittstelle gekapselt; dieses Doppel bildet sie vollständig
 * ab und protokolliert die Aufrufe, damit Tests prüfen können, dass die
 * Bedienlogik das Lagebild korrekt ansteuert.
 */

export const worldCalls = {
  focus: [],
  pulseDetection: [],
  setSelection: [],
  syncAssets: [],
  setAgentRunning: [],
  setView: [],
  setGeofence: [],
  setHeatmap: [],
  runSweep: 0,
  update: 0,
  resize: 0
}

export function resetWorldCalls () {
  worldCalls.focus.length = 0
  worldCalls.pulseDetection.length = 0
  worldCalls.setSelection.length = 0
  worldCalls.syncAssets.length = 0
  worldCalls.setAgentRunning.length = 0
  worldCalls.setView.length = 0
  worldCalls.setGeofence.length = 0
  worldCalls.setHeatmap.length = 0
  worldCalls.runSweep = 0
  worldCalls.update = 0
  worldCalls.resize = 0
}

/** Wird von main.js aufgerufen; `onSelect` merken wir uns für Klick-Simulation. */
export let lastOnSelect = null

export function createWorld (_canvas, _labels, options = {}) {
  lastOnSelect = options.onSelect || null
  return {
    focus: (id) => worldCalls.focus.push(id),
    pulseDetection: (assetId, source) => worldCalls.pulseDetection.push({ assetId, source }),
    setSelection: (ids) => worldCalls.setSelection.push([...ids]),
    syncAssets: (assets) => worldCalls.syncAssets.push(assets.length),
    setAgentRunning: (running) => worldCalls.setAgentRunning.push(running),
    setView: (view) => worldCalls.setView.push(view),
    setGeofence: (on) => worldCalls.setGeofence.push(on),
    setHeatmap: (on) => worldCalls.setHeatmap.push(on),
    runSweep: () => { worldCalls.runSweep++ },
    update: () => { worldCalls.update++ },
    resize: () => { worldCalls.resize++ }
  }
}
