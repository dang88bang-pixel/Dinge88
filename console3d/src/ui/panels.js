/** Seitenpanels: Kennzahlen, Kanal-Matrix, Live-Feed und Asset-Liste. */

import { CHANNELS, STATUS } from '../data/catalog.js'
import { actions, selectors, state } from '../core/store.js'
import { $, bars, batteryColor, clock, el, esc, hexAlpha, relative, rssiColor, sparkline } from './dom.js'

const KPI_DEFS = [
  { id: 'assets',     label: 'Assets',      icon: '📦', color: '#00d4ff' },
  { id: 'online',     label: 'Online',      icon: '🟢', color: '#00e676' },
  { id: 'detections', label: 'Detektionen', icon: '📡', color: '#9c6bff' },
  { id: 'alerts',     label: 'Alarme',      icon: '⚠️', color: '#ff4d6a' }
]

export function createPanels ({ onFocusAsset }) {
  const kpiGrid = $('#kpiGrid')
  const channelList = $('#channelList')
  const feedList = $('#feedList')
  const assetList = $('#assetList')
  const statusChips = $('#statusChips')

  /* ---------------- KPI ---------------- */

  const kpiNodes = new Map()
  for (const def of KPI_DEFS) {
    const value = el('div', { class: 'kpi__value', text: '0' })
    const trend = el('div', { class: 'kpi__trend', text: '' })
    const canvas = el('canvas', { class: 'kpi__spark' })
    const card = el('div', { class: 'kpi' }, [
      el('div', { class: 'kpi__top' }, [
        el('div', { class: 'kpi__icon', text: def.icon, style: { background: hexAlpha(def.color, 0.16) } }),
        trend
      ]),
      value,
      el('div', { class: 'kpi__label', text: def.label }),
      canvas
    ])
    card.style.borderColor = hexAlpha(def.color, 0.22)
    value.style.color = def.color
    kpiGrid.append(card)
    kpiNodes.set(def.id, { value, trend, canvas, color: def.color })
  }

  function renderKpis () {
    const counts = selectors.counts()
    const series = selectors.series()
    const total = series.reduce((a, b) => a + b, 0)
    const half = Math.floor(series.length / 2)
    const older = series.slice(0, half).reduce((a, b) => a + b, 0)
    const newer = series.slice(half).reduce((a, b) => a + b, 0)
    const delta = older === 0 ? (newer > 0 ? 100 : 0) : Math.round(((newer - older) / older) * 100)

    set('assets', counts.total, `${counts.MAINTENANCE || 0} Wartung`, series.map((v) => v * 0.4))
    set('online', counts.ONLINE || 0, `${counts.OFFLINE || 0} offline`, series)
    set('detections', total, `${delta >= 0 ? '▲' : '▼'} ${Math.abs(delta)}%`, series)
    set('alerts', selectors.unacknowledgedAlerts().length, `${state.alerts.length} gesamt`,
      series.map((v) => Math.max(0, v - 1)))

    function set (id, value, trend, data) {
      const node = kpiNodes.get(id)
      if (!node) return
      node.value.textContent = value
      node.trend.textContent = trend
      sparkline(node.canvas, data, node.color)
    }
  }

  /* ---------------- Kanäle ---------------- */

  const channelNodes = new Map()
  for (const channel of CHANNELS) {
    const fill = el('i', { style: { width: '0%', background: channel.color } })
    const val = el('div', { class: 'channel__val', text: '0' })
    const row = el('div', { class: 'channel' }, [
      el('div', { class: 'channel__name', text: channel.label }),
      el('div', { class: 'channel__bar' }, [fill]),
      val
    ])
    channelList.append(row)
    channelNodes.set(channel.id, { fill, val })
  }

  function renderChannels () {
    const activity = selectors.channelActivity()
    const max = Math.max(1, ...Object.values(activity))
    for (const [id, node] of channelNodes) {
      const n = activity[id] || 0
      node.fill.style.width = `${Math.round((n / max) * 100)}%`
      node.val.textContent = n
    }
    const active = Object.keys(activity).length
    $('#channelHint').textContent = `${active}/${CHANNELS.length} aktiv`
  }

  /* ---------------- Live-Feed ---------------- */

  function renderFeed () {
    const scroll = feedList.scrollTop
    feedList.replaceChildren(...state.events.slice(0, 24).map((event) =>
      el('li', {}, [
        el('time', { text: clock(event.ts) }),
        el('div', {}, [
          el('b', { text: event.tag ? `${event.tag} ` : '', style: { color: event.color } }),
          el('span', { text: event.text })
        ])
      ])
    ))
    feedList.scrollTop = scroll
    $('#feedPause').textContent = state.feedPaused ? 'Weiter' : 'Pause'
  }

  /* ---------------- Status-Chips ---------------- */

  const CHIPS = [
    { id: 'ALL', label: 'Alle' },
    { id: 'ONLINE', label: 'Online' },
    { id: 'OFFLINE', label: 'Offline' },
    { id: 'MAINTENANCE', label: 'Wartung' },
    { id: 'SEARCHING', label: 'Suche' }
  ]

  function renderChips () {
    const counts = selectors.counts()
    statusChips.replaceChildren(...CHIPS.map((chip) => {
      const n = chip.id === 'ALL' ? counts.total : (counts[chip.id] || 0)
      return el('button', {
        class: `chip${state.filters.status === chip.id ? ' is-active' : ''}`,
        text: `${chip.label} ${n}`,
        onclick: () => actions.setFilter({ status: chip.id })
      })
    }))
  }

  /* ---------------- Asset-Liste ---------------- */

  function renderAssets () {
    const visible = selectors.visibleAssets()
    const scroll = assetList.scrollTop
    $('#assetCount').textContent = visible.length

    if (!visible.length) {
      assetList.replaceChildren(el('div', {
        class: 'selinfo',
        text: 'Keine Assets für diese Filter.'
      }))
    } else {
      assetList.replaceChildren(...visible.map((asset) => renderAssetRow(asset)))
    }
    assetList.scrollTop = scroll

    const targets = selectors.targets()
    const info = $('#selectionInfo')
    if (!targets.length) {
      info.innerHTML = 'Kein Ziel gewählt · Klick auf Asset oder 3D-Knoten'
    } else if (targets.length === 1) {
      const a = targets[0]
      info.innerHTML = `Ziel <b>${esc(a.shortName)}</b> · ${esc(a.mac)} · ` +
        `${a.rssi ? a.rssi + ' dBm' : 'kein Signal'} · zuletzt ${relative(a.lastSeen)}`
    } else {
      info.innerHTML = `<b>${targets.length}</b> Ziele ausgewählt · Sammelbefehl aktiv`
    }
  }

  function renderAssetRow (asset) {
    const meta = STATUS[asset.status] || STATUS.UNKNOWN
    const level = bars(asset.rssi)
    const barEls = [0, 1, 2, 3].map((i) => el('i', {
      style: {
        height: `${5 + i * 2.6}px`,
        background: i < level ? rssiColor(asset.rssi) : 'rgba(255,255,255,.16)'
      }
    }))

    const battery = asset.battery == null ? null : el('div', { class: 'batt' }, [
      el('div', { class: 'batt__track' }, [
        el('div', {
          class: 'batt__fill',
          style: { width: `${asset.battery}%`, background: batteryColor(asset.battery) }
        })
      ]),
      el('span', { class: 'batt__txt', text: `${asset.battery}%` })
    ])

    return el('li', {
      class: `asset${state.selection.has(asset.id) ? ' is-selected' : ''}`,
      title: `${asset.name}\n${asset.mac}`,
      onclick: (event) => {
        if (event.shiftKey || event.ctrlKey || event.metaKey) actions.toggleSelection(asset.id)
        else actions.selectOnly(asset.id)
      },
      ondblclick: () => onFocusAsset?.(asset.id)
    }, [
      el('div', { class: 'asset__dot', style: { background: meta.color, boxShadow: `0 0 8px ${meta.color}` } }),
      el('div', { class: 'asset__main' }, [
        el('div', { class: 'asset__name', text: asset.shortName }),
        el('div', { class: 'asset__sub', text: `${meta.label} · ${relative(asset.lastSeen)}` })
      ]),
      el('div', { class: 'asset__right' }, [
        el('div', { class: 'bars' }, barEls),
        battery
      ])
    ])
  }

  /* ---------------- Verdrahtung ---------------- */

  $('#assetSearch').addEventListener('input', (e) => actions.setFilter({ query: e.target.value }))
  $('#selectAll').addEventListener('click', () => {
    const ids = selectors.visibleAssets().map((a) => a.id)
    if (state.selection.size >= ids.length) actions.clearSelection()
    else actions.selectAllVisible(ids)
  })
  $('#feedPause').addEventListener('click', () => actions.toggleFeedPause())

  return {
    renderKpis,
    renderChannels,
    renderFeed,
    renderChips,
    renderAssets,
    renderAll () {
      renderKpis(); renderChannels(); renderFeed(); renderChips(); renderAssets()
    }
  }
}
