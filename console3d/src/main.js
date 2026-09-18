// SecureGuard Ops Center — entry point + native bridge glue.
//
// window.SecureGuardOps is the JS side of the bridge:
//   - ingest(snapshot)          native/backend snapshot in
//   - execute(action, assetIds) JS-driven invocation is (re-)routed through
//                               the native SecureGuardNative bridge via ops.js
//   - event handling            SecureGuardNative.onEvent(str)
//
// window.SecureGuardNative (Kotlin @JavascriptInterface) is detected here and
// wrapped so the scene/HUD modules never touch the WebView API directly.
import { createScene } from './scene.js'
import { installHud, renderMetrics } from './hud.js'
import { boot, ingest, nativeReady, execute, event, queueEvent, snapshot, getState, on, emit } from './ops.js'

let scene = null

function detectNativeBridge() {
  const cb = window.SecureGuardNative
  if (!cb || typeof cb !== 'object') return false
  try {
    if (typeof cb.ready === 'function') cb.ready()
    if (typeof cb.onReady === 'function') cb.onReady('SecureGuardOps detected Android WebView bridge')
  } catch (e) {
    console.warn('native ready callback failed', e)
  }
  return true
}

// ---- window.SecureGuardOps (JS bridge surface) --------------------------
window.SecureGuardOps = {
  ingest(snapshotJson) {
    const got = ingest(snapshotJson)
    // Keep the renderer in sync right away.
    if (scene) {
      scene.refreshAssets(got.assets, Array.from(getState().selected))
      scene.setPylonUsage(got.channelLoad)
    }
    return got
  },
  execute(action, assetIds) {
    // Sends the action through the native bridge when available; scene
    // actions stay presentation-only. Mirrors ops.execute().
    const ids = Array.isArray(assetIds) ? assetIds : []
    const record = window.__SG_DISPATCH_PENDING__
    try {
      const native = window.SecureGuardNative
      if (native && typeof native.action === 'function') {
        native.action(JSON.stringify({ action: String(action), assetIds: ids }))
      }
    } catch (e) {
      console.warn('native.action failed', e)
    }
    void record
    return execute(action, ids)
  },
  onNativeEvent(json) {
    try {
      const data = typeof json === 'string' ? JSON.parse(json) : json
      event(data || {})
    } catch (e) {
      console.warn('onNativeEvent parse error', e)
    }
    return true
  },
  onQueueEvent(json) {
    try {
      const data = typeof json === 'string' ? JSON.parse(json) : json
      queueEvent(data || {})
    } catch (e) {
      console.warn('onQueueEvent parse error', e)
    }
    return true
  },
  onError(message) {
    emit('bridge-error', String(message || 'native error'))
    return true
  },
  snapshot() {
    return snapshot()
  }
}

// ---- wire up ------------------------------------------------------------
const hasNative = detectNativeBridge()

const app = document.getElementById('app')
const labels = document.createElement('div')
labels.id = 'labels-root'
labels.style.cssText = 'position:absolute;inset:0;pointer-events:none;overflow:visible;'
app.appendChild(labels)

scene = createScene(app, labels)
installHud()
scene.start()

// First paint immediately, then apply data (native snapshot or simulation
// fallback) once the bridge confirms readiness / provides a snapshot.
renderMetrics()
scene.refreshAssets(getState().assets, [])
scene.setPylonUsage(getState().channelLoad)

boot()

// Keep the scene in sync with every new snapshot.
on('snapshot', (s) => {
  if (scene) {
    scene.refreshAssets(s.assets, Array.from(getState().selected))
    scene.setPylonUsage(s.channelLoad)
  }
})
on('selection', (ids) => {
  if (scene) scene.refreshAssets(getState().assets, ids)
})

// Also wire legacy window.SecureGuardOps callbacks used by the native layer.
window.__SG_HAS_NATIVE__ = hasNative
if (!hasNative && typeof window.SecureGuardNative !== 'undefined') {
  console.warn('SecureGuardNative object present but missing expected methods')
}

// Assist the Kotlin side: it may look for window.SecureGuardOps after
// onPageFinished. Expose a readiness flag.
window.SecureGuardOps.ready = true

// Pause the render loop when the document is hidden (screen pushed to
// background / WebView paused) and resume on visibility — never burn CPU
// while the user is not looking at the scene.
document.addEventListener('visibilitychange', () => {
  if (!scene) return
  if (document.hidden) scene.stop()
  else scene.start()
})
