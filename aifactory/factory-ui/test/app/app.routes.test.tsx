import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, screen, within } from '@testing-library/react'

import { closeAppStream, renderApp, stubAppServer } from '../shared/app.harness'
import { content } from '../../src/shared/content'
import { project } from '../shared/fixtures'

/**
 * The address is the state of the frame (R-WP-1, UC-WP-52): every test here starts from an address or
 * ends at one, because "the same view after a reload" cannot be shown with a component's state. The
 * memory router is the honest stand-in for a browser reload — mounting again at the same path is what a
 * reload is to this app.
 */
const CHAT_PATH = `/chat/${project().id}`

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
  expect(localStorage.getItem('aifactory.lastProject')).toBe(project().id)
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
})

// UC-WP-51
// INC-5A-D2: the trigger is the address now — R-WP-38 removed the "Chat" entry, so a bookmark and a
// redirect are what this use case is about. Both claims below are the ones that were already here.
test('chatOpensTheLastActiveProject', async () => {
  stubAppServer()
  localStorage.setItem('aifactory.lastProject', project().id)
  const app = renderApp('/chat')

  expect(await screen.findByRole('region', { name: content.chat.title })).toBeInTheDocument()
  expect(app.path()).toBe(CHAT_PATH)
})

// UC-WP-51
// INC-5A-D2
test('chatWithoutAProjectGoesToProjects', async () => {
  stubAppServer()
  const app = renderApp('/chat')

  expect(await screen.findByLabelText(content.projects.gitUrl)).toBeInTheDocument()
  expect(app.path()).toBe('/projects')
})

test('aProjectThatIsGoneLeadsToTheList', async () => {
  stubAppServer([])
  const app = renderApp(CHAT_PATH)

  // no chat for an id the registry does not hold, and no new page either (R-WP-1)
  expect(await screen.findByLabelText(content.projects.gitUrl)).toBeInTheDocument()
  expect(app.path()).toBe('/projects')
  expect(screen.queryByRole('region', { name: content.chat.title })).toBeNull()
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
