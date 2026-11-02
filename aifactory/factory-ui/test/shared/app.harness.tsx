import { act, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'

import { AppRoutes } from '../../src/app/app.routes'
import { CurrentPath } from './current.path'
import { project } from './fixtures'
import type { AgentTile } from '../../src/chat/chat.agents.client'

/** The roster a routed project shows — one member, nothing invented (R-WP-7). */
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

/** The settings answer of the factory — masked, with no key of its own. */
const SETTINGS = {
  provider: 'OPEN_AI',
  model: 'gpt-demo',
  url: null,
  tokenWindow: null,
  timeoutSeconds: null,
  sendThinking: false,
  keySet: false,
  keySource: 'NONE',
}

/** The provider list the settings page reads — one entry, the one SETTINGS names (UC-WP-62). */
const PROVIDERS = [{ name: 'OPEN_AI', defaultUrl: null, thinkSupported: true }]

let stream: ReadableStreamDefaultController<Uint8Array> | undefined

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

/**
 * The edges every view reads: the list (one project by default), an empty history, the roster, the
 * settings, and a stream that stays open. A test that needs more — a pushed event, a refusal, another
 * list between the steps — stubs `fetch` itself; this one only keeps the routed app from hanging.
 */
export function stubAppServer(projects: unknown[] = [project()]): void {
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
        return json(200, SETTINGS)
      }
      if (path === '/api/settings/providers') {
        return json(200, PROVIDERS)
      }
      if (path.endsWith('/agents')) {
        return json(200, [DEV])
      }
      return json(200, path === '/api/projects' ? projects : [])
    }),
  )
}

/** The open stream of a mounted view would otherwise leave a pending reader behind (see chat.page.test). */
export async function closeAppStream(): Promise<void> {
  await act(async () => {
    stream?.close()
    stream = undefined
    await new Promise((resolve) => setTimeout(resolve, 0))
  })
}

/**
 * The app under a memory router (UC-WP-52): the address is the input, and the rendered path is readable —
 * which is exactly the claim under test, that nothing shows which the address does not name.
 */
export function renderApp(path = '/projects') {
  const view = render(
    <MemoryRouter initialEntries={[path]}>
      <AppRoutes />
      <CurrentPath />
    </MemoryRouter>,
  )
  return {
    ...view,
    path: (): string => screen.getByTestId('current-path').textContent ?? '',
  }
}
