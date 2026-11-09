import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'

import { ChatPage } from '../../src/chat/chat.page'
import { project } from '../shared/fixtures'
import { content } from '../../src/shared/content'
import { event } from './chat.page.stub'
import type {
  AgentTile,
  AgentTranscript,
  TileCapability,
  TranscriptLine,
} from '../../src/chat/chat.agents.client'

/** What a member's tile offers on the wire — TRANSCRIPT is the one this file is about. */
const MEMBER: TileCapability[] = ['CONTEXT', 'CLEAR', 'COMPACT', 'TRANSCRIPT']
const JON: TileCapability[] = ['CONTEXT', 'CLEAR', 'COMPACT']

const ID = '/api/projects/demo-1a2b3c4'
/** Jon's wire and one member's wire are two connections — exact paths, because both end in `/events`. */
const JON_STREAM = `${ID}/events`
const DEV_STREAM = `${ID}/agents/Dev/events`
const DEV_READ = `${ID}/agents/Dev`

interface Wire {
  calls: { method: string; path: string }[]
  /** Every path one method read — "genau dieser Read, und kein zweiter Weg" is a countable statement. */
  paths(method: string): string[]
  /**
   * The open stream of one exact path. A test writes on a member's wire and proves Jon's wire stayed
   * untouched (UC-WP-90), so the two are not one handle. Fails loudly when nothing stands on that path —
   * a silently missing stream would only make the next `waitFor` time out.
   */
  stream(path: string): { push(text: string): Promise<void> }
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

/** The steps the trace draws, by kind — the member's own account of what he did (R-WP-40). */
function traceOf(kind: 'tool' | 'think', root: Element): Element[] {
  return [...root.querySelectorAll(`.chat-trace-item[data-trace="${kind}"]`)]
}

/**
 * `fetch` stand-in for the tile family: the roster, one transcript per tile name, and one stream **per path**
 * that stays open — the look into a member opens his own wire beside the chat's (R-WP-53). A name the server
 * gives no transcript for answers 400 — the panel cannot invent one (R-WP-26).
 */
function stubWire(tiles: AgentTile[], transcripts: Record<string, AgentTranscript>): Wire {
  const calls: Wire['calls'] = []
  const streams = new Map<string, ReadableStreamDefaultController<Uint8Array>>()
  const encoder = new TextEncoder()

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
              streams.set(path, opened)
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
    stream: path => {
      const found = streams.get(path)
      if (found === undefined) {
        throw new Error(`no stream stands on ${path}`)
      }
      return {
        push: async (text: string) => {
          await act(async () => {
            found.enqueue(encoder.encode(text))
            await new Promise(resolve => setTimeout(resolve, 0))
          })
        },
      }
    },
    closeStream: async () => {
      await act(async () => {
        for (const opened of streams.values()) {
          try {
            opened.close()
          } catch {
            // the test closed this one itself — a second close at tear-down says nothing about the view
          }
        }
        await new Promise(resolve => setTimeout(resolve, 0))
      })
    },
  }
  active = wire
  return wire
}

/**
 * The reads that address one tile's conversation — the roster read is not one of them, and neither is a
 * member's live wire: that one subscribes and reads nothing (R-WP-53).
 */
function transcriptReads(wire: Wire): string[] {
  return wire.paths('GET').filter(path => path.includes('/agents/') && !path.endsWith('/events'))
}

