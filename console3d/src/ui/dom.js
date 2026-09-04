/** Kleine DOM- und Formatierungs-Helfer (bewusst ohne Framework). */

export const $ = (selector, root = document) => root.querySelector(selector)
export const $$ = (selector, root = document) => [...root.querySelectorAll(selector)]

export function el (tag, attrs = {}, children = []) {
  const node = document.createElement(tag)
  for (const [key, value] of Object.entries(attrs)) {
    if (key === 'class') node.className = value
    else if (key === 'html') node.innerHTML = value
    else if (key === 'text') node.textContent = value
    else if (key === 'style') Object.assign(node.style, value)
    else if (key.startsWith('on') && typeof value === 'function') {
      node.addEventListener(key.slice(2).toLowerCase(), value)
    } else if (value !== null && value !== undefined && value !== false) {
      node.setAttribute(key, value === true ? '' : value)
    }
  }
  for (const child of [].concat(children)) {
    if (child == null) continue
    node.append(child.nodeType ? child : document.createTextNode(String(child)))
  }
  return node
}

export function esc (value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
  ))
}

export const clock = (ts) =>
  new Date(ts).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit', second: '2-digit' })

export function relative (ts) {
  if (!ts) return 'nie'
  const diff = Date.now() - ts
  const s = Math.floor(diff / 1000)
  if (s < 45) return 'gerade eben'
  const m = Math.floor(s / 60)
  if (m < 60) return `vor ${m} Min.`
  const h = Math.floor(m / 60)
  if (h < 24) return `vor ${h} Std.`
  return `vor ${Math.floor(h / 24)} Tg.`
}

export function duration (ms) {
  if (!ms || ms < 0) return '–'
  const s = Math.floor(ms / 1000)
  const m = Math.floor(s / 60)
  const h = Math.floor(m / 60)
  if (s < 60) return `${s}s`
  if (m < 60) return `${m}m ${s % 60}s`
  return `${h}h ${m % 60}m`
}

/** RSSI (dBm) → 0..4 Balken. */
export function bars (rssi) {
  if (!rssi) return 0
  if (rssi > -55) return 4
  if (rssi > -68) return 3
  if (rssi > -80) return 2
  return 1
}

export function rssiColor (rssi) {
  if (!rssi) return '#78909c'
  if (rssi > -60) return '#00e676'
  if (rssi > -75) return '#ffc400'
  return '#ff4d6a'
}

export function batteryColor (pct) {
  if (pct == null) return '#78909c'
  if (pct >= 60) return '#00e676'
  if (pct >= 25) return '#ffc400'
  return '#ff4d6a'
}

/** Zeichnet eine Sparkline in ein <canvas>. */
export function sparkline (canvas, values, color) {
  if (!canvas || typeof canvas.getContext !== 'function') return
  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  const w = canvas.clientWidth || 120
  const h = canvas.clientHeight || 24
  canvas.width = w * dpr
  canvas.height = h * dpr
  // getContext() liefert null, wenn der Browser das Kontingent an
  // 2D-Kontexten erschöpft hat. Die Sparkline ist Dekoration – sie darf das
  // Rendern des restlichen HUD niemals abbrechen.
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.scale(dpr, dpr)
  ctx.clearRect(0, 0, w, h)
  if (!values || values.length < 2) return

  const max = Math.max(...values, 1)
  const step = w / (values.length - 1)
  const y = (v) => h - 2 - (v / max) * (h - 5)

  ctx.beginPath()
  ctx.moveTo(0, y(values[0]))
  values.forEach((v, i) => ctx.lineTo(i * step, y(v)))

  const area = ctx.createLinearGradient(0, 0, 0, h)
  area.addColorStop(0, hexAlpha(color, 0.35))
  area.addColorStop(1, hexAlpha(color, 0))
  ctx.lineTo(w, h)
  ctx.lineTo(0, h)
  ctx.closePath()
  ctx.fillStyle = area
  ctx.fill()

  ctx.beginPath()
  ctx.moveTo(0, y(values[0]))
  values.forEach((v, i) => ctx.lineTo(i * step, y(v)))
  ctx.strokeStyle = color
  ctx.lineWidth = 1.6
  ctx.lineJoin = 'round'
  ctx.stroke()

  ctx.beginPath()
  ctx.arc(w, y(values[values.length - 1]), 2.2, 0, Math.PI * 2)
  ctx.fillStyle = color
  ctx.fill()
}

export function hexAlpha (hex, alpha) {
  const value = hex.replace('#', '')
  const full = value.length === 3 ? value.split('').map((c) => c + c).join('') : value
  const r = parseInt(full.slice(0, 2), 16)
  const g = parseInt(full.slice(2, 4), 16)
  const b = parseInt(full.slice(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}
