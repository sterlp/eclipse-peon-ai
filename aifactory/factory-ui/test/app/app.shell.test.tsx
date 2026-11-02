import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react'

import AppShell from '../../src/app/app.shell'
import { content } from '../../src/shared/content'
import { project } from '../shared/fixtures'
import type { AgentTile } from '../../src/chat/chat.agents.client'

/** One member on the wire — the tile row of the opened project. */
const DEV: AgentTile = {
  name: 'Dev',
  context: '3k',
  tokens: 3_400,
  estimate: false,
  contextPercent: 3,
  budget: 100_000,
  state: 'IDLE',
  capabilities: ['CONTEXT', 'CLEAR', 'COMPACT'],
}

let stream: ReadableStreamDefaultController<Uint8Array> | undefined

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } })
}

/** The whole app over one stub: the list, one project with a tile, the stream and the settings. */
function stubServer(): void {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input)
      if (path.endsWith('/events')) {
        return new Response(
          new ReadableStream<Uint8Array>({
            start(opened) {
              stream = opened
            },
          }),
          { status: 200, headers: { 'content-type': 'text/event-stream' } },
        )
      }
      if (path === '/api/settings') {
        return json(200, {
          provider: 'OPEN_AI',
          model: 'gpt-demo',
          url: null,
          tokenWindow: null,
          timeoutSeconds: null,
          sendThinking: false,
          keySet: false,
          keySource: 'NONE',
        })
      }
      if (path.endsWith('/agents')) {
        return json(200, [DEV])
      }
      return json(200, path === '/api/projects' ? [project()] : [])
    }),
  )
}

async function flush(): Promise<void> {
  await act(async () => {
    await new Promise(resolve => setTimeout(resolve, 0))
  })
}

beforeEach(() => {
  cleanup()
  localStorage.clear()
})

afterEach(async () => {
  // an open stream of an unmounted view would leave a pending reader behind — see chat.page.test.tsx
  await act(async () => {
    stream?.close()
    stream = undefined
    await new Promise(resolve => setTimeout(resolve, 0))
  })
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

// UC-WP-53
test('theUiSpeaksEnglish', async () => {
  stubServer()
  const { container } = render(<AppShell />)

  const nav = screen.getByRole('navigation', { name: content.nav.views })
  expect(within(nav).getAllByRole('button').map(entry => entry.textContent)).toEqual([
    content.nav.projects,
    content.nav.chat,
    content.nav.settings,
  ])
  expect(screen.getByLabelText(content.user.name)).toBeInTheDocument()

  // the projects view: its form and its empty affordance are the catalog's words
  expect(await screen.findByLabelText(content.projects.gitUrl)).toBeInTheDocument()
  expect(screen.getByRole('button', { name: content.projects.add })).toBeInTheDocument()

  // the chat of a project: composer, tile state and the file area's affordance
  fireEvent.click(await screen.findByRole('button', { name: 'demo' }))
  const chat = await screen.findByRole('region', { name: content.chat.title })
  expect(within(chat).getByRole('button', { name: content.chat.send })).toBeInTheDocument()
  expect(within(chat).getByRole('button', { name: content.chat.stop })).toBeInTheDocument()
  expect(within(chat).getByTestId('tile-state-Dev')).toHaveTextContent(content.tiles.state.IDLE)
  expect(screen.getByTestId('files-open')).toHaveTextContent(content.files.open)

  // settings: the field names of the form
  fireEvent.click(within(nav).getByRole('button', { name: content.nav.settings }))
  expect(await screen.findByText(content.config.provider)).toBeInTheDocument()
  expect(screen.getByText(content.config.model)).toBeInTheDocument()

  // what the screen actually shows carries no German left — the catalog is the only source of copy
  await flush()
  expect(container.textContent).not.toMatch(/[ÄÖÜäöüß]/)
})
