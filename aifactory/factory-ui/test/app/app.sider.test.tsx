import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import type { ReactElement } from 'react'

import { AppSider, SIDER_BREAKPOINT } from '../../src/app/app.sider'
import { chatKeyOf, filesKeyOf } from '../../src/app/app.paths'
import { content } from '../../src/shared/content'
import { project } from '../shared/fixtures'
import type { Project } from '../../src/shared/dto'

/**
 * The tree of the frame (R-WP-44), without a router: one group per project comes in as props, the two
 * answers go out as callbacks, and the address is represented by the one key the frame read from it.
 */

const DEMO = project()
const OTHER = project({ id: 'other-9f8e7d6', name: 'other' })
const THIRD = project({ id: 'third-5c4b3a2', name: 'third' })
const FOURTH = project({ id: 'fourth-7b6a5c4', name: 'fourth' })

function mediaAnswer(query: string, matches: boolean): MediaQueryList {
  return {
    matches,
    media: query,
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    dispatchEvent: () => false,
  } as MediaQueryList
}

/** The narrow answer for the sider's own question only — a blanket `true` would answer the dark question too. */
function belowTheBreakpoint(): void {
  vi.spyOn(window, 'matchMedia').mockImplementation(
    (query: string) => mediaAnswer(String(query), String(query).includes('max-width')),
  )
}

function nav(): HTMLElement {
  return screen.getByRole('navigation', { name: content.nav.views })
}

/**
 * Every entry of the tree, the group titles included. A title is the basis' own disclosure and carries
 * `aria-expanded`, an entry never does — that is the DOM's own difference, so no class of the basis is read
 * here.
 */
function entries(): HTMLElement[] {
  return within(nav()).queryAllByRole('menuitem')
}

function groups(): HTMLElement[] {
  return entries().filter(entry => entry.getAttribute('aria-expanded') !== null)
}

/** The projects whose group stands open, in the order the tree shows them. */
function openGroups(): string[] {
  return groups()
    .filter(group => group.getAttribute('aria-expanded') === 'true')
    .map(group => group.textContent ?? '')
}

/** The whole branch of one project: its title and the entries the basis hangs under it. */
function branchOf(name: string): HTMLElement {
  const title = within(nav()).getByRole('menuitem', { name })
  expect(title.getAttribute('aria-expanded'), `${name} is a group and not an entry`).not.toBeNull()
  return title.parentElement as HTMLElement
}

function sider(): HTMLElement {
  return document.querySelector('.app-sider') as HTMLElement
}

interface Tree {
  projects: Project[]
  activeKey: string | null
  onOpen: (p: Project) => void
  onFiles: (p: Project) => void
}

function tree(props: Partial<Tree> = {}): ReactElement {
  return (
    <AppSider
      projects={props.projects ?? [DEMO, OTHER]}
      activeKey={props.activeKey ?? null}
      onOpen={props.onOpen ?? vi.fn()}
      onFiles={props.onFiles ?? vi.fn()}
    />
  )
}

function show(props: Partial<Tree> = {}) {
  return render(tree(props))
}

