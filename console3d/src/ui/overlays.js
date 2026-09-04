/** Überlagerungen: Protokoll-Drawer, Alarm-Drawer, Befehlspalette, Modal, Toasts. */

import { actions, selectors, state } from '../core/store.js'
import { $, clock, el, esc, relative } from './dom.js'

const LOG_FILTERS = [
  { id: 'all', label: 'Alle' },
  { id: 'delivered', label: 'Zugestellt' },
  { id: 'queued', label: 'Queue' },
  { id: 'problem', label: 'Probleme' }
]

const SEVERITY_COLOR = { CRITICAL: '#ff4d6a', WARNING: '#ffc400', INFO: '#00d4ff' }

export function createOverlays ({ onPaletteCommand }) {
  let logFilter = 'all'

  /* ---------------- Protokoll ---------------- */

  const logDrawer = $('#logDrawer')
  const logList = $('#logList')
  const logFilterBox = $('#logFilters')

  logFilterBox.replaceChildren(...LOG_FILTERS.map((f) =>
    el('button', {
      class: f.id === logFilter ? 'is-active' : '',
      text: f.label,
      onclick: () => {
        logFilter = f.id
        const children = [...logFilterBox.children]
        children.forEach((c, i) => c.classList.toggle('is-active', LOG_FILTERS[i].id === f.id))
        renderLog()
      }
    })
  ))

  function filteredCommands () {
    switch (logFilter) {
      case 'delivered': return state.commands.filter((c) => c.state === 'delivered')
      case 'queued': return state.commands.filter((c) => c.state === 'queued')
      case 'problem': return state.commands.filter((c) => c.state === 'denied' || c.state === 'blocked')
      default: return state.commands
    }
  }

  function renderLog () {
    const rows = filteredCommands()
    if (!rows.length) {
      logList.replaceChildren(el('li', { style: { gridTemplateColumns: '1fr', color: '#64788f' } },
        ['Noch keine Befehle in dieser Ansicht.']))
      return
    }
    logList.replaceChildren(...rows.slice(0, 120).map((cmd) =>
      el('li', {}, [
        el('time', { text: clock(cmd.ts) }),
        el('span', { html: `${esc(cmd.title)} → <b>${esc(cmd.assetName)}</b>` +
          (cmd.detail ? ` <span style="color:#64788f">· ${esc(cmd.detail)}</span>` : '') }),
        el('span', { class: `st st-${cmd.state}`, text: cmd.state })
      ])
    ))
  }

  /* ---------------- Alarme ---------------- */

  const alertDrawer = $('#alertDrawer')
  const alertList = $('#alertList')

  function renderAlerts () {
    const badge = $('#alertBadge')
    const open = selectors.unacknowledgedAlerts().length
    badge.textContent = open
    badge.classList.toggle('is-zero', open === 0)

    if (!state.alerts.length) {
      alertList.replaceChildren(el('div', { class: 'selinfo', text: 'Keine Alarme – alles ruhig.' }))
      return
    }
    alertList.replaceChildren(...state.alerts.slice(0, 60).map((alert) =>
      el('li', { class: `alertrow${alert.acknowledged ? ' is-ack' : ''}` }, [
        el('div', {
          class: 'alertrow__bar',
          style: { background: SEVERITY_COLOR[alert.severity] || '#00d4ff' }
        }),
        el('div', {}, [
          el('div', { class: 'alertrow__msg', text: alert.message }),
          el('div', {
            class: 'alertrow__meta',
            text: `${alert.type} · ${alert.severity} · ${relative(alert.ts)}`
          })
        ]),
        alert.acknowledged
          ? el('span', { class: 'tag', text: 'ok' })
          : el('button', {
            class: 'linkbtn',
            text: 'Bestätigen',
            onclick: () => actions.ackAlert(alert.id)
          })
      ])
    ))
  }

  /* ---------------- Toasts ---------------- */

  const toastBox = $('#toasts')

  function toast (message, kind = 'info') {
    const icon = kind === 'error' ? '⛔' : kind === 'ok' ? '✅' : 'ℹ️'
    const node = el('div', { class: `toast is-${kind}` }, [
      el('span', { class: 'toast__ico', text: icon }),
      el('span', { text: message })
    ])
    toastBox.append(node)
    setTimeout(() => {
      node.style.transition = 'opacity .3s, transform .3s'
      node.style.opacity = '0'
      node.style.transform = 'translateY(8px)'
      setTimeout(() => node.remove(), 320)
    }, 3200)
  }

  /* ---------------- Modal ---------------- */

  const modal = $('#modal')
  let confirmResolver = null

  $('#modalCancel').addEventListener('click', () => resolveModal(false))
  $('#modalConfirm').addEventListener('click', () => resolveModal(true))
  modal.addEventListener('click', (e) => { if (e.target === modal) resolveModal(false) })

  function resolveModal (value) {
    modal.hidden = true
    confirmResolver?.(value)
    confirmResolver = null
  }

  function confirm ({ title, text, targets, confirmLabel = 'Ausführen', icon = '⚠️' }) {
    $('#modalIcon').textContent = icon
    $('#modalTitle').textContent = title
    $('#modalText').textContent = text
    $('#modalTargets').innerHTML = targets ? `Ziele: ${targets}` : ''
    $('#modalConfirm').textContent = confirmLabel
    modal.hidden = false
    return new Promise((resolve) => { confirmResolver = resolve })
  }

  /* ---------------- Befehlspalette ---------------- */

  const palette = $('#palette')
  const paletteInput = $('#paletteInput')
  const paletteList = $('#paletteList')
  let paletteItems = []
  let paletteIndex = 0

  function openPalette (items) {
    paletteItems = items
    paletteIndex = 0
    paletteInput.value = ''
    palette.hidden = false
    renderPalette('')
    paletteInput.focus()
  }

  function closePalette () {
    palette.hidden = true
  }

  function renderPalette (query) {
    const q = query.trim().toLowerCase()
    const matches = paletteItems.filter((item) =>
      !q || item.label.toLowerCase().includes(q) || (item.hint || '').toLowerCase().includes(q))
    paletteIndex = Math.min(paletteIndex, Math.max(0, matches.length - 1))
    paletteList.replaceChildren(...matches.map((item, i) =>
      el('li', {
        class: i === paletteIndex ? 'is-active' : '',
        onmouseenter: () => { paletteIndex = i; renderPalette(paletteInput.value) },
        onclick: () => { closePalette(); item.run() }
      }, [
        el('span', { class: 'pk', text: item.icon || '›' }),
        el('span', { text: item.label }),
        el('span', { class: 'ps', text: item.hint || '' })
      ])
    ))
    paletteList._matches = matches
  }

  paletteInput.addEventListener('input', (e) => renderPalette(e.target.value))
  paletteInput.addEventListener('keydown', (e) => {
    const matches = paletteList._matches || []
    if (e.key === 'ArrowDown') { paletteIndex = Math.min(matches.length - 1, paletteIndex + 1); renderPalette(paletteInput.value); e.preventDefault() }
    else if (e.key === 'ArrowUp') { paletteIndex = Math.max(0, paletteIndex - 1); renderPalette(paletteInput.value); e.preventDefault() }
    else if (e.key === 'Enter') { const item = matches[paletteIndex]; closePalette(); item?.run(); e.preventDefault() }
    else if (e.key === 'Escape') closePalette()
  })
  palette.addEventListener('click', (e) => { if (e.target === palette) closePalette() })

  /* ---------------- Verdrahtung ---------------- */

  $('#logClose').addEventListener('click', () => toggleLog(false))
  $('#logClear').addEventListener('click', () => { actions.clearCommands(); toast('Protokoll geleert') })
  $('#logCopy').addEventListener('click', async () => {
    const text = state.commands.map((c) =>
      `${clock(c.ts)}  ${c.title} → ${c.assetName}  [${c.state}] ${c.detail || ''}`).join('\n')
    try {
      await navigator.clipboard.writeText(text || '(leer)')
      toast('Protokoll in die Zwischenablage kopiert', 'ok')
    } catch {
      toast('Zwischenablage nicht verfügbar', 'error')
    }
  })
  $('#alertClose').addEventListener('click', () => toggleAlerts(false))
  $('#alertAckAll').addEventListener('click', () => { actions.ackAllAlerts(); toast('Alle Alarme bestätigt', 'ok') })

  function toggleLog (force) {
    const show = force ?? logDrawer.hidden
    logDrawer.hidden = !show
    $('#btnLog').classList.toggle('is-active', show)
    if (show) renderLog()
  }

  function toggleAlerts (force) {
    const show = force ?? alertDrawer.hidden
    alertDrawer.hidden = !show
    $('#btnAlerts').classList.toggle('is-active', show)
    if (show) renderAlerts()
  }

  $('#btnLog').addEventListener('click', () => toggleLog())
  $('#btnAlerts').addEventListener('click', () => toggleAlerts())
  $('#btnPalette').addEventListener('click', () => onPaletteCommand())

  return {
    renderLog, renderAlerts, toast, confirm,
    openPalette, closePalette, toggleLog, toggleAlerts,
    isPaletteOpen: () => !palette.hidden,
    isModalOpen: () => !modal.hidden
  }
}
