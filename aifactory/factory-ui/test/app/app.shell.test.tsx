import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { act, cleanup, fireEvent, screen, within } from '@testing-library/react'

import { closeAppStream, renderApp, stubAppServer } from '../shared/app.harness'
import { belowTheBreakpoint } from '../shared/media'
import { content } from '../../src/shared/content'
import { project } from '../shared/fixtures'

/** The chat of the one project the harness answers with — the address names it (UC-WP-52, C-3). */
const CHAT_PATH = `/chat/${project().name}`

async function flush(): Promise<void> {
  await act(async () => {
    await new Promise(resolve => setTimeout(resolve, 0))
  })
}

function nav(): HTMLElement {
  return screen.getByRole('navigation', { name: content.nav.views })
}

/** The group titles of the tree — a title is the basis' disclosure and carries `aria-expanded` (R-WP-44). */
function groups(): HTMLElement[] {
  return within(nav())
    .getAllByRole('menuitem')
    .filter(entry => entry.getAttribute('aria-expanded') !== null)
}

/** The header's navigation — named of its own, so the tree's helpers stay unambiguous (R-WP-43). */
function topNav(): HTMLElement {
  return screen.getByRole('navigation', { name: content.nav.pages })
}

/** The entries the header marks as showing: the reading of the address, never the last click. */
function markedTopEntries(): HTMLElement[] {
  return within(topNav())
    .getAllByRole('menuitem')
    .filter(entry => entry.className.includes('ant-menu-item-selected'))
}

beforeEach(() => {
  cleanup()
  localStorage.clear()
})

