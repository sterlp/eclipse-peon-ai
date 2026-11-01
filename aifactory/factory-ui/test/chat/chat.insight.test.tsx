import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'

import { ChatPage } from '../../src/chat/chat.page'
import { project } from '../shared/fixtures'
import type {
  AgentTile,
  AgentTranscript,
  TileCapability,
  TranscriptLine,
} from '../../src/chat/chat.agents.client'

/** What a member's tile offers on the wire — TRANSCRIPT is the one this file is about. */
const MEMBER: TileCapability[] = ['CONTEXT', 'CLEAR', 'COMPACT', 'TRANSCRIPT']
const JON: TileCapability[] = ['CONTEXT', 'CLEAR', 'COMPACT']

interface Wire {
  calls: { method: string; path: string }[]
  /** Every path one method read — "genau dieser Read, und kein zweiter Weg" is a countable statement. */
  paths(method: string): string[]
  closeStream(): Promise<void>
}

let active: Wire | undefined

async function flush(): Promise<void> {
  await act(async () => {
    await new Promise(resolve => setTimeout(resolve, 0))
  })
}

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } })
}

function tile(name: string, capabilities: TileCapability[]): AgentTile {
  return {
    name,
    context: '3k',
    tokens: 3_400,
    estimate: false,
    contextPercent: 3,
    budget: 100_000,
    state: 'IDLE',
    capabilities,
  }
}

function transcriptOf(name: string, lines: TranscriptLine[], truncated = false): AgentTranscript {
  return { name, truncated, lines }
}

/**
 * `fetch` stand-in for the tile family: the roster, one transcript per tile name, and a stream that stays
 * open. A name the server gives no transcript for answers 400 — the panel cannot invent one (R-WP-26).
 */
function stubWire(tiles: AgentTile[], transcripts: Record<string, AgentTranscript>): Wire {
  const calls: Wire['calls'] = []
  let controller: ReadableStreamDefaultController<Uint8Array> | undefined

  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input)
      const method = (init?.method ?? 'GET').toUpperCase()
      calls.push({ method, path })

      if (path.endsWith('/events')) {
        return new Response(
          new ReadableStream<Uint8Array>({
            start(opened) {
              controller = opened
            },
          }),
          { status: 200, headers: { 'content-type': 'text/event-stream' } },
        )
      }
      const named = path.match(/\/agents\/([^/]+)$/)
      if (named !== null) {
        const name = decodeURIComponent(named[1])
        const found = transcripts[name]
        return found === undefined
          ? json(400, { status: 400, error: 'bad request', message: `kein Blick für ${name}` })
          : json(200, found)
      }
      if (path.endsWith('/agents')) {
        return json(200, tiles)
      }
      return json(200, [])
    }),
  )

  const wire: Wire = {
    calls,
    paths: method => calls.filter(call => call.method === method).map(call => call.path),
    closeStream: async () => {
      await act(async () => {
        controller?.close()
        await new Promise(resolve => setTimeout(resolve, 0))
      })
    },
  }
  active = wire
  return wire
}

/** The reads that address one tile's conversation — the roster read is not one of them. */
function transcriptReads(wire: Wire): string[] {
  return wire.paths('GET').filter(path => path.includes('/agents/'))
}

beforeEach(() => {
  cleanup()
  active = undefined
})

afterEach(async () => {
  // the open stream of an unmounted view would leave a pending reader behind — see chat.page.test.tsx
  await active?.closeStream()
  active = undefined
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

// UC-WP-49
test('insightShowsTheMembersOwnConversation', async () => {
  const wire = stubWire([tile('Jon', JON), tile('Dev', MEMBER)], {
    Dev: transcriptOf('Dev', [
      { kind: 'user', text: 'zähl die Dateien im Root' },
      { kind: 'tool', text: 'shellRunCommand({"command":"ls"})' },
      { kind: 'agent', text: 'Zwei Dateien.' },
    ]),
  })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.click(screen.getByTestId('tile-open-Dev'))
  const panel = await screen.findByTestId('insight-panel')

  // exactly the one read of the tile that was clicked, and no second way to a conversation
  expect(transcriptReads(wire)).toEqual(['/api/projects/demo-1a2b3c4/agents/Dev'])
  expect(within(panel).getByTestId('insight-agent')).toHaveTextContent('Dev')
  expect(within(panel).getByText('schreibgeschützt')).toBeInTheDocument()

  await waitFor(() => expect(within(panel).getAllByTestId('insight-line')).toHaveLength(3))
  const lines = within(panel).getAllByTestId('insight-line')
  expect(lines.map(line => line.getAttribute('data-kind'))).toEqual(['user', 'tool', 'agent'])
  expect(within(panel).getByText('zähl die Dateien im Root')).toBeInTheDocument()
  expect(within(panel).getByText('shellRunCommand({"command":"ls"})')).toBeInTheDocument()
  expect(within(panel).getByText('Zwei Dateien.')).toBeInTheDocument()
  // the member's own step is named as one — it is the reason the Blick exists (UC-WP-40 stays sharp)
  expect(within(panel).getAllByText('Werkzeug')).toHaveLength(1)
})

// UC-WP-49
test('insightHasNoWriteControl', async () => {
  stubWire([tile('Dev', MEMBER)], {
    Dev: transcriptOf('Dev', [
      { kind: 'user', text: 'baue die Kachel' },
      { kind: 'agent', text: 'sie steht' },
    ]),
  })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.click(screen.getByTestId('tile-open-Dev'))
  const panel = await screen.findByTestId('insight-panel')
  await waitFor(() => expect(within(panel).getAllByTestId('insight-line')).toHaveLength(2))

  // the only control in the Blick closes it: anything else would write, send or choose
  expect(within(panel).queryAllByRole('button').map(b => b.textContent?.trim())).toEqual(['Schließen'])
  expect(panel.querySelector('input, textarea, select')).toBeNull()
})

// UC-WP-26
test('tileIsNotClickableForSearch', async () => {
  const wire = stubWire([tile('Jon', JON), tile('Search', [])], {})
  render(<ChatPage project={project()} />)
  await flush()

  // a tile that offers no TRANSCRIPT offers no door — the capability set is the whole rule (D-14)
  expect(screen.queryByTestId('tile-open-Jon')).toBeNull()
  expect(screen.queryByTestId('tile-open-Search')).toBeNull()

  fireEvent.click(screen.getByTestId('tile-Jon'))
  fireEvent.click(screen.getByTestId('tile-Search'))
  await flush()

  expect(transcriptReads(wire)).toEqual([])
  expect(screen.queryByTestId('insight-panel')).toBeNull()
})

// UC-WP-49
test('closingTheInsightThenOpeningAnotherTileReadsAgain', async () => {
  const wire = stubWire([tile('Plan', MEMBER), tile('Dev', MEMBER)], {
    Plan: transcriptOf('Plan', [{ kind: 'agent', text: 'Der Plan steht.' }]),
    Dev: transcriptOf('Dev', [{ kind: 'agent', text: 'Der Code steht.' }]),
  })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.click(screen.getByTestId('tile-open-Dev'))
  await screen.findByText('Der Code steht.')

  fireEvent.click(screen.getByTestId('insight-close'))
  expect(screen.queryByTestId('insight-panel')).toBeNull()

  // another tile is another read — the panel holds no conversation of its own
  fireEvent.click(screen.getByTestId('tile-open-Plan'))
  await screen.findByText('Der Plan steht.')
  expect(transcriptReads(wire)).toEqual([
    '/api/projects/demo-1a2b3c4/agents/Dev',
    '/api/projects/demo-1a2b3c4/agents/Plan',
  ])
})
