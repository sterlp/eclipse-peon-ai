import { vi } from 'vitest'
import { act, cleanup, screen, within } from '@testing-library/react'

import type { ChatMessage } from '../../src/chat/chat.client'
import type { AgentTile, AgentTileState } from '../../src/chat/chat.agents.client'

export interface Chat {
  calls: { method: string; path: string; body?: string }[]
  /** How often an edge was called — the count <em>is</em> the assertion behind "ohne neu zu laden". */
  countOf(pathEnd: string, method: string): number
  /** The answer of the next history read, so a test can hand back what the turn wrote. */
  setMessages(messages: ChatMessage[]): void
  /** The roster the next `GET …/agents` answers with — what the board shows after a connect (R-WP-7). */
  setAgents(tiles: AgentTile[]): void
  /**
   * Holds the answer of the next `GET …/events` back until the test resolves it. The answer is what tells the
   * view its subscription stands, so a held answer is exactly a connection that does not stand yet
   * (R-WP-48). Resolve it before the test ends: an unanswered request leaves the view waiting for a stream.
   */
  deferNextStreamOpen(): { resolve(): void }
  /**
   * Holds the next history read open until the test resolves it — a read in flight, without a sleep, so a
   * test can say what the screen looks like <em>while</em> the server is still answering. May be used twice:
   * the reads are held in the order they were asked for. `answersWith` hands that read the conversation as
   * it stood when it started, whatever the store holds by the time it answers — two reads of one view
   * racing is what C-5 is about.
   */
  deferNextHistoryRead(answersWith?: ChatMessage[]): { resolve(): void }
  /**
   * Holds the answer of the next `GET …/agents` back until the test resolves it — the roster read that is
   * still on the wire while the stream already carries a newer snapshot (UC-WP-84 GIVEN 3). `answersWith`
   * answers exactly that read. Resolve it before the test ends: an unanswered read leaves a promise open.
   */
  deferNextAgentsRead(answersWith?: AgentTile[]): { resolve(): void }
  /** Every `POST …/agents` answers this instead of 202 — the refusal a running turn produces. */
  refuseActions(status: number, message: string): void
  /** Every `POST …/messages` answers this instead of 202 — the send that never landed. */
  refuseSend(status: number, message: string): void
  /** Every `POST …/messages/stop` answers this instead of 202 — the refusal of an ended turn. */
  setStopAnswer(status: number, message: string): void
  /** Writes bytes onto the open stream — may stop in the middle of an event. */
  push(text: string): Promise<void>
  closeStream(): Promise<void>
}

export const QUESTION: ChatMessage = { role: 'user', text: 'wo liegen die Dateien?' }
/** A user line the server named — the shape of R-WP-25: the author is a person, not an agent. */
export const ANNAS_QUESTION: ChatMessage = { role: 'user', text: 'bitte X', origin: 'Anna' }
export const ANSWER: ChatMessage = { role: 'jon', text: 'Im Root liegen: pom.xml und README.md' }
/** A team member's final answer — role `jon` with an author, never a third role (UC-WP-39). */
export const TEAM_ANSWER: ChatMessage = { role: 'jon', text: 'Eine Banane.', origin: 'Dev' }
export const JONS_ANSWER: ChatMessage = { role: 'jon', text: 'Dev nennt eine Banane.', origin: 'Jon' }

/** The unified diff the wire carries for one written file (R-WP-36) — as the core's `SimpleDiff` writes it. */
export const DIFF = '--- a/src/App.java\n+++ b/src/App.java\n@@ -1,2 +1,3 @@\n a\n+b\n \n'

/** One roster entry as the server sends it — the tile family of R-WP-7. */
export function tile(name: string, state: AgentTileState): AgentTile {
  return {
    name,
    context: '3k',
    tokens: 3_400,
    estimate: false,
    contextPercent: 3,
    budget: 100_000,
    state,
    capabilities: ['CONTEXT', 'CLEAR', 'COMPACT'],
  }
}

let active: Chat | undefined

/**
 * Lets the reader loop, the stubbed `fetch` and the state update behind it settle — every write to
 * the wire happens inside `act`, so nothing updates the view from outside it.
 */
export async function flush(): Promise<void> {
  await act(async () => {
    await new Promise(resolve => setTimeout(resolve, 0))
  })
}

/**
 * `fetch` stand-in for the chat edges: the conversation edges (send, stop, events) and the tile family
 * (R-WP-7). The stream body is a `ReadableStream` the test writes into byte by byte, so a chunk may end
 * anywhere — including inside one event's JSON.
 */