afterEach(async () => {
  await closeAppStream()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

// UC-WP-1
test('theFrameShowsTheProjectTree', async () => {
  stubAppServer()
  renderApp()

  // the frame owns the read (D1), so the tree fills in with it
  expect(await within(nav()).findByText(project().name)).toBeInTheDocument()

  // the frame is the tree (R-WP-44): one group per project, named by the project — the list of projects is a
  // page of the header and no entry here anymore
  expect(groups().map(group => group.textContent)).toEqual([project().name])
  expect(within(nav()).queryByRole('menuitem', { name: content.nav.projects })).toBeNull()
})

// UC-WP-53
test('theUiSpeaksEnglish', async () => {
  stubAppServer()
  const { container } = renderApp('/projects')

  // R-WP-44: the frame's navigation is the tree in the sider — one group per project
  expect(await within(nav()).findByText(project().name)).toBeInTheDocument()
  expect(groups().map(group => group.textContent)).toEqual([project().name])
  expect(screen.getByLabelText(content.user.name)).toBeInTheDocument()
  // the colour switch of the frame (UC-WP-71) is a control of the basis and speaks the catalog's word
  expect(screen.getByRole('switch', { name: content.theme.toggle('light') })).toBeInTheDocument()

  // the projects view: its form and its empty affordance are the catalog's words
  expect(await screen.findByLabelText(content.projects.gitUrl)).toBeInTheDocument()
  expect(screen.getByRole('button', { name: content.projects.add })).toBeInTheDocument()

  // the chat of a project: composer, tile state and the file area's affordance
  fireEvent.click(await screen.findByRole('button', { name: project().name }))
  const chat = await screen.findByRole('region', { name: content.chat.title })
  expect(within(chat).getByRole('button', { name: content.chat.send })).toBeInTheDocument()
  expect(within(chat).getByRole('button', { name: content.chat.stop })).toBeInTheDocument()
  expect(await within(chat).findByTestId('tile-state-Dev')).toHaveTextContent(content.tiles.state.IDLE)
  expect(screen.getByTestId('files-open')).toHaveTextContent(content.files.open)

  // settings: the field names of the form, reached from the header's pages (R-WP-43)
  fireEvent.click(within(topNav()).getByRole('menuitem', { name: content.nav.settings }))
  expect(await screen.findByText(content.config.provider)).toBeInTheDocument()
  expect(screen.getByText(content.config.model)).toBeInTheDocument()

  // what the screen actually shows carries no German left — the catalog is the only source of copy
  await flush()
  expect(container.textContent).not.toMatch(/[ÄÖÜäöüß]/)
})

// R-WP-43
// UC-WP-76
test('theHeaderCarriesTheBrandThePagesAndTheName', async () => {
  stubAppServer()
  const { container } = renderApp('/projects')
  await screen.findByLabelText(content.projects.gitUrl)

  const header = container.querySelector('.app-header') as HTMLElement
  expect(header.textContent).toContain(content.brand)
  expect(within(header).getByLabelText(content.user.name)).toBeInTheDocument()

  // the two global pages of R-WP-43 and nothing else, in a navigation of their own: the chat belongs to a
  // project and is opened from the tree, so it is no entry here (Paul 2026-11-08); a project entry would be a
  // second answer to the question the tree beside it answers (R-WP-38)
  expect(within(topNav()).getAllByRole('menuitem').map(entry => entry.textContent)).toEqual([
    content.nav.projects,
    content.nav.settings,
  ])
  // one mark only, and the list page carries it — `aria-current` is what a screen reader reads (ANTD-A9)
  expect(markedTopEntries().map(entry => entry.textContent)).toEqual([content.nav.projects])
  expect(header.querySelectorAll('[aria-current]'), 'exactly one entry of the header shows').toHaveLength(1)

  // INC-L1-BUG-1: R-WP-43 asks for **dark** and **horizontal**, and jsdom paints no colour — so the claim is
  // pinned on the two classes the basis puts on its own list (ANTD-A1). Without them the header holds a light
  // inline tree and the `Menu.dark*` tokens of theme.ts paint a menu that is never drawn.
  const pages = topNav().querySelector('.ant-menu') as HTMLElement
  expect(pages, 'the basis renders its own list inside our navigation').not.toBeNull()
  expect(pages.className, 'the pages menu is the dark one of R-WP-43').toContain('ant-menu-dark')
  expect(pages.className, 'the pages menu lies horizontal in the band').toContain('ant-menu-horizontal')

  // INC-L1-D3: the page is reached through its entry alone — no button of the header carries a mark beside it
  expect(within(header).queryAllByRole('button', { name: content.nav.settings })).toEqual([])
  // the brand is a word and not a way to navigate
  expect(within(header).queryAllByRole('link')).toEqual([])
})

/** The entry the tree marks as showing — the mark that has to answer the address, not the last click. */
function markedEntries(): HTMLElement[] {
  return within(nav())
    .getAllByRole('menuitem')
    .filter(entry => entry.className.includes('ant-menu-item-selected'))
}

/** The settings page of the header (R-WP-43): a page is an entry now, not a button beside the entries. */
function settingsEntry(): HTMLElement {
  return within(topNav()).getByRole('menuitem', { name: content.nav.settings })
}

// R-WP-38
// R-WP-1
test('theSettingsEntryIsCurrentOnSettings', async () => {
  // one mount walked through four addresses: a mark that is read from a state — the mount's first reading or
  // the last click — survives a reload-test and fails here
  stubAppServer()
  renderApp('/settings')
  await screen.findByText(content.config.provider)

  // settings is a page, the tree answers which project (R-WP-38): the header carries the mark, the tree
  // names nothing because `/settings` names no project
  expect(settingsEntry()).toHaveAttribute('aria-current', 'page')
  expect(markedEntries()).toEqual([])

  fireEvent.click(within(topNav()).getByRole('menuitem', { name: content.nav.projects }))
  await screen.findByLabelText(content.projects.gitUrl)
  expect(settingsEntry(), 'the header entry marks nothing where another page shows').not.toHaveAttribute(
    'aria-current',
  )
  // the list is a page of the header, not a place in the tree (R-WP-44): the tree names nothing here
  expect(markedEntries()).toEqual([])
  expect(markedTopEntries().map(entry => entry.textContent)).toEqual([content.nav.projects])

  // and here the address changes without any click in the tree — the list row opened the chat while the
  // frame stayed mounted, so only a reading of the address can know which entry shows now
  fireEvent.click(await screen.findByRole('button', { name: project().name }))
  await screen.findByRole('region', { name: content.chat.title })
  expect(
    markedEntries().map(entry => entry.textContent),
    'the tree shows the chat unit of the project the address names (UC-WP-77)',
  ).toEqual([content.nav.chat])
  expect(settingsEntry()).not.toHaveAttribute('aria-current')

  fireEvent.click(settingsEntry())
  await screen.findByText(content.config.provider)
  expect(settingsEntry()).toHaveAttribute('aria-current', 'page')
  expect(markedEntries()).toEqual([])
})

// R-WP-43
// UC-WP-76
test('theTopMarkFollowsAnAddressNobodyClicked', async () => {
  // one mount, three addresses, and not one click in the header: a menu that keeps its own selection — the
  // mount's first reading or the last click — survives a reload test and fails here
  stubAppServer()
  const app = renderApp('/settings')
  await screen.findByText(content.config.provider)

  expect(markedTopEntries().map(entry => entry.textContent)).toEqual([content.nav.settings])
  expect(settingsEntry()).toHaveAttribute('aria-current', 'page')

  // the tree is the handle of every step below, so the header is only ever read; the list itself is a page
  // of the header and not an entry of the tree (R-WP-44)
  fireEvent.click(within(topNav()).getByRole('menuitem', { name: content.nav.projects }))
  await screen.findByLabelText(content.projects.gitUrl)
  expect(app.path()).toBe('/projects')
  expect(markedTopEntries().map(entry => entry.textContent)).toEqual([content.nav.projects])

  // the list row opens a chat: a third address, still no click in the header. The header names no chat page
  // (Paul 2026-11-08), so its reading of a chat address is empty — the tree answers it instead (UC-WP-76)
  fireEvent.click(await screen.findByRole('button', { name: project().name }))
  await screen.findByRole('region', { name: content.chat.title })
  expect(app.path()).toBe(CHAT_PATH)
  expect(markedTopEntries(), 'a chat address marks no page of the header').toEqual([])
  expect(markedEntries().map(entry => entry.textContent), 'the tree answers the same address').toEqual([
    content.nav.chat,
  ])
})

// R-WP-38
// R-WP-43
// UC-WP-76
// UC-WP-77
test('theTopMarkAndTheTreeMarkAgreeInOneRender', async () => {
  // INC-L1-LOW-3: the two menus read one address, so „die Markierung folgt der Adresse und ist einig" only
  // holds **within one render** — two mounts could each be right about another URL. One chat address, both
  // navigations read in the same render: the header names no chat page and marks nothing at all, the tree
  // marks exactly the chat unit — the emptiness on one side and no claim on the other are the agreement
  // (UC-WP-76 GIVEN 3), a marked header entry would be the contradiction.
  stubAppServer()
  renderApp(CHAT_PATH)
  await screen.findByRole('region', { name: content.chat.title })

  const tree = within(nav()).getByRole('menuitem', { name: content.nav.chat })

  expect(
    within(topNav()).queryByRole('menuitem', { name: content.nav.chat }),
    'the header carries no chat entry',
  ).toBeNull()
  expect(markedTopEntries(), 'nothing marks in the header on a chat address').toEqual([])
  expect(topNav().querySelectorAll('[aria-current]'), 'and the header claims no page').toHaveLength(0)

  expect(tree, 'the tree names the unit the address names').toHaveAttribute('aria-current', 'page')
  // one mark in the tree, so nothing there can disagree: it says nothing about the project entry or the
  // file area (R-WP-38)
  expect(markedEntries(), 'exactly the chat unit marks in the tree').toEqual([tree])
  expect(nav().querySelectorAll('[aria-current]')).toHaveLength(1)
})

// UC-WP-70
test('theChatInputStandsInsideTheContentBesideTheSider', async () => {
  stubAppServer()
  const { container } = renderApp(CHAT_PATH)
  const chat = await screen.findByRole('region', { name: content.chat.title })

  const main = container.querySelector('.app-main') as HTMLElement
  const sider = container.querySelector('.app-sider') as HTMLElement
  const row = container.querySelector('.ant-layout-has-sider') as HTMLElement
  const field = within(chat).getByTestId('chat-input')
  const send = within(chat).getByTestId('chat-send')

  // the composer docks inside its own column (R-WP-35): it hangs in the content, ...
  expect(main.contains(field)).toBe(true)
  expect(main.contains(send)).toBe(true)
  // ... and it never runs under the tree, which stands in full height beside it (R-WP-38). Nesting, not
  // measures — jsdom lays out nothing, so a pixel here would prove nothing about a real window
  expect(sider.contains(field)).toBe(false)
  expect(main.contains(sider)).toBe(false)
  expect(sider.contains(main)).toBe(false)

  // the two are branches of one row, neither holding the other
  expect(row, 'the basis answers its sider row for the frame').not.toBeNull()
  expect(row.querySelector(':scope > .app-sider')).toBe(sider)
  expect(row.querySelector(':scope > .app-main')).toBe(main)
})

/**
 * The tree is folded away for the chat and reached again from the band (R-WP-44, UC-WP-70): one answer, held
 * by the frame, asked by two — the breakpoint of the basis and the button of the header. The narrow case is
 * the honest one, because that is where the tree steps out of the chat's way.
 */
// UC-WP-70
test('theFrameFoldsTheTreeAndTheHandleOfTheBandReachesItAgain', async () => {
  stubAppServer()
  belowTheBreakpoint()
  const app = renderApp('/projects')
  await flush()

  const sider = app.container.querySelector('.app-sider') as HTMLElement
  // eng: the basis reports its edge, the frame folds — no click of anyone
  expect(sider.style.width).toBe('0px')

  // the handle is one button of the band and speaks in glyphs, so its name says what pressing it will do
  const handle = screen.getByTestId('sider-toggle')
  expect(handle, 'the band offers the handle, the tree offers none').not.toBeNull()
  expect(handle).toHaveAttribute('aria-label', content.nav.toggleSider(true))

  fireEvent.click(handle)
  expect(sider.style.width, 'the handle opens the tree').toBe('250px')
  expect(handle).toHaveAttribute('aria-label', content.nav.toggleSider(false))

  // and it folds it again — one button, both ways
  fireEvent.click(handle)
  expect(sider.style.width).toBe('0px')
  fireEvent.click(handle)
  expect(sider.style.width).toBe('250px')

  // the opened tree is reachable: its entry answers with an address the frame was not already at
  fireEvent.click(await within(nav()).findByRole('menuitem', { name: content.nav.chat }))
  expect(await screen.findByRole('region', { name: content.chat.title })).toBeInTheDocument()
})
