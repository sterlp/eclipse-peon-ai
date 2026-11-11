import { act, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'

import { AppRoot } from '../../src/app/app.root'
import { AppRoutes } from '../../src/app/app.routes'
import { CurrentPath } from './current.path'
import { project } from './fixtures'
import type { AgentTile } from '../../src/chat/chat.agents.client'

/**
 * The roster a routed project shows — one member, nothing invented (R-WP-7). Exported, because a test that
 * needs the door to a tile's own conversation adds `TRANSCRIPT` to this one instead of writing a second tile.
 */
export const DEV: AgentTile = {
  name: 'Dev',
  context: '3k',
  tokens: 3_400,
  estimate: false,
  contextPercent: 3,
  budget: 100_000,
  state: 'IDLE',
  capabilities: ['CONTEXT', 'CLEAR', 'COMPACT'],
  stoppable: false,
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

/**
 * The agent slots as the settings page reads them (R-WP-23): none overridden, which is the state of a
 * home nobody has configured. The section itself is tested in `config.agents.test.tsx`; here it only has
 * to answer, and it must do so before the chat roster below — both paths end in `/agents`.
 */
const AGENTS: unknown[] = []

/**
 * Every stream a stubbed view opened. The chat opens one and the look into a member opens another (R-WP-53),
 * so the tear-down has to close all of them and not whichever one answered last.
 */
let streams: ReadableStreamDefaultController<Uint8Array>[] = []

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
 *
 * `roster` is the team of the routed project. The default has no `TRANSCRIPT`, so no tile grows the door
 * into a conversation and every existing test sees exactly one member; a test that wants that door adds the
 * capability. Such a test then reads an **empty** transcript: the panel itself is the claim, never its lines.
 */
export function stubAppServer(projects: unknown[] = [project()], roster: AgentTile[] = [DEV]): void {
  streams = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input)
      if (path.endsWith('/events')) {
        return new Response(
          new ReadableStream<Uint8Array>({
            start(opened) {
              streams = [...streams, opened]
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
      if (path === '/api/settings/agents') {
        return json(200, AGENTS)
      }
      // one member's own conversation — asked by path, so it answers for whichever tile was clicked. Before
      // the roster branch: that one matches on the ending, and both of these paths end in `/agents`.
      const transcript = /^.*\/agents\/([^/]+)$/.exec(path)
      if (transcript !== null) {
        return json(200, { name: transcript[1], truncated: false, lines: [] })
      }
      if (path.endsWith('/agents')) {
        return json(200, roster)
      }
      return json(200, path === '/api/projects' ? projects : [])
    }),
  )
}

/** The open stream of a mounted view would otherwise leave a pending reader behind (see chat.page.test). */
export async function closeAppStream(): Promise<void> {
  const open = streams
  streams = []
  await act(async () => {
    for (const controller of open) {
      try {
        controller.close()
      } catch {
        // the view closed its own wire already — closing it once more says nothing about it
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 0))
  })
}

/**
 * The app under a memory router (UC-WP-52): the address is the input, and the rendered path is readable —
 * which is exactly the claim under test, that nothing shows which the address does not name.
 *
 * Inside {@link AppRoot}, which is how the entry mounts it: the frame owns the colour mode (UC-WP-71) and the
 * design basis, so a view rendered beside it would sit outside the tree every real screen sits in.
 */
export function renderApp(path = '/projects') {
  const view = render(
    <AppRoot>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
        <CurrentPath />
      </MemoryRouter>
    </AppRoot>,
  )
  return {
    ...view,
    path: (): string => screen.getByTestId('current-path').textContent ?? '',
  }
}