export function stubChat(
  initial: { messages?: ChatMessage[]; projects?: unknown[]; agents?: AgentTile[] } = {},
): Chat {
  const calls: Chat['calls'] = []
  const encoder = new TextEncoder()
  const state = {
    messages: initial.messages ?? [],
    agents: initial.agents ?? [],
    refusal: undefined as { status: number; message: string } | undefined,
    sendRefusal: undefined as { status: number; message: string } | undefined,
    stopAnswer: undefined as { status: number; message: string } | undefined,
  }
  let controller: ReadableStreamDefaultController<Uint8Array> | undefined
  // the history reads the test holds open, in the order they were asked for — each with the conversation it
  // is allowed to bring back, which is the point of a slow answer: the conversation moved on since
  let heldReads: { wait: Promise<void>; answersWith?: ChatMessage[] }[] = []
  // the stream answers the test holds back: no answer means no subscription, which is what R-WP-48 reads
  let heldOpens: { wait: Promise<void> }[] = []
  // the roster reads the test holds open, in the order they were asked for — a read on the wire is the race
  // UC-WP-84 GIVEN 3 is about: the snapshot can overtake it
  let heldAgentReads: { wait: Promise<void>; answersWith?: AgentTile[] }[] = []

  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input)
      const method = (init?.method ?? 'GET').toUpperCase()
      calls.push({ method, path, body: init?.body as string | undefined })

      if (path.endsWith('/events')) {
        const answered = () =>
          new Response(
            new ReadableStream<Uint8Array>({
              start(opened) {
                controller = opened
              },
            }),
            { status: 200, headers: { 'content-type': 'text/event-stream' } },
          )
        // built when the answer goes out and not when it was asked for: the stream a held answer finally
        // opens is the stream the test writes into from then on
        const [heldOpen, ...restOpens] = heldOpens
        heldOpens = restOpens
        return heldOpen === undefined ? answered() : heldOpen.wait.then(answered)
      }
      if (path.endsWith('/agents') && method === 'POST') {
        if (state.refusal !== undefined) {
          return json(state.refusal.status, {
            status: state.refusal.status,
            error: 'conflict',
            message: state.refusal.message,
          })
        }
        return new Response(null, { status: 202 })
      }
      if (path.endsWith('/agents')) {
        const [held, ...rest] = heldAgentReads
        heldAgentReads = rest
        if (held === undefined) {
          return json(200, state.agents)
        }
        // answered when the test lets it go and with the roster it was promised: the whole point of a held
        // read is that the board has moved on by the time it answers
        return held.wait.then(() => json(200, held.answersWith ?? state.agents))
      }
      if (path.endsWith('/messages/stop')) {
        if (state.stopAnswer !== undefined) {
          return json(state.stopAnswer.status, {
            status: state.stopAnswer.status,
            error: 'conflict',
            message: state.stopAnswer.message,
          })
        }
        return new Response(null, { status: 202 })
      }
      if (path.endsWith('/messages') && method === 'POST') {
        if (state.sendRefusal !== undefined) {
          return json(state.sendRefusal.status, {
            status: state.sendRefusal.status,
            error: 'unavailable',
            message: state.sendRefusal.message,
          })
        }
        return new Response(null, { status: 202 })
      }
      if (path.endsWith('/messages')) {
        const [held, ...rest] = heldReads
        heldReads = rest
        if (held === undefined) {
          // read at answer time, not at request time: the test decides what the conversation holds
          return json(200, state.messages)
        }
        return held.wait.then(() => json(200, held.answersWith ?? state.messages))
      }
      return json(200, initial.projects ?? [])
    }),
  )

  const chat: Chat = {
    calls,
    countOf: (pathEnd, method) =>
      calls.filter(call => call.method === method && call.path.endsWith(pathEnd)).length,
    setMessages: messages => {
      state.messages = messages
    },
    setAgents: tiles => {
      state.agents = tiles
    },
    deferNextStreamOpen: () => {
      let settle: (() => void) | undefined
      const wait = new Promise<void>(done => {
        settle = done
      })
      heldOpens = [...heldOpens, { wait }]
      return {
        resolve: () => {
          settle?.()
        },
      }
    },
    deferNextHistoryRead: answersWith => {
      let settle: (() => void) | undefined
      const wait = new Promise<void>(done => {
        settle = done
      })
      heldReads = [...heldReads, { wait, answersWith }]
      return {
        resolve: () => {
          settle?.()
        },
      }
    },
    deferNextAgentsRead: answersWith => {
      let settle: (() => void) | undefined
      const wait = new Promise<void>(done => {
        settle = done
      })
      heldAgentReads = [...heldAgentReads, { wait, answersWith }]
      return {
        resolve: () => {
          settle?.()
        },
      }
    },
    refuseActions: (status, message) => {
      state.refusal = { status, message }
    },
    refuseSend: (status, message) => {
      state.sendRefusal = { status, message }
    },
    setStopAnswer: (status, message) => {
      state.stopAnswer = { status, message }
    },
    push: async (text: string) => {
      await act(async () => {
        controller?.enqueue(encoder.encode(text))
        await new Promise(resolve => setTimeout(resolve, 0))
      })
    },
    closeStream: async () => {
      await act(async () => {
        try {
          controller?.close()
        } catch {
          // the test closed its own stream — the tear-down of every test closes it once more, and an
          // already closed stream is the end of the story, not a failure of this one
        }
        await new Promise(resolve => setTimeout(resolve, 0))
      })
    },
  }
  active = chat
  return chat
}