/** The reads of the conversation's own history — the chat's, never the look's (UC-WP-90). */
function historyReads(wire: Wire): string[] {
  return wire.paths('GET').filter(path => path.endsWith('/messages'))
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
// UC-WP-89
test('theAgentViewRendersInTheSharedChatWidget', async () => {
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
  const log = await within(panel).findByTestId('insight-log')

  // exactly the one read of the tile that was clicked, and no second way to a conversation
  expect(transcriptReads(wire)).toEqual([DEV_READ])
  expect(within(panel).getByTestId('insight-agent')).toHaveTextContent('Dev')
  expect(within(panel).getByText(content.insight.readOnly)).toBeInTheDocument()
  // the chat's own log keeps its handle: two logs on one page, two addresses (R-WP-53)
  expect(screen.getByTestId('chat-log')).toBeInTheDocument()
  expect(within(panel).queryByTestId('insight-line'), 'no markup world of the Blick is left').toBeNull()

  // the task he was given, without an author: nobody typed it into this widget (R-WP-4)
  const task = within(log).getByTestId('chat-line-user')
  expect(task.textContent).toBe('zähl die Dateien im Root')
  expect(
    task.querySelector('[data-testid="chat-line-author"]'),
    'the look invents no writer of a member task',
  ).toBeNull()
  // his own step is a link of his trace and not a voice of his own
  expect(traceOf('tool', log).map(step => step.textContent)).toEqual([
    'shellRunCommand({"command":"ls"})',
  ])
  // his answer is the shared widget's answer: Markdown, named by the core's name the read resolved
  const answer = within(log).getByTestId('chat-line-jon')
  expect(answer.querySelector('[data-testid="chat-markdown"]')).not.toBeNull()
  expect(answer.textContent).toContain('Zwei Dateien.')
  expect(within(answer).getByTestId('chat-line-author')).toHaveTextContent('Dev')
  // the log is the last thing of the slide-over: the sheet hangs its measures on that order
  expect(panel.lastElementChild).toBe(log)
})

// UC-WP-49
// UC-WP-89
test('theAgentViewHasNoWriteControl', async () => {
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
  await within(panel).findByTestId('chat-line-jon')

  // the only control in the Blick closes it: anything else would write, send or choose
  expect(within(panel).queryAllByRole('button').map(b => b.textContent?.trim())).toEqual([
    content.common.close,
  ])
  expect(panel.querySelector('input, textarea, select')).toBeNull()
})

// UC-WP-89
test('anAgentWithoutConversationShowsOnlyTheHead', async () => {
  stubWire([tile('Dev', MEMBER)], { Dev: transcriptOf('Dev', []) })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.click(screen.getByTestId('tile-open-Dev'))
  const panel = await screen.findByTestId('insight-panel')
  const log = await within(panel).findByTestId('insight-log')

  // an empty look is the shared log's empty state and no word, no line of the Blick's own
  expect(log.querySelectorAll('[data-testid^="chat-line"]')).toHaveLength(0)
  expect(log.textContent).toContain(content.chat.noMessages)
  expect(within(panel).queryByTestId('insight-empty')).toBeNull()
})

// UC-WP-90
test('theAgentViewGrowsWhileTheMemberWorks', async () => {
  const wire = stubWire([tile('Dev', MEMBER)], {
    Dev: transcriptOf('Dev', [{ kind: 'user', text: 'baue die Kachel' }]),
  })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.click(screen.getByTestId('tile-open-Dev'))
  const panel = await screen.findByTestId('insight-panel')
  const log = await within(panel).findByTestId('insight-log')
  await waitFor(() => expect(wire.paths('GET')).toContain(DEV_STREAM))
  const readsBefore = transcriptReads(wire).length
  const historyBefore = historyReads(wire).length

  await wire.stream(DEV_STREAM).push(event('partial', { kind: 'answer', text: 'Die Kachel **steht**.' }))
  await wire.stream(DEV_STREAM).push(event('tool', { text: 'mvn clean test', origin: 'Dev' }))

  // the look grows on its own wire: an answer of his as Markdown, a step of his as a link of his trace
  await waitFor(() => expect(within(log).getByTestId('chat-live').textContent).toContain('Die Kachel'))
  expect(within(log).getByTestId('chat-live').querySelector('[data-testid="chat-markdown"]')).not.toBeNull()
  const steps = traceOf('tool', log)
  expect(steps).toHaveLength(1)
  expect(steps[0].textContent).toContain('mvn clean test')
  expect(
    steps[0].querySelector('.chat-step-name')?.textContent,
    'the step keeps the stamp his wire carried and the log invents nothing',
  ).toBe('Dev')
  expect(
    panel.querySelectorAll('[data-testid="working-indicator"]'),
    'his wire names no turn end, so no step of the look may claim to be working (R-WP-53)',
  ).toHaveLength(0)
  // and it grew without asking anything: one snapshot, no second read, the chat's history untouched
  expect(transcriptReads(wire)).toHaveLength(readsBefore)
  expect(historyReads(wire)).toHaveLength(historyBefore)
})

// UC-WP-90
test('openingTheViewReadsOnlyThisAgent', async () => {
  const wire = stubWire([tile('Jon', JON), tile('Dev', MEMBER)], {
    Dev: transcriptOf('Dev', [{ kind: 'agent', text: 'Der Code steht.' }]),
  })
  render(<ChatPage project={project()} />)
  await flush()
  const historyBefore = historyReads(wire)

  fireEvent.click(screen.getByTestId('tile-open-Dev'))
  const panel = await screen.findByTestId('insight-panel')
  await within(panel).findByTestId('chat-line-jon')
  await flush()

  // one read of his conversation, then his wire — the order the anchor needs, and nothing else (D-E)
  expect(wire.paths('GET').filter(path => path.includes('/agents/'))).toEqual([DEV_READ, DEV_STREAM])
  expect(wire.paths('GET')).toContain(JON_STREAM)
  expect(historyReads(wire), 'opening the look reads no history again').toEqual(historyBefore)
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
// UC-WP-89
test('closingTheInsightThenOpeningAnotherTileReadsAgain', async () => {
  const wire = stubWire([tile('Plan', MEMBER), tile('Dev', MEMBER)], {
    Plan: transcriptOf('Plan', [{ kind: 'agent', text: 'Der Plan steht.' }]),
    Dev: transcriptOf('Dev', [{ kind: 'agent', text: 'Der Code steht.' }]),
  })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.click(screen.getByTestId('tile-open-Dev'))
  const devPanel = await screen.findByTestId('insight-panel')
  await within(devPanel).findByTestId('chat-line-jon')

  fireEvent.click(screen.getByTestId('insight-close'))
  expect(screen.queryByTestId('insight-panel')).toBeNull()

  // another tile is another read and another wire — the panel holds no conversation of its own
  fireEvent.click(screen.getByTestId('tile-open-Plan'))
  const planPanel = await screen.findByTestId('insight-panel')
  await within(planPanel).findByTestId('chat-line-jon')
  expect(transcriptReads(wire)).toEqual([DEV_READ, `${ID}/agents/Plan`])
})
