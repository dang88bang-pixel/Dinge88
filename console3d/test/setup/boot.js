import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

/**
 * Bootet das HUD in jsdom mit dem echten `index.html`.
 *
 * Bewusst nicht mit einem vereinfachten Test-DOM: Die IDs in `index.html` sind
 * der Vertrag zwischen Markup und JavaScript. Fehlt eine, muss der Test
 * fehlschlagen – genau das würde im Browser einen weißen Bildschirm ergeben.
 */

const here = dirname(fileURLToPath(import.meta.url))
const INDEX_HTML = resolve(here, '../../index.html')

export function mountIndexHtml () {
  const html = readFileSync(INDEX_HTML, 'utf8')
  const body = html.slice(html.indexOf('<body>') + 6, html.lastIndexOf('</body>'))
  // Das Modul-Script wird von jsdom ohnehin nicht ausgeführt; entfernen, damit
  // keine irreführende Ressourcenanfrage entsteht.
  document.body.innerHTML = body.replace(/<script[\s\S]*?<\/script>/g, '')
}

/** Rendert die Warteschlange der Animationsframes kontrolliert statt endlos. */
export function installFrameControl () {
  const frames = []
  globalThis.requestAnimationFrame = (cb) => { frames.push(cb); return frames.length }
  globalThis.cancelAnimationFrame = () => {}
  return {
    /** Führt genau einen Frame aus (main.js plant im Callback den nächsten). */
    pump (times = 1) {
      for (let i = 0; i < times; i++) {
        const cb = frames.shift()
        if (cb) cb(performance.now())
      }
    },
    get pending () { return frames.length }
  }
}

/** Kein Backend: `probe()` scheitert, die Konsole fällt auf Simulation zurück. */
export function installOfflineFetch () {
  globalThis.fetch = () => Promise.reject(new Error('kein Backend im Test'))
}

/** jsdom kennt WebSocket, soll aber nichts öffnen. */
export function installWebSocketStub () {
  const instances = []
  class FakeSocket {
    constructor (url) {
      this.url = url
      this.readyState = 1
      instances.push(this)
    }

    close () { this.readyState = 3 }
  }
  globalThis.WebSocket = FakeSocket
  return instances
}

/** Wartet, bis die Mikrotask-Warteschlange und kurze Timer abgearbeitet sind. */
export const tick = (ms = 0) => new Promise((r) => setTimeout(r, ms))

/** Simuliert einen Tastendruck auf Fensterebene. */
export function press (key, init = {}) {
  const event = new window.KeyboardEvent('keydown', {
    key, bubbles: true, cancelable: true, ...init
  })
  ;(init.target || window).dispatchEvent(event)
  return event
}

export function click (selector) {
  const node = document.querySelector(selector)
  if (!node) throw new Error(`Element ${selector} fehlt im HUD`)
  node.dispatchEvent(new window.MouseEvent('click', { bubbles: true, cancelable: true }))
  return node
}