export function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } })
}

/** One SSE event exactly as the server writes it — no space after the colon. */
export function event(name: string, payload: unknown): string {
  return `event:${name}\ndata:${JSON.stringify(payload)}\n\n`
}

/** A keep-alive comment: the other thing a real wire puts between the events. */
export const KEEP_ALIVE = ':ping\n\n'

/** The view is gone and no stub of the previous test is left standing. */
export function resetChatStub(): void {
  cleanup()
  active = undefined
}

/** Closing is awaited: an unfinished act() of this test would overlap the act() of the next one. */
export async function closeChatStub(): Promise<void> {
  await active?.closeStream()
  active = undefined
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
}
/**
 * A scroll to measure: jsdom knows no layout, so the log gets its geometry from the test and keeps
 * `scrollTop` writable — the page's numbers stay what the test states (R-WP-35).
 */
export function mockScrollOf(area: HTMLElement, scrollHeight: number, clientHeight: number): void {
  let scrollTop = 0
  Object.defineProperty(area, 'scrollHeight', { configurable: true, value: scrollHeight })
  Object.defineProperty(area, 'clientHeight', { configurable: true, value: clientHeight })
  Object.defineProperty(area, 'scrollTop', {
    configurable: true,
    get: () => scrollTop,
    set: (value: number) => {
      scrollTop = value
    },
  })
}
/**
 * The lines wearing a waiting frame — the frame R-WP-31 no longer allows (UC-WP-65). A class nothing paints
 * proves nothing on its own, so the one test that asks for it asks the sheet as well (`declares`).
 */
export function framedLines(): Element[] {
  return Array.from(document.querySelectorAll('.chat-line.pending'))
}

/** The author labels of the agent lines, in reading order — the shape of R-WP-4's claim. */
export function agentAuthorsOnScreen(): (string | null)[] {
  return authorsOfLines('chat-line-jon')
}

/** The texts the queue got, in the order they were posted — what a send really put on the wire. */
export function sentTexts(chat: Chat): string[] {
  return chat.calls
    .filter(call => call.method === 'POST' && call.path.endsWith('/messages'))
    .map(call => String(JSON.parse(call.body ?? '{}').text))
}

/**
 * The agent lines that say exactly this. An answer reads as Markdown (R-WP-28) and the renderer paints a
 * sentence in its own tokens, so the row is the unit a test asks for — never one span of it.
 */
export function agentLinesSaying(text: string): HTMLElement[] {
  return screen.getAllByTestId('chat-line-jon').filter(line => line.textContent === text)
}

/** The author labels of the user lines, in reading order — what every viewer reads (R-WP-25, UC-WP-48). */
export function userAuthorsOnScreen(): (string | null)[] {
  return authorsOfLines('chat-line-user')
}

/**
 * The texts of the user lines in reading order — his provisional ones and the history's, without the
 * author label (R-WP-37 counts lines, so the test counts them the same way).
 */
export function userLineTexts(): (string | null)[] {
  return [
    ...screen
      .getByTestId('chat-log')
      .querySelectorAll('[data-testid="chat-line-user"] .chat-text'),
  ].map(line => line.textContent)
}

export function authorsOfLines(lineTestId: string): (string | null)[] {
  return screen
    .getAllByTestId(lineTestId)
    .flatMap(line => within(line).queryAllByTestId('chat-line-author').map(label => label.textContent))
}
