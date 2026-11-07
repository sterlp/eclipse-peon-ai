import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { act, cleanup, fireEvent, screen, within } from '@testing-library/react'

import { closeAppStream, renderApp, stubAppServer } from '../shared/app.harness'
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

  // the frame is the tree (R-WP-38): the list first, then one entry per project; the showing entry carries
  // the basis' item state (ANTD-A1, R-WP-39) and the answer a screen reader reads (ANTD-A9)
  const entries = within(nav()).getAllByRole('menuitem')
  expect(entries.map(entry => entry.textContent)).toEqual([content.nav.projects, project().name])
  expect(entries.map(entry => entry.getAttribute('aria-current'))).toEqual(['page', null])
  expect(entries.filter(entry => entry.className.includes('ant-menu-item-selected'))).toHaveLength(1)
})

// UC-WP-53
test('theUiSpeaksEnglish', async () => {
  stubAppServer()
  const { container } = renderApp('/projects')

  // R-WP-38: the frame's navigation is the tree in the sider — the list first, then one entry per project
  expect(await within(nav()).findByText(project().name)).toBeInTheDocument()
  expect(within(nav()).getAllByRole('menuitem').map(entry => entry.textContent)).toEqual([
    content.nav.projects,
    project().name,
  ])
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

  // the three pages of R-WP-43 and nothing else, in a navigation of their own: a project entry here would be a
  // second answer to the question the tree beside it answers (R-WP-38)
  expect(within(topNav()).getAllByRole('menuitem').map(entry => entry.textContent)).toEqual([
    content.nav.projects,
    content.nav.chat,
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

  fireEvent.click(within(nav()).getByRole('menuitem', { name: content.nav.projects }))
  await screen.findByLabelText(content.projects.gitUrl)
  expect(settingsEntry(), 'the header entry marks nothing where another page shows').not.toHaveAttribute(
    'aria-current',
  )
  expect(markedEntries().map(entry => entry.textContent)).toEqual([content.nav.projects])

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

  // the tree is the handle of every step below, so the header is only ever read
  fireEvent.click(within(nav()).getByRole('menuitem', { name: content.nav.projects }))
  await screen.findByLabelText(content.projects.gitUrl)
  expect(app.path()).toBe('/projects')
  expect(markedTopEntries().map(entry => entry.textContent)).toEqual([content.nav.projects])

  // the list row opens a chat: a third address, still no click in the header
  fireEvent.click(await screen.findByRole('button', { name: project().name }))
  await screen.findByRole('region', { name: content.chat.title })
  expect(app.path()).toBe(CHAT_PATH)
  expect(markedTopEntries().map(entry => entry.textContent)).toEqual([content.nav.chat])
})

// R-WP-38
// R-WP-43
// UC-WP-76
// UC-WP-77
test('theTopMarkAndTheTreeMarkAgreeInOneRender', async () => {
  // INC-L1-LOW-3: the two menus read one address, so „die Markierung folgt der Adresse und ist einig" only
  // holds **within one render** — two mounts could each be right about another URL. One chat address, both
  // navigations read in the same render: each carries exactly one mark and both name the chat unit.
  stubAppServer()
  renderApp(CHAT_PATH)
  await screen.findByRole('region', { name: content.chat.title })

  const top = within(topNav()).getByRole('menuitem', { name: content.nav.chat })
  const tree = within(nav()).getByRole('menuitem', { name: content.nav.chat })

  expect(top, 'the header names the page the address names').toHaveAttribute('aria-current', 'page')
  expect(tree, 'the tree names the unit the address names').toHaveAttribute('aria-current', 'page')

  // one mark per navigation, so nothing can disagree with the other: the header says nothing about the
  // projects and the settings page, the tree nothing about the project entry or the file area (R-WP-38)
  expect(markedTopEntries(), 'exactly the chat page marks in the header').toEqual([top])
  expect(markedEntries(), 'exactly the chat unit marks in the tree').toEqual([tree])
  expect(topNav().querySelectorAll('[aria-current]')).toHaveLength(1)
  expect(nav().querySelectorAll('[aria-current]')).toHaveLength(1)
})

// R-WP-43
// UC-WP-76
test('theTopChatEntryLeadsToTheLastChat', async () => {
  // UC-WP-51: the entry names the chat on its own, and that address answers with the project this browser
  // last opened — the menu holds no project of its own
  stubAppServer()
  localStorage.setItem('aifactory.lastProject', project().name)
  const app = renderApp('/settings')
  await screen.findByText(content.config.provider)

  fireEvent.click(within(topNav()).getByRole('menuitem', { name: content.nav.chat }))

  expect(await screen.findByRole('region', { name: content.chat.title })).toBeInTheDocument()
  expect(app.path()).toBe(CHAT_PATH)
  expect(markedTopEntries().map(entry => entry.textContent)).toEqual([content.nav.chat])
})

// R-WP-43
// UC-WP-76
test('theTopChatEntryWithoutAProjectLeadsToTheList', async () => {
  // nothing stored is a real answer and not a gap: the chat question stays unanswered, and the list is the
  // page where a project is picked — a fourth page or a guessed project would both be a second answer
  stubAppServer()
  const app = renderApp('/settings')
  await screen.findByText(content.config.provider)

  fireEvent.click(within(topNav()).getByRole('menuitem', { name: content.nav.chat }))

  expect(await screen.findByLabelText(content.projects.gitUrl)).toBeInTheDocument()
  expect(app.path()).toBe('/projects')
  expect(markedTopEntries().map(entry => entry.textContent)).toEqual([content.nav.projects])
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