beforeEach(() => {
  cleanup()
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

/**
 * The tree is the projects and their units and nothing else (R-WP-44): the list of projects is a page of the
 * header, and the group is a disclosure, not a fourth address.
 */
// UC-WP-78
test('siderGroupsPerProjectWithChatsAsEntries', () => {
  // kein Projekt ⇒ der Sider ist leer: keine Gruppen-Überschrift, kein Platzhalter
  const none = show({ projects: [] })
  expect(entries()).toEqual([])
  expect(sider().textContent).toBe('')
  none.unmount()

  const onFiles = vi.fn()
  const onOpen = vi.fn()
  const view = show({ projects: [DEMO], activeKey: filesKeyOf(DEMO.name), onFiles, onOpen })

  // je Projekt eine Klapp-Gruppe, der Projektname ist ihr Titel
  expect(groups().map(group => group.textContent)).toEqual([DEMO.name])
  // ihre Kinder sind der Chat und die File-Fläche, und Files steht **zuletzt**: jsdom legt nichts aus, also
  // ist die Nachbarschaft im DOM der Beweis und nicht die Reihenfolge eines Arrays
  const files = within(nav()).getByRole('menuitem', { name: content.nav.files })
  const chat = within(nav()).getByRole('menuitem', { name: content.nav.chat })
  expect(files.previousElementSibling).toBe(chat)

  // die Projektliste lebt allein im Top-Menü (R-WP-44), und Pauls Name steht in keinem Eintrag
  expect(nav().textContent).not.toContain(content.nav.projects)
  expect(within(sider()).queryByText(/jon/i)).toBeNull()

  // die Kinder sind die Adressen: das Files-Kind öffnet die File-Fläche **dieses** Projekts und sonst nichts
  fireEvent.click(files)
  expect(onFiles).toHaveBeenCalledTimes(1)
  expect(onFiles).toHaveBeenCalledWith(DEMO)
  expect(onOpen).not.toHaveBeenCalled()

  // was der Rahmen an Projekten hat, steht als Gruppe im Baum — drei Projekte, drei Gruppen, je zwei Kinder
  view.rerender(tree({ projects: [DEMO, OTHER, THIRD] }))
  expect(groups().map(group => group.textContent)).toEqual([DEMO.name, OTHER.name, THIRD.name])
  expect(entries().filter(entry => entry.getAttribute('aria-expanded') === null)).toHaveLength(6)
})

/**
 * The chat entry of a group is the way to that project's chat (UC-WP-77) — one callback, one path written,
 * the open chat never mounted again.
 */
// UC-WP-78
// UC-WP-77
// R-WP-1
test('chatUnitOpensTheChatAddress', () => {
  const onOpen = vi.fn()
  const onFiles = vi.fn()
  show({ activeKey: filesKeyOf(OTHER.name), onOpen, onFiles })

  // both groups stand open, so only the child of `other` can answer with `other`
  fireEvent.click(within(branchOf(OTHER.name)).getByRole('menuitem', { name: content.nav.chat }))

  expect(onOpen, 'the chat entry opens the project its group names').toHaveBeenCalledTimes(1)
  expect(onOpen).toHaveBeenCalledWith(OTHER)
  expect(onFiles, 'no second address invented on the way').not.toHaveBeenCalled()
})

/**
 * The title answers **which project** and never **where** (R-WP-44): it opens and closes its group, and the
 * tree keeps its one pair of handles — the children.
 */
// UC-WP-78
test('groupTitleOnlyTogglesTheGroup', () => {
  const onOpen = vi.fn()
  const onFiles = vi.fn()
  show({ onOpen, onFiles })

  expect(openGroups()).toEqual([DEMO.name, OTHER.name])

  fireEvent.click(within(nav()).getByRole('menuitem', { name: OTHER.name }))

  // geklappt — and with the group its entries go: an entry of a closed group is nothing to click
  expect(openGroups()).toEqual([DEMO.name])
  expect(within(nav()).queryAllByRole('menuitem', { name: content.nav.files })).toHaveLength(1)

  fireEvent.click(within(nav()).getByRole('menuitem', { name: OTHER.name }))
  expect(openGroups()).toEqual([DEMO.name, OTHER.name])

  expect(onOpen, 'the title is no address').not.toHaveBeenCalled()
  expect(onFiles, 'the title is no address').not.toHaveBeenCalled()
})

// R-WP-44
// UC-WP-78
test('groupsAreOpenUntilFourProjects', () => {
  const view = show({ projects: [DEMO, OTHER, THIRD] })
  expect(openGroups()).toEqual([DEMO.name, OTHER.name, THIRD.name])

  view.rerender(tree({ projects: [DEMO, OTHER, THIRD, FOURTH] }))
  // ab vier Projekten steht nur die erste offen: der Rest ist ein Klick, keine zweite Baumform
  expect(openGroups()).toEqual([DEMO.name])
})

/**
 * The mark is a reading of the address and nothing else (UC-WP-52, R-WP-1), and it is **shown**: a mark
 * inside a closed group would answer the URL while the tree displays nothing.
 */
// R-WP-1
// UC-WP-52
test('theSiderMarksTheEntryTheAddressNames', () => {
  const view = show({ activeKey: chatKeyOf(OTHER.name) })

  // the basis states no `aria-current` of its own (ANTD-A1); the frame hands it to the entry it read out of
  // the path, so one mark answers the screen reader and the URL (ANTD-A9)
  const current = entries().filter(entry => entry.getAttribute('aria-current') === 'page')
  expect(current, 'exactly one entry of the tree shows').toHaveLength(1)
  expect(current[0].textContent).toBe(content.nav.chat)
  expect(within(branchOf(OTHER.name)).getByRole('menuitem', { name: content.nav.chat })).toBe(current[0])

  view.rerender(tree({ projects: [DEMO, OTHER, THIRD, FOURTH], activeKey: chatKeyOf(FOURTH.name) }))
  const named = entries().filter(entry => entry.getAttribute('aria-current') === 'page')
  expect(named, 'the address marks one entry although its group stands past the opening line').toHaveLength(1)
  expect(within(branchOf(FOURTH.name)).getByRole('menuitem', { name: content.nav.chat })).toBe(named[0])
})

// R-WP-1
test('noAddressMeansNoMark', () => {
  // `/settings`, `/projects` and the bare `/chat` name no entry of the tree: marking the first anyway would
  // show a project the address does not speak of
  show({ activeKey: null })

  expect(entries().filter(entry => entry.getAttribute('aria-current') !== null)).toEqual([])
})

// UC-WP-70
test('theSiderCollapsesBelowItsBreakpointAndStaysReachable', () => {
  const onOpen = vi.fn()
  belowTheBreakpoint()
  const narrow = show({ activeKey: filesKeyOf(DEMO.name), onOpen })

  // eingeklappt, und die Chat-Fläche bekommt die Breite: Width 0 and no rail of its own left
  expect(sider().className).toContain('ant-layout-sider-below')
  expect(sider().className).toContain('ant-layout-sider-collapsed')
  expect(sider().className).toContain('ant-layout-sider-zero-width')
  expect(sider().style.width).toBe('0px')
  const handles = document.querySelectorAll('.ant-layout-sider-zero-width-trigger')
  // the way back is the basis' own trigger — a `span` it gives neither role nor name, so it is read here
  // by its class and not by `getByRole('button')`; `collapsedWidth={0}` gives it in both states (R-WP-44)
  expect(handles).toHaveLength(1)

  // über den ausgeklappten Sider bleibt die Auswahl erreichbar: erst die Gruppe, dann ihr Chat-Kind
  fireEvent.click(handles[0])
  expect(sider().className).not.toContain('ant-layout-sider-collapsed')
  expect(sider().className).not.toContain('ant-layout-sider-zero-width')
  expect(sider().style.width).toBe('250px')
  fireEvent.click(within(nav()).getByRole('menuitem', { name: DEMO.name }))
  fireEvent.click(within(nav()).getByRole('menuitem', { name: content.nav.chat }))
  expect(onOpen).toHaveBeenCalledTimes(1)
  expect(onOpen).toHaveBeenCalledWith(DEMO)

  narrow.unmount()
  vi.restoreAllMocks()

  // breit: nothing is folded, the same handle stands ready, and the tree is 250 px wide (A2's Look measure)
  show({ activeKey: filesKeyOf(DEMO.name), onOpen })
  expect(sider().className).not.toContain('ant-layout-sider-below')
  expect(sider().className).not.toContain('ant-layout-sider-collapsed')
  expect(sider().style.width).toBe('250px')
  expect(document.querySelectorAll('.ant-layout-sider-zero-width-trigger')).toHaveLength(1)
})

// R-WP-38
test('theBreakpointIsTheMeasuredOne', () => {
  // asked and answered in the plan's Rechnung: under 992 px the file panel would be pushed into the chat,
  // and `md` (768) leaves the row too narrow long before that
  const asked: string[] = []
  vi.spyOn(window, 'matchMedia').mockImplementation((query: string) => {
    asked.push(String(query))
    return mediaAnswer(String(query), false)
  })
  show()

  expect(SIDER_BREAKPOINT).toBe('lg')
  expect(asked).toContain('screen and (max-width: 991.98px)')
})
