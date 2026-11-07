import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'

import { AppSider, SIDER_BREAKPOINT } from '../../src/app/app.sider'
import { filesKeyOf, projectKeyOf, siderRootKey } from '../../src/app/app.paths'
import { content } from '../../src/shared/content'
import { declarations } from '../shared/sheets'
import { project } from '../shared/fixtures'
import type { Project } from '../../src/shared/dto'

/**
 * The tree of the frame (R-WP-38), without a router: entries come in as props, the two answers go out as
 * callbacks, and the address is represented by the one key the frame read from it.
 */

const DEMO = project()
const OTHER = project({ id: 'other-9f8e7d6', name: 'other' })

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

function entries(): HTMLElement[] {
  return within(nav()).getAllByRole('menuitem')
}

function sider(): HTMLElement {
  return document.querySelector('.app-sider') as HTMLElement
}

function show(props: Partial<{ projects: Project[]; activeKey: string | null; onOpen: (p: Project) => void; onProjects: () => void; onFiles: (p: Project) => void }>) {
  return render(
    <AppSider
      projects={props.projects ?? [DEMO, OTHER]}
      activeKey={props.activeKey ?? null}
      onOpen={props.onOpen ?? vi.fn()}
      onProjects={props.onProjects ?? vi.fn()}
      onFiles={props.onFiles ?? vi.fn()}
    />,
  )
}

