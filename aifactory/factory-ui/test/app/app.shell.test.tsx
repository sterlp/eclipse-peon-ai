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

beforeEach(() => {
  cleanup()
  localStorage.clear()
})

afterEach(async () => {
  await closeAppStream()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
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

  // settings: the field names of the form, reached from the header
  fireEvent.click(screen.getByRole('button', { name: content.nav.settings }))
  expect(await screen.findByText(content.config.provider)).toBeInTheDocument()
  expect(screen.getByText(content.config.model)).toBeInTheDocument()

  // what the screen actually shows carries no German left — the catalog is the only source of copy
  await flush()
  expect(container.textContent).not.toMatch(/[ÄÖÜäöüß]/)
})

// R-WP-38
test('theHeaderCarriesBrandUserAndSettingsOnly', async () => {
  stubAppServer()
  const { container } = renderApp('/projects')
  await screen.findByLabelText(content.projects.gitUrl)

  const header = container.querySelector('.app-header') as HTMLElement
  expect(header.textContent).toContain(content.brand)
  expect(within(header).getByRole('button', { name: content.nav.settings })).toBeInTheDocument()
  expect(within(header).getByLabelText(content.user.name)).toBeInTheDocument()

  // no view entry of its own: the tree beside it says where the viewer is (R-WP-38), so a second set of
  // entries would be a second answer to keep in step with the URL
  expect(within(header).queryAllByRole('menuitem')).toEqual([])
  expect(within(header).queryByText(content.nav.projects)).toBeNull()
  // the brand is a word and not a way to navigate
  expect(within(header).queryAllByRole('link')).toEqual([])
})

// R-WP-38
// R-WP-1
test('theSettingsEntryIsCurrentOnSettings', async () => {
  stubAppServer()
  const onSettings = renderApp('/settings')
  await screen.findByText(content.config.provider)

  expect(screen.getByRole('button', { name: content.nav.settings })).toHaveAttribute(
    'aria-current',
    'page',
  )

  // the mark is the address and not the last click: the same entry says nothing where another page shows
  onSettings.unmount()
  renderApp('/projects')
  await screen.findByLabelText(content.projects.gitUrl)
  expect(screen.getByRole('button', { name: content.nav.settings })).not.toHaveAttribute('aria-current')
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
