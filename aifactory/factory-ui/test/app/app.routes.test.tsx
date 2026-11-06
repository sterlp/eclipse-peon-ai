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
 * The entry the frame marks as showing. A1: the design basis states a menu's selection as the item state
 * of its entry (`ant-menu-item-selected`) and carries no `aria-current`, so the mark is read there — the
 * claim itself is unchanged: exactly one entry shows, and it is the one the address names.
 */
function markedEntry(): HTMLElement {
  const marked = within(nav())
    .getAllByRole('menuitem')
    .filter(entry => entry.className.includes('ant-menu-item-selected'))
  expect(marked, 'exactly one entry of the menu shows').toHaveLength(1)
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

  // exactly the three entries of R-WP-1 — the counter-probe to a fourth page
  expect(within(nav()).getAllByRole('menuitem')).toHaveLength(3)
  expect(await screen.findByLabelText(content.projects.gitUrl)).toBeInTheDocument()

  fireEvent.click(within(nav()).getByRole('menuitem', { name: content.nav.settings }))
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
  // the same click is what the menu's "Chat" needs next time (UC-WP-51)
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
  expect(markedEntry().textContent).toBe(content.nav.chat)
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
  expect(markedEntry().textContent).toBe(content.nav.settings)
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
test('chatOpensTheLastActiveProject', async () => {
  stubAppServer()
  localStorage.setItem('aifactory.lastProject', project().id)
  const app = renderApp('/projects')
  await screen.findByLabelText(content.projects.gitUrl)

  fireEvent.click(within(nav()).getByRole('menuitem', { name: content.nav.chat }))

  expect(await screen.findByRole('region', { name: content.chat.title })).toBeInTheDocument()
  expect(app.path()).toBe(CHAT_PATH)
})

// UC-WP-51
test('chatWithoutAProjectGoesToProjects', async () => {
  stubAppServer()
  const app = renderApp('/settings')
  await screen.findByText(content.config.provider)

  fireEvent.click(within(nav()).getByRole('menuitem', { name: content.nav.chat }))

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
