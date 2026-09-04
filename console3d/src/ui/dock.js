/** Aktions-Dock: Katalog, Favoriten, Sammelbefehle und Freitext. */

import { ACTIONS, CATEGORIES, RISK_LABEL } from '../data/catalog.js'
import { actions, selectors, state } from '../core/store.js'
import { $, el, esc } from './dom.js'

export function createDock ({ onExecute, onFlushQueue }) {
  const tabs = $('#actionTabs')
  const grid = $('#actionGrid')
  const noteRow = $('#noteRow')
  const noteInput = $('#noteInput')

  function actionsForCategory () {
    if (state.category === 'FAVORITES') {
      const favs = ACTIONS.filter((a) => state.favorites.has(a.id))
      return favs.length ? favs : ACTIONS.slice(0, 4)
    }
    return ACTIONS.filter((a) => a.category === state.category)
  }

  function renderTabs () {
    tabs.replaceChildren(...CATEGORIES.map((category) => {
      const count = category.id === 'FAVORITES'
        ? state.favorites.size
        : ACTIONS.filter((a) => a.category === category.id).length
      return el('button', {
        class: `chip${state.category === category.id ? ' is-active' : ''}`,
        text: `${category.label} ${count}`,
        onclick: () => actions.setCategory(category.id)
      })
    }))
  }

  function renderGrid () {
    const targets = selectors.targets()
    const list = actionsForCategory()

    grid.replaceChildren(...list.map((action) => {
      const blocked = !action.local && targets.length === 0
      const offlineBlock = action.requiresOnline &&
        targets.length > 0 && targets.every((t) => t.status !== 'ONLINE')
      const disabled = blocked || offlineBlock || (state.busyAction && state.busyAction !== action.id)

      const fav = el('span', {
        class: `act__fav${state.favorites.has(action.id) ? ' is-on' : ''}`,
        text: state.favorites.has(action.id) ? '★' : '☆',
        title: 'Als Favorit merken',
        onclick: (event) => { event.stopPropagation(); actions.toggleFavorite(action.id) }
      })

      const card = el('button', {
        class: `act${state.busyAction === action.id ? ' is-busy' : ''}`,
        style: { '--acc': action.color },
        disabled: disabled || undefined,
        title: offlineBlock
          ? 'Aktion verlangt ein Online-Ziel'
          : blocked ? 'Bitte zuerst ein Ziel wählen' : action.desc,
        onclick: () => onExecute(action)
      }, [
        el('div', { class: 'act__row' }, [
          el('div', { class: 'act__ico', text: action.icon }),
          el('div', { class: 'act__name', text: action.title }),
          action.local ? null : fav
        ]),
        el('div', { class: 'act__desc', text: action.desc }),
        el('div', {
          class: `act__risk risk-${action.risk}`,
          text: action.local ? 'lokal' : RISK_LABEL[action.risk]
        })
      ])
      // `--acc` muss als CSS-Variable gesetzt werden (Object.assign kann das nicht)
      card.style.setProperty('--acc', action.color)
      return card
    }))

    const needsNote = list.some((a) => a.acceptsNote)
    noteRow.hidden = !needsNote
  }

  function renderHeader () {
    const targets = selectors.targets()
    $('#dockTarget').textContent = targets.length === 0
      ? 'Kein Ziel'
      : targets.length === 1
        ? targets[0].shortName
        : `${targets.length} Ziele`

    const delivered = state.commands.filter((c) => c.state === 'delivered').length
    const failed = state.commands.filter((c) => c.state === 'denied' || c.state === 'blocked').length
    $('#queueTag').textContent = `Queue ${state.queue}`
    $('#deliveredTag').textContent = `${delivered} zugestellt`
    $('#failedTag').textContent = `${failed} Probleme`
  }

  noteInput.addEventListener('input', (event) => {
    const value = event.target.value
    actions.setNote(value)
    $('#noteCount').textContent = `${value.length}/120`
  })

  $('#btnQueueFlush').addEventListener('click', onFlushQueue)

  return {
    renderTabs,
    renderGrid,
    renderHeader,
    syncNote () {
      if (noteInput.value !== state.note) noteInput.value = state.note
      $('#noteCount').textContent = `${state.note.length}/120`
    },
    renderAll () { renderTabs(); renderGrid(); renderHeader() }
  }
}

/** Baut die Zusammenfassung für den Bestätigungsdialog. */
export function targetSummary () {
  const targets = selectors.targets()
  if (!targets.length) return 'Kein Ziel'
  return targets.map((t) => `${esc(t.shortName)} (${esc(t.status.toLowerCase())})`).join(' · ')
}