beforeEach(() => {
  cleanup()
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

// UC-WP-70
test('theSiderShowsOneEntryPerProject', () => {
  const view = show({ projects: [DEMO], activeKey: siderRootKey })

  // first the list itself, then the projects (R-WP-38) — and they are the props, not a copy of them:
  // what the frame added has to be in the tree without the tree asking again
  expect(entries().map(entry => entry.textContent)).toEqual([content.nav.projects, DEMO.name])
  view.rerender(
    <AppSider projects={[DEMO, OTHER]} activeKey={siderRootKey} onOpen={vi.fn()} onProjects={vi.fn()} onFiles={vi.fn()} />,
  )
  expect(entries().map(entry => entry.textContent)).toEqual([
    content.nav.projects,
    DEMO.name,
    OTHER.name,
  ])
})

// UC-WP-70
test('theSiderCarriesNoJonEntryOrLabel', () => {
  show({ activeKey: siderRootKey })

  // Paul's "Jon muss weg" (docs/ui.md R-WP-38): neither an entry nor a word of him in the navigation —
  // the agent keeps his name on his tile, which is another component
  expect(entries().map(entry => entry.textContent)).toEqual([content.nav.projects, DEMO.name, OTHER.name])
  expect(within(sider()).queryByText(/jon/i)).toBeNull()
})

// UC-WP-70
test('aProjectEntryOpensThatProjectChat', () => {
  const onOpen = vi.fn()
  const onProjects = vi.fn()
  show({ activeKey: null, onOpen, onProjects })

  fireEvent.click(within(nav()).getByRole('menuitem', { name: OTHER.name }))

  expect(onOpen).toHaveBeenCalledTimes(1)
  expect(onOpen).toHaveBeenCalledWith(OTHER)
  expect(onProjects).not.toHaveBeenCalled()
})

// UC-WP-70
test('theRootEntryOpensTheProjectList', () => {
  const onOpen = vi.fn()
  const onProjects = vi.fn()
  show({ activeKey: null, onOpen, onProjects })

  // the first entry is the existing list — where a project is added and removed — and not a fourth page
  fireEvent.click(within(nav()).getByRole('menuitem', { name: content.nav.projects }))

  expect(onProjects).toHaveBeenCalledTimes(1)
  expect(onOpen).not.toHaveBeenCalled()
})

// R-WP-1
// UC-WP-52
test('theSiderMarksTheEntryTheAddressNames', () => {
  // the frame reads this key out of the address, and the address names a project (C-3, R-WP-38)
  show({ activeKey: projectKeyOf(OTHER.name) })

  // the basis marks its entry with its own item state (ANTD-A1), and the same entry carries the answer a
  // screen reader reads (ANTD-A9) — one mark, not two that can disagree
  const marked = entries().filter(entry => entry.className.includes('ant-menu-item-selected'))
  expect(marked, 'exactly one entry of the tree shows').toHaveLength(1)
  expect(marked[0].textContent).toBe(OTHER.name)

  const current = entries().filter(entry => entry.getAttribute('aria-current') === 'page')
  expect(current, 'exactly one entry says it is the current page').toHaveLength(1)
  expect(current[0]).toBe(marked[0])
})

// R-WP-1
test('noAddressMeansNoMark', () => {
  // `/settings` and the bare `/chat` name no entry of the tree: marking the first anyway would show a
  // project the address does not speak of
  show({ activeKey: null })

  expect(entries().filter(entry => entry.className.includes('ant-menu-item-selected'))).toEqual([])
  expect(
    entries().filter(entry => entry.getAttribute('aria-current') !== null),
  ).toEqual([])
  // and no unit of any project either: an area nobody named is not offered (R-WP-38)
  expect(within(nav()).queryByRole('menuitem', { name: content.nav.files })).toBeNull()
})

/**
 * The file area is an address of its own and gets its own entry — under the one project the address names
 * and nowhere else (R-WP-38): two handles of one address change, and the entry hangs one step behind its
 * project instead of opening a subtree, so there is no open-state to keep in step.
 */
// R-WP-38
// UC-WP-70
test('theUnitsOfTheOpenProjectHangUnderIt', () => {
  const onFiles = vi.fn()
  const onOpen = vi.fn()
  const onProjects = vi.fn()
  show({ activeKey: filesKeyOf(DEMO.name), onFiles, onOpen, onProjects })

  // directly behind its project, and the other project keeps no such entry
  expect(entries().map(entry => entry.textContent)).toEqual([
    content.nav.projects,
    DEMO.name,
    content.nav.files,
    OTHER.name,
  ])

  // the indent is the entry's own class and the sheet gives it a measure — jsdom lays out nothing, so the
  // contract is read where it is written
  const unit = within(nav()).getByRole('menuitem', { name: content.nav.files })
  expect(unit.className).toContain('app-sider-unit')
  expect(declarations('.app-sider .ant-menu-item.app-sider-unit')).toContain('padding-inline-start')

  // the second handle names the area and nothing else
  fireEvent.click(unit)
  expect(onFiles).toHaveBeenCalledTimes(1)
  expect(onFiles).toHaveBeenCalledWith(DEMO)
  expect(onOpen).not.toHaveBeenCalled()
  expect(onProjects).not.toHaveBeenCalled()
})

/**
 * One address, one mark: when the address names the unit, the unit is what shows — the project beside it
 * is not marked as well, or the tree would answer the same question twice (R-WP-38).
 */
// R-WP-38
test('theUnitIsTheOnlyMarkOfTheAddress', () => {
  show({ activeKey: filesKeyOf(DEMO.name) })

  const marked = entries().filter(entry => entry.className.includes('ant-menu-item-selected'))
  expect(marked, 'exactly one entry of the tree shows').toHaveLength(1)
  expect(marked[0].textContent).toBe(content.nav.files)

  const current = entries().filter(entry => entry.getAttribute('aria-current') === 'page')
  expect(current, 'exactly one entry says it is the current page').toHaveLength(1)
  expect(current[0]).toBe(marked[0])

  const named = within(nav()).getByRole('menuitem', { name: DEMO.name })
  expect(named.className).not.toContain('ant-menu-item-selected')
  expect(named.getAttribute('aria-current')).toBeNull()
})

/**
 * The chat of a project names the project, so its area hangs under it — and under that one only: a unit
 * beside every project would be a mark the address never gave, and a tree of areas to keep in step with
 * a page that has one (R-WP-38).
 */
// R-WP-38
test('theUnitHangsUnderTheNamedProjectAlone', () => {
  show({ activeKey: projectKeyOf(OTHER.name) })

  // `other` is named, so it carries the one unit; `demo` carries none
  expect(entries().map(entry => entry.textContent)).toEqual([
    content.nav.projects,
    DEMO.name,
    OTHER.name,
    content.nav.files,
  ])
})

// UC-WP-70
test('theSiderCollapsesBelowItsBreakpointAndStaysReachable', () => {
  const onProjects = vi.fn()
  belowTheBreakpoint()
  const narrow = show({ activeKey: siderRootKey, onProjects })

  // eingeklappt, und die Chat-Fläche bekommt die Breite: Width 0 and no rail of its own left
  expect(sider().className).toContain('ant-layout-sider-below')
  expect(sider().className).toContain('ant-layout-sider-collapsed')
  expect(sider().className).toContain('ant-layout-sider-zero-width')
  expect(sider().style.width).toBe('0px')
  const triggers = document.querySelectorAll('.ant-layout-sider-zero-width-trigger')
  // the way back is the basis' own trigger — a `span` it gives neither role nor name, so it is read here
  // by its class and not by `getByRole('button')`
  expect(triggers).toHaveLength(1)

  // über den ausgeklappten Sider bleibt die Auswahl erreichbar (auch „Projects")
  fireEvent.click(triggers[0])
  expect(sider().className).not.toContain('ant-layout-sider-collapsed')
  expect(sider().className).not.toContain('ant-layout-sider-zero-width')
  expect(sider().style.width).toBe('200px')
  fireEvent.click(within(nav()).getByRole('menuitem', { name: content.nav.projects }))
  expect(onProjects).toHaveBeenCalledTimes(1)

  narrow.unmount()
  vi.restoreAllMocks()

  // breit: nothing is folded and no trigger is offered — the tree stands beside the view
  show({ activeKey: siderRootKey, onProjects })
  expect(sider().className).not.toContain('ant-layout-sider-below')
  expect(sider().className).not.toContain('ant-layout-sider-collapsed')
  expect(sider().style.width).toBe('200px')
  expect(document.querySelectorAll('.ant-layout-sider-zero-width-trigger')).toHaveLength(0)
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
  show({ activeKey: siderRootKey })

  expect(SIDER_BREAKPOINT).toBe('lg')
  expect(asked).toContain('screen and (max-width: 991.98px)')
})
