import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'

import { closeAppStream, renderApp, stubAppServer } from '../shared/app.harness'
import { content } from '../../src/shared/content'
import { project } from '../shared/fixtures'

/**
 * The address is the state of the frame (R-WP-1, UC-WP-52): every test here starts from an address or
 * ends at one, because "the same view after a reload" cannot be shown with a component's state. The
 * memory router is the honest stand-in for a browser reload — mounting again at the same path is what a
 * reload is to this app. The address names its project (`CHAT_PATH`), not a database key (C-3).
 */
const CHAT_PATH = `/chat/${project().name}`

function nav() {
  return screen.getByRole('navigation', { name: content.nav.views })
}

/**
 * The entry the frame marks as showing. ANTD-A1: the design basis states a menu's selection as the item state
 * of its entry (`ant-menu-item-selected`), so the mark is read there — the claim itself is unchanged: exactly
 * one entry shows, and it is the one the address names (R-WP-38).
 */
function markedEntry(): HTMLElement {
  const marked = within(nav())
    .getAllByRole('menuitem')
    .filter(entry => entry.className.includes('ant-menu-item-selected'))
  expect(marked, 'exactly one entry of the tree shows').toHaveLength(1)
  return marked[0]
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
test('theMvpPagesAreReachable', async () => {
  stubAppServer()
  renderApp('/projects')

  // the tree of R-WP-38: the list first, then one entry per project — the counter-probe to a fourth page
  expect(await screen.findByLabelText(content.projects.gitUrl)).toBeInTheDocument()
  await within(nav()).findByText(project().name)
  expect(within(nav()).getAllByRole('menuitem').map(entry => entry.textContent)).toEqual([
    content.nav.projects,
    project().name,
  ])

  fireEvent.click(screen.getByRole('button', { name: content.nav.settings }))
  expect(await screen.findByText(content.config.provider)).toBeInTheDocument()

  fireEvent.click(within(nav()).getByRole('menuitem', { name: content.nav.projects }))
  fireEvent.click(await screen.findByRole('button', { name: project().name }))
  expect(await screen.findByRole('region', { name: content.chat.title })).toBeInTheDocument()

  // the file area and the tiles are inside the chat, not pages of their own (R-WP-1)
  expect(screen.getByTestId('files-open')).toBeInTheDocument()
  expect(await screen.findByTestId('tile-Dev')).toBeInTheDocument()
})

// UC-WP-52
test('openingAProjectChangesTheUrl', async () => {
  stubAppServer()
  const app = renderApp('/projects')
  fireEvent.click(await screen.findByRole('button', { name: project().name }))

  expect(await screen.findByRole('region', { name: content.chat.title })).toBeInTheDocument()
  expect(app.path()).toBe(CHAT_PATH)
  // the same click is what the `/chat` address needs next time (UC-WP-51)
  expect(localStorage.getItem('aifactory.lastProject')).toBe(project().name)
})

// UC-WP-70
test('theChatAddressUsesTheProjectName', async () => {
  // C-3: the address reads as what the tree shows — a click on an entry answers with that entry's name
  stubAppServer()
  const app = renderApp('/projects')
  fireEvent.click(await within(nav()).findByRole('menuitem', { name: project().name }))

  expect(await screen.findByRole('region', { name: content.chat.title })).toBeInTheDocument()
  expect(app.path()).toBe(`/chat/${project().name}`)
  expect(app.path()).not.toContain(project().id)
})

// UC-WP-52
// UC-WP-43
test('aDeepLinkShowsTheSameState', async () => {
  stubAppServer()
  const app = renderApp(CHAT_PATH)

  const chat = await screen.findByRole('region', { name: content.chat.title })
  expect(within(chat).getByRole('heading', { name: project().name })).toBeInTheDocument()
  expect(app.path()).toBe(CHAT_PATH)
  // the entry that shows is the one the address names, not one a click left behind
  expect(markedEntry().textContent).toBe(project().name)
})

// UC-WP-70
test('theNameAddressOpensTheProjectItNames', async () => {
  // the counter-proof of C-3: two projects are in the registry, the address names the second one, so a
  // reading that took the first entry it knows would answer with another project's chat
  const second = project({
    id: 'other-9f8e7d6',
    name: 'other',
    gitUrl: 'https://git.example.com/acme/other.git',
  })
  stubAppServer([project(), second])
  const app = renderApp(`/chat/${second.name}`)

  const chat = await screen.findByRole('region', { name: content.chat.title })
  expect(within(chat).getByRole('heading', { name: second.name })).toBeInTheDocument()
  expect(within(chat).queryByRole('heading', { name: project().name })).toBeNull()
  expect(app.path()).toBe(`/chat/${second.name}`)
  expect(markedEntry().textContent).toBe(second.name)
})

// UC-WP-70
test('anEscapedNameAddressOpensItsChatAndMarksItsEntry', async () => {
  // a name with a space is what a hand-edited `projects.json` can carry — the backend refuses it (`SAFE_NAME`)
  // — and then the address speaks the escaped form while the chat resolves the decoded one: one answer, not
  // two that disagree about the spelling of the same project (R-WP-38, UC-WP-52)
  const spaced = project({
    id: 'deep-1b2c3d4',
    name: 'deep dive',
    gitUrl: 'https://git.example.com/acme/deep-dive.git',
  })
  stubAppServer([spaced])
  const app = renderApp('/chat/deep%20dive')

  const chat = await screen.findByRole('region', { name: content.chat.title })
  expect(within(chat).getByRole('heading', { name: spaced.name })).toBeInTheDocument()
  expect(app.path()).toBe('/chat/deep%20dive')
  expect(markedEntry().textContent).toBe(spaced.name)
})

// UC-WP-43
test('aReloadStaysOnThePage', async () => {
  stubAppServer()
  const opened = renderApp(CHAT_PATH)
  await screen.findByRole('region', { name: content.chat.title })
  opened.unmount()
  await closeAppStream()

  // a reload is a new mount at the same address: the chat of the same project, frame and all
  const reloaded = renderApp(CHAT_PATH)
  const chat = await screen.findByRole('region', { name: content.chat.title })
  expect(within(chat).getByRole('heading', { name: project().name })).toBeInTheDocument()
  expect(reloaded.path()).toBe(CHAT_PATH)
  expect(within(chat).getByRole('button', { name: content.chat.send })).toBeInTheDocument()
})

// UC-WP-43
test('aReloadStaysOnSettings', async () => {
  stubAppServer()
  const opened = renderApp('/settings')
  await screen.findByText(content.config.provider)
  opened.unmount()

  renderApp('/settings')
  expect(await screen.findByText(content.config.provider)).toBeInTheDocument()
  // the mark of this page sits on the header's entry, not in the tree: Settings is a page and the tree
  // answers which project (R-WP-38)
  expect(screen.getByRole('button', { name: content.nav.settings })).toHaveAttribute('aria-current', 'page')
})

// UC-WP-43
test('anUnknownAddressShowsTheApp', async () => {
  stubAppServer()
  const app = renderApp('/no/such/route')

  // the server answered the app for this address (UiDeliveryTest), the client routes it to the list —
  // a page of its own would be a fourth view (R-WP-1)
  expect(await screen.findByLabelText(content.projects.gitUrl)).toBeInTheDocument()
  expect(app.path()).toBe('/projects')
  // the mark follows the address and not the way it was reached: the redirect lands on the list, whose own
  // entry the tree shows (R-WP-38) — an address nobody clicked in the tree
  await waitFor(() => expect(markedEntry().textContent).toBe(content.nav.projects))
})

// UC-WP-51
// INC-5A-D2: the trigger is the address now — R-WP-38 removed the "Chat" entry, so a bookmark and a
// redirect are what this use case is about. Both claims below are the ones that were already here.
test('chatOpensTheLastActiveProject', async () => {
  stubAppServer()
  localStorage.setItem('aifactory.lastProject', project().name)
  const app = renderApp('/chat')

  expect(await screen.findByRole('region', { name: content.chat.title })).toBeInTheDocument()
  expect(app.path()).toBe(CHAT_PATH)
  // same mount, address nobody clicked in the tree: `/chat` became this project's chat, so the tree shows
  // exactly that entry (R-WP-38) — the mark is the address, not what the mount found first
  await waitFor(() => expect(markedEntry().textContent).toBe(project().name))
})

// UC-WP-51
// INC-5A-D2
test('chatWithoutAProjectGoesToProjects', async () => {
  stubAppServer()
  const app = renderApp('/chat')

  expect(await screen.findByLabelText(content.projects.gitUrl)).toBeInTheDocument()
  expect(app.path()).toBe('/projects')
  // and the tree answers the address it landed on, not the one it was called with (R-WP-38)
  await waitFor(() => expect(markedEntry().textContent).toBe(content.nav.projects))
})

test('aProjectThatIsGoneLeadsToTheList', async () => {
  stubAppServer([])
  const app = renderApp(CHAT_PATH)

  // no chat for a segment the registry does not hold, and no new page either (R-WP-1)
  expect(await screen.findByLabelText(content.projects.gitUrl)).toBeInTheDocument()
  expect(app.path()).toBe('/projects')
  expect(screen.queryByRole('region', { name: content.chat.title })).toBeNull()
  // the redirect left the tree nothing to name but the list: an unknown segment marks no project, the address
  // it landed on marks the list's own entry (R-WP-38, `navKeyOfPathname`)
  await waitFor(() => expect(markedEntry().textContent).toBe(content.nav.projects))
})

// UC-WP-70
test('aNewProjectAppearsInTheSider', async () => {
  // one owner of the list (D1): the frame reads it once and both the tree and the page show that read
  const registry = [project()]
  stubAppServer(registry)
  renderApp('/projects')
  const added = project({
    id: 'other-9f8e7d6',
    name: 'other',
    gitUrl: 'https://git.example.com/acme/other.git',
  })
  await screen.findByLabelText(content.projects.gitUrl)

  fireEvent.change(screen.getByLabelText(content.projects.gitUrl), {
    target: { value: added.gitUrl },
  })
  // the registry answers for both readers from here on: the page's row and the tree's entry
  registry.push(added)
  fireEvent.click(screen.getByRole('button', { name: content.projects.add }))

  expect(await within(nav()).findByText(added.name)).toBeInTheDocument()
  expect(within(nav()).getAllByRole('menuitem').map(entry => entry.textContent)).toEqual([
    content.nav.projects,
    project().name,
    added.name,
  ])
})
