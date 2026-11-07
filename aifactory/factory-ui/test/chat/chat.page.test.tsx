import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'

import { renderApp } from '../shared/app.harness'
import { baseDeclarations, declarations, declares, tokenValues } from '../shared/sheets'
import { separatorsOf } from '../shared/log.parts'
import { ChatPage } from '../../src/chat/chat.page'
import { project } from '../shared/fixtures'
import { content } from '../../src/shared/content'
import type { ChatMessage } from '../../src/chat/chat.client'
import type { AgentTile, AgentTileState } from '../../src/chat/chat.agents.client'

interface Chat {
  calls: { method: string; path: string; body?: string }[]
  /** How often an edge was called — the count <em>is</em> the assertion behind "ohne neu zu laden". */
  countOf(pathEnd: string, method: string): number
  /** The answer of the next history read, so a test can hand back what the turn wrote. */
  setMessages(messages: ChatMessage[]): void
  /**
   * Holds the next history read open until the test resolves it — a read in flight, without a sleep, so a
   * test can say what the screen looks like <em>while</em> the server is still answering. May be used twice:
   * the reads are held in the order they were asked for. `answersWith` hands that read the conversation as
   * it stood when it started, whatever the store holds by the time it answers — two reads of one view
   * racing is what C-5 is about.
   */
  deferNextHistoryRead(answersWith?: ChatMessage[]): { resolve(): void }
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

const QUESTION: ChatMessage = { role: 'user', text: 'wo liegen die Dateien?' }
/** A user line the server named — the shape of R-WP-25: the author is a person, not an agent. */
const ANNAS_QUESTION: ChatMessage = { role: 'user', text: 'bitte X', origin: 'Anna' }
const ANSWER: ChatMessage = { role: 'jon', text: 'Im Root liegen: pom.xml und README.md' }
/** A team member's final answer — role `jon` with an author, never a third role (UC-WP-39). */
const TEAM_ANSWER: ChatMessage = { role: 'jon', text: 'Eine Banane.', origin: 'Dev' }
const JONS_ANSWER: ChatMessage = { role: 'jon', text: 'Dev nennt eine Banane.', origin: 'Jon' }

/** The unified diff the wire carries for one written file (R-WP-36) — as the core's `SimpleDiff` writes it. */
const DIFF = '--- a/src/App.java\n+++ b/src/App.java\n@@ -1,2 +1,3 @@\n a\n+b\n \n'

/** One roster entry as the server sends it — the tile family of R-WP-7. */
function tile(name: string, state: AgentTileState): AgentTile {
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
async function flush(): Promise<void> {
  await act(async () => {
    await new Promise(resolve => setTimeout(resolve, 0))
  })
}

/**
 * `fetch` stand-in for the chat edges: the conversation edges (send, stop, events) and the tile family
 * (R-WP-7). The stream body is a `ReadableStream` the test writes into byte by byte, so a chunk may end
 * anywhere — including inside one event's JSON.
 */
function stubChat(
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

  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input)
      const method = (init?.method ?? 'GET').toUpperCase()
      calls.push({ method, path, body: init?.body as string | undefined })

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
        return json(200, state.agents)
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

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } })
}

/** One SSE event exactly as the server writes it — no space after the colon. */
function event(name: string, payload: unknown): string {
  return `event:${name}\ndata:${JSON.stringify(payload)}\n\n`
}

/** A keep-alive comment: the other thing a real wire puts between the events. */
const KEEP_ALIVE = ':ping\n\n'

beforeEach(() => {
  cleanup()
  active = undefined
})

afterEach(async () => {
  // a stream that never ends would keep the reader of the unmounted view pending forever; awaited,
  // because an unfinished act() of this test would overlap the act() of the next one
  await active?.closeStream()
  active = undefined
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

test('chatAsksForAProjectWhenNoneIsOpen', () => {
  render(<ChatPage project={null} />)

  expect(screen.getByText(content.chat.noProject)).toBeInTheDocument()
  expect(screen.queryByTestId('chat-input')).toBeNull()
})

// UC-WP-5
test('openProjectBindsWorkDir', async () => {
  const chat = stubChat({ messages: [QUESTION], projects: [project()] })
  renderApp()
  expect(await screen.findByLabelText(content.projects.gitUrl)).toBeInTheDocument()

  fireEvent.click(await screen.findByRole('button', { name: 'demo' }))

  // the address names the project now, so the chat appears with the read that resolves that address
  expect(await screen.findByTestId('chat-log')).toBeInTheDocument()
  await flush()
  expect(screen.getByText('wo liegen die Dateien?')).toBeInTheDocument()
  expect(screen.getByTestId('chat-input')).toHaveFocus()
  // the project list is gone — opening a project opens its chat, it does not only remember it
  expect(screen.queryByLabelText(content.projects.gitUrl)).toBeNull()
  const reads = chat.calls.filter(call => call.path.endsWith('/messages'))
  expect(reads.map(call => call.path)).toEqual(['/api/projects/demo-1a2b3c4/messages'])
})

test('theChatHeaderNamesTheProjectOnly', async () => {
  stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  const chat = await screen.findByRole('region', { name: content.chat.title })
  await flush()

  // the view IS the chat of a project, so its head names the project and nothing else — a second "Chat"
  // heading would only repeat the menu entry the user just clicked (R-WP-1)
  expect(within(chat).getByRole('heading', { name: 'demo' })).toBeInTheDocument()
  expect(within(chat).queryByRole('heading', { name: content.chat.title })).toBeNull()
  expect(within(chat).queryByText(/^Projekt:/)).toBeNull()
})

// UC-WP-6
test('chatAlwaysGoesToJon', async () => {
  const chat = stubChat({ messages: [] })
  const { container } = render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'fängst du mich auf?' } })
  fireEvent.click(screen.getByTestId('chat-send'))

  expect(chat.countOf('/messages', 'POST')).toBe(1)
  const post = chat.calls.find(call => call.method === 'POST')
  expect(post?.path).toBe('/api/projects/demo-1a2b3c4/messages')
  // one field only: nothing on this edge can name an addressee
  expect(JSON.parse(post?.body ?? '{}')).toEqual({ text: 'fängst du mich auf?' })
  expect(Object.keys(JSON.parse(post?.body ?? '{}'))).toEqual(['text'])
  // the own line is on the screen before the history carries it
  expect(screen.getByTestId('chat-line-user')).toHaveTextContent('fängst du mich auf?')
  // and nothing exists that could take the input away from Jon
  expect(screen.queryByTestId('agent-switcher')).toBeNull()
  expect(container.querySelector('select')).toBeNull()
  expect(screen.queryByRole('combobox')).toBeNull()
})

// UC-WP-56
test('enterSendsBothLinesInOneTurn', async () => {
  const chat = stubChat({ messages: [] })
  render(<ChatPage project={project()} />)
  await flush()

  // Enter is the send (R-WP-30), and what the field holds goes with it — both lines, in their order, one turn
  const field = screen.getByTestId('chat-input') as HTMLTextAreaElement
  fireEvent.change(field, { target: { value: 'erste Zeile' } })

  // the second line is set the way the user sets it, at the caret of the field, so the chain
  // Umbruch → two lines → one turn is walked and not modelled
  field.setSelectionRange(field.value.length, field.value.length)
  fireEvent.keyDown(field, { key: 'Enter', metaKey: true })
  expect(field, 'the line break came from the keyboard').toHaveValue('erste Zeile\n')
  fireEvent.change(field, { target: { value: `${field.value}zweite Zeile` } })

  fireEvent.keyDown(field, { key: 'Enter' })
  await flush()

  expect(sentTexts(chat)).toEqual(['erste Zeile\nzweite Zeile'])
  expect(field, 'the send took the text').toHaveValue('')

  // the same keystroke on an empty field opens nothing: the send needs a text (UC-WP-56 GIVEN 1)
  fireEvent.keyDown(field, { key: 'Enter' })
  await flush()
  expect(chat.countOf('/messages', 'POST')).toBe(1)
})

// UC-WP-75
test('anUrgentLineGoesToTheServerAsTyped', async () => {
  const chat = stubChat({ messages: [] })
  render(<ChatPage project={project()} />)
  await flush()

  // the `!` is the core's marker, not the view's (R-WP-42): one POST with the text as typed, one line with
  // it as typed — whether this input is a prio message is decided by the server and by nothing here
  const field = screen.getByTestId('chat-input') as HTMLTextAreaElement
  fireEvent.change(field, { target: { value: '! stop now' } })
  fireEvent.keyDown(field, { key: 'Enter' })
  await flush()

  expect(sentTexts(chat)).toEqual(['! stop now'])
  expect(userLineTexts()).toEqual(['! stop now'])
})


// R-WP-38
// UC-WP-70
test('aRunningTurnSurvivesTheUnitClick', async () => {
  // the file area is an address now, and an address change may not cost a running turn: a new mount would
  // read the history again and open the stream again, and the answer half way to the screen would be gone
  const chat = stubChat({ messages: [QUESTION], projects: [project()] })
  renderApp()
  fireEvent.click(await screen.findByRole('button', { name: 'demo' }))
  await screen.findByTestId('chat-log')
  await flush()

  await chat.push(event('partial', { kind: 'answer', text: 'Im Root liegen' }))
  expect(screen.getByTestId('chat-live')).toHaveTextContent('Im Root liegen')
  // the two wires of this project only — the list beside the chat has a work-state stream of its own, and
  // counting that one would answer a different question
  const readsBefore = chat.countOf(`/${project().id}/messages`, 'GET')
  const streamsBefore = chat.countOf(`/${project().id}/events`, 'GET')
  expect(streamsBefore, 'the open chat streams on exactly one wire').toBe(1)

  // the second handle of the same place: the tree names the file area while the answer is still arriving
  fireEvent.click(
    within(screen.getByRole('navigation', { name: content.nav.views })).getByRole('menuitem', {
      name: content.nav.files,
    }),
  )

  expect(await screen.findByTestId('files-panel')).toBeInTheDocument()
  // the chat beside it is the same instance — no read, no second wire, the live line still stands (G2)
  expect(screen.getByTestId('chat-live')).toHaveTextContent('Im Root liegen')
  expect(chat.countOf(`/${project().id}/messages`, 'GET'), 'the running chat is not read again').toBe(
    readsBefore,
  )
  expect(chat.countOf(`/${project().id}/events`, 'GET'), 'one live stream stays open').toBe(streamsBefore)

  // and the turn goes on: opening a folder is not the end of an answer
  await chat.push(event('partial', { kind: 'answer', text: ': pom.xml' }))
  expect(screen.getByTestId('chat-live')).toHaveTextContent('Im Root liegen: pom.xml')
})

// R-WP-38
// UC-WP-70
// UC-WP-77
test('aRunningTurnSurvivesTheChatUnitClick', async () => {
  // the way **back** from the file area, with a turn running: the tree's chat unit names the chat one address
  // up. An entry that answered with `/chat` instead of the project's own address would walk through the
  // last-project redirect, mount the chat again and cost the answer half way to the screen (UC-WP-77, G2).
  // INC-L1-BUG-2: started on the file area on purpose — started on the chat the click asks for the address the
  // view already has, so an entry wired to nothing would survive this test as neatly as the turn does.
  const chat = stubChat({ messages: [QUESTION], projects: [project()] })
  const app = renderApp(`/chat/${project().name}/files`)
  await screen.findByTestId('files-panel')
  await screen.findByTestId('chat-log')
  await flush()

  await chat.push(event('partial', { kind: 'answer', text: 'Im Root liegen' }))
  expect(screen.getByTestId('chat-live')).toHaveTextContent('Im Root liegen')
  const readsBefore = chat.countOf(`/${project().id}/messages`, 'GET')
  const streamsBefore = chat.countOf(`/${project().id}/events`, 'GET')
  expect(streamsBefore, 'the open chat streams on exactly one wire').toBe(1)

  fireEvent.click(
    within(screen.getByRole('navigation', { name: content.nav.views })).getByRole('menuitem', {
      name: content.nav.chat,
    }),
  )

  // the click moved one address up: the area is gone, which is what makes „no new mount" a claim about a
  // changed address and not about a click that did nothing
  await waitFor(() => expect(app.path()).toBe(`/chat/${project().name}`))
  expect(screen.queryByTestId('files-panel'), 'the chat unit leaves the file area').toBeNull()

  // no third address and no new mount: the same chat instance keeps the live line and both wires (G2)
  expect(screen.getByTestId('chat-live'), 'the live line stands through the click').toHaveTextContent(
    'Im Root liegen',
  )
  expect(chat.countOf(`/${project().id}/messages`, 'GET'), 'the running chat is not read again').toBe(
    readsBefore,
  )
  expect(chat.countOf(`/${project().id}/events`, 'GET'), 'one live stream stays open').toBe(streamsBefore)

  // and the turn goes on: naming the unit is not the end of an answer
  await chat.push(event('partial', { kind: 'answer', text: ': pom.xml' }))
  expect(screen.getByTestId('chat-live')).toHaveTextContent('Im Root liegen: pom.xml')
})

// UC-WP-7
test('streamingUpdatesChatWithoutReload', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()
  expect(chat.countOf('/events', 'GET')).toBe(1)
  const readsBefore = chat.countOf('/messages', 'GET')

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  expect(chat.countOf('/messages', 'POST')).toBe(1)

  // the answer arrives in halves, the border cut through the middle of one event's JSON
  await chat.push('event:partial\ndata:{"kind":"answer","te')
  await chat.push('xt":"Im Root liegen"}\n\n' + KEEP_ALIVE)
  expect(screen.getByTestId('chat-live')).toHaveTextContent('Im Root liegen')

  await chat.push(event('partial', { kind: 'answer', text: ': pom.xml und README.md' }))
  expect(screen.getByTestId('chat-live')).toHaveTextContent('Im Root liegen: pom.xml und README.md')
  // growing text without a reload: no second history read while the text grows
  expect(chat.countOf('/messages', 'GET')).toBe(readsBefore)

  await chat.push(event('tool', { text: 'Running: `ls` in /home/me/.aifactory/projects/demo' }))
  expect(screen.getByTestId('chat-tool-line')).toHaveTextContent('Running: `ls`')

  // thinking is streamed too, but it is not an answer: the chat shows the answer only
  await chat.push(event('partial', { kind: 'think', text: 'Ich überlege gerade…' }))
  expect(screen.getByTestId('chat-live')).toHaveTextContent('Im Root liegen: pom.xml und README.md')
  expect(screen.getByTestId('chat-log').textContent).not.toContain('Ich überlege gerade')

  // the finished text is no event: done means read the history once, and it replaces the live line
  chat.setMessages([QUESTION, { role: 'user', text: 'zähl die Dateien' }, ANSWER])
  await chat.push(event('done', { millis: 1234 }))

  expect(screen.getByTestId('chat-line-jon')).toHaveTextContent(ANSWER.text)
  expect(chat.countOf('/messages', 'GET')).toBe(readsBefore + 1)
  expect(screen.queryByTestId('chat-live')).toBeNull()
  // the trace of the turn is not lost by the read: it stays where it happened, while the streaming text
  // gives way to the history that carries it now (R-WP-28, UC-WP-54)
  expect(screen.getByTestId('chat-tool-line')).toHaveTextContent('Running: `ls`')
  // the own input lives in the history only now — the pending copy is gone, no line twice
  expect(screen.getAllByTestId('chat-line-user')).toHaveLength(2)
})

// UC-WP-64
test('failedTurnIsALineInTheHistory', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  const reason = 'Es fehlt ein Modellschlüssel für OPEN_AI'
  // the server writes the failure into the conversation before it reports it, so the read that answers the
  // event is what makes the line outlive a reload and every other browser (R-WP-34)
  chat.setMessages([QUESTION, { role: 'error', text: reason }])

  await chat.push(event('error', { message: reason }))
  await flush()

  // the line stands in the log — once, never twice: the live notice gives way to the history that carries it
  const failures = screen.getAllByTestId('chat-line-error')
  expect(failures).toHaveLength(1)
  expect(screen.getByTestId('chat-log')).toContainElement(failures[0])
  expect(failures[0]).toHaveTextContent(reason)
  // no notice beside the log: a failed turn is a line of the conversation, not a message about it
  expect(screen.queryByTestId('chat-error')).toBeNull()
  // no alarm dialog, no dead input: the next turn is one line away (Q1)
  expect(screen.getByTestId('chat-input')).toBeEnabled()
  expect(screen.getByTestId('chat-send')).toBeDisabled()
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'und jetzt mit Schlüssel' } })
  expect(screen.getByTestId('chat-send')).toBeEnabled()
})

// UC-WP-64
test('turnErrorFromTheHistoryIsRenderedWithoutAnAuthor', async () => {
  // the wire text of the server, marker and all it makes of it: the UI knows no wording of its own for a
  // failure and adds none (R-WP-27)
  const reason = 'turn needs a model — set model in the factory config'
  stubChat({ messages: [QUESTION, { role: 'error', text: reason }] })
  render(<ChatPage project={project()} />)
  await flush()

  const failure = screen.getByTestId('chat-line-error')
  expect(failure.className).toContain('error')
  expect(failure.textContent).toBe(reason)
  // nobody spoke this line, so no name stands above it (R-WP-4)
  expect(within(failure).queryByTestId('chat-line-author')).toBeNull()
})

test('droppedStreamReopensAndReadsHistory', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()
  expect(chat.countOf('/events', 'GET')).toBe(1)

  await chat.closeStream()

  // reconnected within the second, and the history read again — R-WP-11 loses live lines, no more
  await waitFor(() => expect(chat.countOf('/events', 'GET')).toBe(2), { timeout: 1500 })
  expect(chat.countOf('/messages', 'GET')).toBeGreaterThanOrEqual(2)
  expect(screen.getAllByTestId('chat-line-user')).toHaveLength(1)
  expect(screen.getByText('wo liegen die Dateien?')).toBeInTheDocument()
})

// UC-WP-54
test('theStreamOpensAfterTheFirstHistoryRead', async () => {
  const chat = stubChat({ messages: [QUESTION, ANSWER] })
  const read = chat.deferNextHistoryRead()
  render(<ChatPage project={project()} />)
  await flush()

  // the conversation is asked for and has not answered: no stream is open yet, because the anchor of the
  // first live line is a statement about the rows that stand, and an event in front of the read would be
  // drawn above the whole history that the read brings (D-E)
  expect(chat.countOf('/messages', 'GET')).toBe(1)
  expect(chat.countOf('/events', 'GET')).toBe(0)

  read.resolve()
  await flush()

  expect(chat.countOf('/events', 'GET')).toBe(1)
  expect(agentLinesSaying(ANSWER.text)).toHaveLength(1)
})

// UC-WP-7
test('aLaterTurnKeepsStreamingWhileAnEarlierReadInFlight', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  // turn one answers and its `done` reads the history — that read is held on the wire
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await chat.push(event('partial', { kind: 'answer', text: 'Im Root liegen ' }))
  chat.setMessages([QUESTION, { role: 'user', text: 'zähl die Dateien' }, ANSWER])
  const read = chat.deferNextHistoryRead()
  await chat.push(event('done', { millis: 7 }))

  // the queued input opens the next turn while that read is still out: its answer cannot be in what the
  // read brings, because the read started before a single chunk of it arrived (R-WP-5, UC-WP-7)
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'und die Tests?' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await chat.push(event('partial', { kind: 'answer', text: 'Vierhundert ' }))
  expect(
    [...screen.getByTestId('chat-log').children].map(line => line.getAttribute('data-testid')),
    'both answers are on the screen while the first one has no history line yet',
  ).toEqual(['chat-line-user', 'chat-line-user', 'chat-live', 'chat-line-user', 'chat-live'])

  read.resolve()
  await flush()

  // the read gives up only what it could know: turn one's answer became a line of the conversation and is
  // not shown twice, while the answer that is still streaming keeps its head — the text of a turn the read
  // never saw is not something the read may delete (UC-WP-7). What it does give up is the second provisional
  // line: the read carries a user line of this browser's sender class and that class confirms every
  // provisional line sent before it — a nameless browser holds no sender to tell "carried one" from
  // "carried both", and the bundle of the core looks exactly like this (R-WP-37, §10.8.3: heilt mit dem
  // nächsten Read). The queued text is not lost, it only reads from the history now
  const streaming = screen.getAllByTestId('chat-live')
  expect(streaming).toHaveLength(1)
  expect(streaming[0]).toHaveTextContent('Vierhundert')
  expect(agentLinesSaying(ANSWER.text)).toHaveLength(1)
  const after = [...screen.getByTestId('chat-log').children].map(line =>
    line.getAttribute('data-testid'),
  )
  expect(after).toEqual(['chat-line-user', 'chat-line-user', 'chat-line-jon', 'chat-live'])

  // the positive replacement: the next read brings the drained input with the core's marker — it stands
  // exactly once in the log, no provisional line beside it, the streaming text given way to its answer
  chat.setMessages([
    QUESTION,
    { role: 'user', text: 'zähl die Dateien' },
    ANSWER,
    { role: 'user', text: '[Queued Message] (14:33): und die Tests?' },
    { role: 'jon', text: 'Vierhundert Tests.' },
  ])
  await chat.push(event('done', { millis: 15 }))
  expect(userLineTexts()).toEqual([
    QUESTION.text,
    'zähl die Dateien',
    '[Queued Message] (14:33): und die Tests?',
  ])
  expect(screen.queryByTestId('chat-live')).toBeNull()
})

// R-WP-11
test('anOlderHistoryReadNeverOverwritesANewerOne', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  // two turns end one after the other, and each reads the history. A read is a round trip over the whole
  // memory, so the first one can answer last — and it answers with the conversation as it stood when it
  // started, which is the state before the newer read's turn
  const older = chat.deferNextHistoryRead([QUESTION])
  await chat.push(event('done', { millis: 7 }))
  chat.setMessages([QUESTION, ANSWER])
  const newer = chat.deferNextHistoryRead([QUESTION, ANSWER])
  await chat.push(event('done', { millis: 9 }))

  newer.resolve()
  await flush()
  expect(agentLinesSaying(ANSWER.text)).toHaveLength(1)

  // the late answer of the older read is the older content: applying it would move the log back and lose the
  // answer that is already on the screen (R-WP-11)
  older.resolve()
  await flush()
  expect(agentLinesSaying(ANSWER.text)).toHaveLength(1)
  expect([...screen.getByTestId('chat-log').children].map(line => line.getAttribute('data-testid'))).toEqual(
    ['chat-line-user', 'chat-line-jon'],
  )
  // dropped is the answer, not the reading: both reads were asked for
  expect(chat.countOf('/messages', 'GET')).toBe(3)
})

// UC-WP-16
test('oneChatPerProject', async () => {
  const chat = stubChat({
    messages: [QUESTION, ANSWER],
    projects: [project()],
    // Plan carries TRANSCRIPT, so this guard also covers the third affordance Inc 5c adds to a member's
    // tile — what a tile offers is the server's set, never a name (D-14)
    agents: [
      tile('Jon', 'IDLE'),
      { ...tile('Plan', 'IDLE'), capabilities: ['CONTEXT', 'CLEAR', 'COMPACT', 'TRANSCRIPT'] },
    ],
  })
  renderApp()
  expect(await screen.findByLabelText(content.projects.gitUrl)).toBeInTheDocument()

  fireEvent.click(await screen.findByRole('button', { name: 'demo' }))
  const opened = await screen.findByRole('region', { name: content.chat.title })
  await waitFor(() => expect(agentLinesSaying(ANSWER.text)).toHaveLength(1))

  // the controls the chat has: one writes into THIS conversation, one sends the last accepted text again
  // (R-WP-30), one ends its running turn (UC-WP-50) — none names an addressee and none starts a conversation.
  // The tile actions of Inc 5b sit beside them and are asserted separately below (R-WP-7)
  const controls = within(opened)
    .queryAllByRole('button')
    .filter(control => control.closest('.chat-tiles') === null)
    .map(control => control.textContent?.trim() ?? '')
  expect(controls).toEqual([content.chat.send, content.chat.resend, content.chat.stop])
  expect(
    controls.filter(label =>
      /neue|gespräch|dialog|verlauf|wechseln|öffnen|lösch|new|conversation|history|switch|open|delete|remove|clear/i.test(
        label,
      ),
    ),
  ).toEqual([])
  // the positive replacement: what the tiles add is exactly the two actions and, for the one tile that
  // offers it, the read-only Blick — and nothing that selects a conversation: the roster is the only
  // thing a tile can address (UC-WP-49 adds a read, not a second conversation). R-WP-32 moved that Blick
  // onto the tile itself, so the door is the tile and no longer a button beside it.
  const tileActions = [...opened.querySelectorAll('.chat-tile-actions button')]
    .map(control => control.textContent?.trim() ?? '')
    .filter(label => label !== '')
  expect([...new Set(tileActions)].sort()).toEqual([content.tiles.clear, content.tiles.compact])
  const tileDoors = [...opened.querySelectorAll('.chat-tile-face[role="button"]')].map(
    door => door.getAttribute('aria-label') ?? '',
  )
  expect(tileDoors, 'only the tile with TRANSCRIPT opens a conversation').toEqual([
    content.tiles.open('Plan'),
  ])

  // A1 (R-WP-39): the frame's entry is the basis' menu item now — the click and its claim are unchanged
  fireEvent.click(
    within(screen.getByRole('navigation', { name: content.nav.views })).getByRole('menuitem', {
      name: content.nav.projects,
    }),
  )
  expect(screen.queryByTestId('chat-log')).toBeNull()

  // INC-5A-D1 (R-WP-38): the frame owns that read now — the tree beside the list is drawn from it — so the
  // project still has to be found again before it can be opened. The click and its claim are unchanged.
  fireEvent.click(await screen.findByRole('button', { name: 'demo' }))
  const again = await screen.findByRole('region', { name: content.chat.title })
  await waitFor(() => expect(agentLinesSaying(ANSWER.text)).toHaveLength(1))

  // the same conversation, not a second one: one log, both lines once, one single read path
  expect(screen.getAllByTestId('chat-log')).toHaveLength(1)
  expect(within(again).getAllByTestId('chat-line-user')).toHaveLength(1)
  expect(within(again).getAllByTestId('chat-line-jon')).toHaveLength(1)
  expect(within(again).getByText(QUESTION.text)).toBeInTheDocument()
  const reads = chat.calls
    .filter(call => call.method === 'GET' && call.path.endsWith('/messages'))
    .map(call => call.path)
  expect(reads.length).toBeGreaterThanOrEqual(2)
  expect([...new Set(reads)]).toEqual(['/api/projects/demo-1a2b3c4/messages'])
})

// UC-WP-39
test('everyLineCarriesItsAuthor', async () => {
  stubChat({ messages: [QUESTION, TEAM_ANSWER, JONS_ANSWER] })
  render(<ChatPage project={project()} />)
  await flush()

  // exactly two lines have an agent relationship, and exactly those two carry an agent name (R-WP-4)
  expect(agentAuthorsOnScreen()).toEqual(['Dev', 'Jon'])

  // the own input has no agent relationship, so it names no agent — what it names is the role label the
  // doc gives a nameless user line (R-WP-25); the named case is UC-WP-48 below
  expect(userAuthorsOnScreen()).toEqual([content.chat.you])
  expect(screen.queryByText(/System|Peon|Unbekannt/i)).toBeNull()
})

// UC-WP-48
test('theSharedChatShowsWhoWroteIt', async () => {
  stubChat({ messages: [ANNAS_QUESTION, TEAM_ANSWER, JONS_ANSWER] })
  render(<ChatPage project={project()} />)
  await flush()

  // the server names the person, so a viewer who never typed anything reads the same author (R-WP-25)
  expect(userAuthorsOnScreen()).toEqual(['Anna'])
  expect(within(screen.getByTestId('chat-line-user')).getByText('Anna')).toBeInTheDocument()
  expect(within(screen.getByTestId('chat-line-user')).getByText(ANNAS_QUESTION.text)).toBeInTheDocument()
  // and the person never becomes an agent of the conversation
  expect(agentAuthorsOnScreen()).toEqual(['Dev', 'Jon'])
})

// UC-WP-48
test('withoutANameTheLineSaysYOU', async () => {
  const chat = stubChat({ messages: [QUESTION, ANSWER] })
  render(<ChatPage project={project()} />)
  await flush()

  // GIVEN no name set THEN the line says YOU — the doc's role label, not a guessed author
  expect(userAuthorsOnScreen()).toEqual([content.chat.you])
  // a line the server left without an agent stays nameless (R-WP-4)
  const jonsLine = screen.getByTestId('chat-line-jon')
  expect(within(jonsLine).queryAllByTestId('chat-line-author')).toHaveLength(0)
  expect(agentAuthorsOnScreen()).toEqual([])

  // the own, still unconfirmed line carries the same label, so it does not jump when the history arrives
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'und ohne Namen' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  expect(chat.countOf('/messages', 'POST')).toBe(1)
  expect(userAuthorsOnScreen()).toEqual([content.chat.you, content.chat.you])
})

// UC-WP-48
test('theSendCarriesTheName', async () => {
  const chat = stubChat({ messages: [] })
  render(<ChatPage project={project()} user=" Anna " />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'bitte X' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()

  // the name travels as its own field, trimmed: the server owns the prefix of the memory (R-WP-25), and
  // a field that is only spaces is no name — the browser sends exactly what a nameless one sends
  const post = chat.calls.find(call => call.method === 'POST')
  expect(JSON.parse(post?.body ?? '{}')).toEqual({ text: 'bitte X', user: 'Anna' })
  expect(Object.keys(JSON.parse(post?.body ?? '{}'))).toEqual(['text', 'user'])
  // the provisional line is stamped with the same trimmed name the server hands back as `origin` —
  // stamped untrimmed, the history would never confirm this line (D-H, R-WP-37)
  expect(userAuthorsOnScreen()).toEqual(['Anna'])
})

// UC-WP-48
test('theOwnLineShowsTheName', async () => {
  stubChat({ messages: [] })
  render(<ChatPage project={project()} user="Anna" />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'bitte X' } })
  fireEvent.click(screen.getByTestId('chat-send'))

  // before the history confirms the line, it already carries his name — otherwise it jumps on the read
  expect(userAuthorsOnScreen()).toEqual(['Anna'])
})

// UC-WP-40
test('onlyFinalAnswersFromTheTeam', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  // GIVEN 2 — a member's thinking line is not an answer and never reaches the screen
  await chat.push(event('partial', { kind: 'think', text: 'DENKTEXT-AUS-MITGLIED' }))
  expect(screen.getByTestId('chat-log').textContent).not.toContain('DENKTEXT-AUS-MITGLIED')

  // GIVEN 1 — and a member's final answer is no live event either: while the turn runs, Jon's own trace
  // is the only named thing on the wire, and no line of the conversation carries a member's text
  await chat.push(event('tool', { text: 'a tool line of the running turn' }))
  expect(screen.queryByTestId('chat-line-jon')).toBeNull()
  expect(screen.getByTestId('chat-tool-line')).toHaveTextContent('a tool line of the running turn')

  // with `done` the history is read once, and it carries the member's line under its own name — once
  chat.setMessages([QUESTION, TEAM_ANSWER, JONS_ANSWER])
  await chat.push(event('done', { millis: 4321 }))

  expect(agentAuthorsOnScreen()).toEqual(['Dev', 'Jon'])
  expect(screen.getAllByTestId('chat-line-jon')).toHaveLength(2)
  expect(screen.getAllByText('Eine Banane.')).toHaveLength(1)
  expect(screen.getByTestId('chat-log').textContent).not.toContain('DENKTEXT-AUS-MITGLIED')
})

test('reconnectReadsTeamAnswerFromTheHistory', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  // the turn dies mid-flight: what is lost is the live line and nothing else — the client keeps no
  // memory of a team answer, because the history is the only truth for finished text (R-WP-11)
  await chat.push(event('partial', { kind: 'answer', text: 'HALB ' }))
  expect(screen.getByTestId('chat-live')).toHaveTextContent('HALB')

  // (re-)connect reads the history: the member's line arrives with it, named, and exactly once
  chat.setMessages([QUESTION, TEAM_ANSWER, JONS_ANSWER])
  await chat.closeStream()
  await waitFor(() => expect(chat.countOf('/events', 'GET')).toBe(2), { timeout: 1500 })

  expect(screen.getAllByText('Eine Banane.')).toHaveLength(1)
  expect(agentAuthorsOnScreen()).toEqual(['Dev', 'Jon'])
  expect(screen.queryByTestId('chat-live')).toBeNull()
})

// UC-WP-11
test('tileStateFollowsTheStream', async () => {
  const chat = stubChat({ messages: [QUESTION], agents: [tile('Jon', 'IDLE'), tile('Dev', 'IDLE')] })
  render(<ChatPage project={project()} />)
  await flush()

  expect(screen.getByTestId('tile-state-Jon')).toHaveTextContent(content.tiles.state.IDLE)
  const historyReads = chat.countOf('/messages', 'GET')
  const rosterReads = chat.countOf('/agents', 'GET')

  // the snapshot arrives on the wire and replaces the list — no second read of anything (R-WP-7)
  await chat.push(event('agents', { tiles: [tile('Jon', 'WORKING'), tile('Dev', 'COMPACTING')] }))

  expect(screen.getByTestId('tile-state-Jon')).toHaveTextContent(content.tiles.state.WORKING)
  expect(screen.getByTestId('tile-state-Dev')).toHaveTextContent(content.tiles.state.COMPACTING)
  expect(chat.countOf('/messages', 'GET')).toBe(historyReads)
  expect(chat.countOf('/agents', 'GET')).toBe(rosterReads)
  // and a tile that is not idle offers nothing any more (UC-WP-12's guard, seen from the screen)
  expect(screen.getByTestId('tile-clear-Jon')).toBeDisabled()
})

// UC-WP-12
test('clearOnATileNamesThatTileAndARefusalIsALine', async () => {
  const chat = stubChat({ messages: [QUESTION], agents: [tile('Jon', 'IDLE'), tile('Dev', 'IDLE')] })
  chat.refuseActions(409, 'a turn or another action is still running')
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.click(screen.getByTestId('tile-clear-Dev'))
  await waitFor(() => expect(screen.getByTestId('chat-error')).toHaveTextContent(/still running/))

  // one POST, and it addresses exactly the tile that was clicked — name and action, nothing else
  expect(chat.countOf('/agents', 'POST')).toBe(1)
  const post = chat.calls.find(call => call.method === 'POST')
  expect(post?.path).toBe('/api/projects/demo-1a2b3c4/agents')
  expect(JSON.parse(post?.body ?? '{}')).toEqual({ name: 'Dev', action: 'clear' })
  // the refusal changed nothing: no re-read of the chat and no new roster
  expect(chat.countOf('/messages', 'GET')).toBe(1)
  expect(chat.countOf('/agents', 'GET')).toBe(1)
  expect(screen.getByTestId('chat-log')).toBeInTheDocument()
})

// UC-WP-50
test('stopKeepsTheShownLines', async () => {
  const chat = stubChat({ messages: [QUESTION], agents: [tile('Jon', 'IDLE')] })
  render(<ChatPage project={project()} />)
  await flush()
  // nothing runs, so nothing can be stopped
  expect(screen.getByTestId('chat-stop')).toBeDisabled()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'schlaf drei sekunden' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()
  const historyReads = chat.countOf('/messages', 'GET')

  await chat.push(event('partial', { kind: 'answer', text: 'Ich schlafe gerade' }))
  expect(screen.getByTestId('chat-live')).toHaveTextContent('Ich schlafe gerade')
  expect(screen.getByTestId('chat-stop')).toBeEnabled()

  fireEvent.click(screen.getByTestId('chat-stop'))
  await flush()

  // exactly one call, on the stop edge, and it starts nothing: no second send, no history read
  const stops = chat.calls.filter(call => call.path.endsWith('/messages/stop'))
  expect(stops.map(call => call.method)).toEqual(['POST'])
  expect(stops[0]?.path).toBe('/api/projects/demo-1a2b3c4/messages/stop')
  expect(chat.countOf('/messages', 'POST')).toBe(1)
  expect(chat.countOf('/messages', 'GET')).toBe(historyReads)

  // the turn ends: what he was shown stays on the screen, because the history kept less than that
  chat.setMessages([QUESTION])
  await chat.push(event('done', { millis: 5 }))

  expect(screen.getByTestId('chat-live')).toHaveTextContent('Ich schlafe gerade')
  expect(screen.getByText('schlaf drei sekunden')).toBeInTheDocument()
  expect(chat.countOf('/messages', 'GET')).toBe(historyReads)
  expect(screen.getByTestId('chat-stop')).toBeDisabled()
})

// UC-WP-50
test('stopOnAFinishedTurnIsSilent', async () => {
  // a roster that says WORKING is enough to offer the stop — another user's turn is stoppable too (UC-WP-22)
  const chat = stubChat({ messages: [QUESTION], agents: [tile('Jon', 'WORKING')] })
  chat.setStopAnswer(409, 'no turn is running')
  render(<ChatPage project={project()} />)
  await flush()
  expect(screen.getByTestId('chat-stop')).toBeEnabled()
  const rosterReads = chat.countOf('/agents', 'GET')

  fireEvent.click(screen.getByTestId('chat-stop'))
  await waitFor(() => expect(chat.countOf('/agents', 'GET')).toBe(rosterReads + 1))

  // 409 says the turn was already over — that is what the click wanted, so it is not an error line
  expect(screen.queryByTestId('chat-error')).toBeNull()
  expect(chat.countOf('/messages', 'POST')).toBe(0)
  expect(chat.countOf('/messages', 'GET')).toBe(1)
})

// UC-WP-50
test('aCompactingTileOffersNoStop', async () => {
  // a half Compact is not something a turn's stop may cut — so it is not a turn to stop (D-17)
  stubChat({ messages: [QUESTION], agents: [tile('Jon', 'COMPACTING')] })
  render(<ChatPage project={project()} />)
  await flush()

  expect(screen.getByTestId('tile-state-Jon')).toHaveTextContent(content.tiles.state.COMPACTING)
  expect(screen.getByTestId('chat-stop')).toBeDisabled()
})

// UC-WP-55
test('userLineCarriesTheBlueAccentOnTheLeft', async () => {
  stubChat({ messages: [QUESTION, ANSWER] })
  render(<ChatPage project={project()} />)
  await flush()

  const line = screen.getByTestId('chat-line-user')
  expect(line.className).toContain('user')
  expect(line.className).not.toContain('agent')

  // what colour a line has is no fact of the DOM: the sheet paints it. The class the row carries has to be
  // the class the sheet paints, and the token it paints has to be the blue of chat.html (R-WP-29). The doc
  // line that asked for the right edge was the source of F2 — the two assertions that pinned it now pin
  // its opposite, so the old contract is inverted here and not dropped.
  const painted = declarations('.chat-log .chat-line.user')
  expect(painted, 'the accent of the person is the user token').toContain('var(--user)')
  expect(painted, 'the accent sits on the left, like the agent accent').toContain('border-left')
  expect(painted, 'no second accent on the right edge').not.toContain('border-right')
  expect(painted, 'the line reads in the flow, not against it').not.toContain('text-align: right')
  expect(declarations('.chat-log .chat-line'), 'and the width of the accent stays with the line rule')
    .toContain('border-left: 3px solid transparent')
  expect(tokenValues('--user')[0], 'the blue of chat.html, not a lookalike').toBe('#0969da')
})

// UC-WP-55
test('agentLineIsGreenAndNamedByItsAgent', async () => {
  stubChat({ messages: [QUESTION, TEAM_ANSWER, JONS_ANSWER] })
  render(<ChatPage project={project()} />)
  await flush()

  const lines = screen.getAllByTestId('chat-line-jon')
  expect(lines).toHaveLength(2)
  for (const line of lines) {
    expect(line.className).toContain('agent')
    expect(line.className).not.toContain('user')
    // the label of an agent line is the agent that spoke, and it is the label the sheet colours (R-WP-4)
    expect(line.querySelector('.chat-role')).not.toBeNull()
  }
  expect(lines.map(line => within(line).getByTestId('chat-line-author').textContent)).toEqual(['Dev', 'Jon'])

  expect(declarations('.chat-log .chat-line.agent'), 'the accent of an answer is the agent token')
    .toContain('var(--agent)')
  expect(declarations('.chat-log .chat-role'), 'the name above the line is set in caps like chat.html')
    .toContain('text-transform: uppercase')
  expect(declarations('.chat-log .chat-line.user .chat-role'), 'and the name of a person in their blue')
    .toContain('var(--user)')
  expect(tokenValues('--agent')[0], 'the green of chat.html, not a lookalike').toBe('#1a7f37')
})

// UC-WP-55
// R-WP-29
test('roleChangeDrawsNoRule', async () => {
  stubChat({ messages: [QUESTION, TEAM_ANSWER, JONS_ANSWER, { role: 'user', text: 'und im Root?' }] })
  render(<ChatPage project={project()} />)
  await flush()

  // user, agent, agent, user: two changes of voice and no line for either — what separates them is the
  // accent each row carries on its own side (R-WP-29, UC-WP-55 GIVEN 2)
  expect(separatorsOf(screen.getByTestId('chat-log')), 'the whole log draws no separator').toEqual([])
  expect(
    screen.getAllByTestId(/chat-line-(user|jon)/).map(line => (line.classList.contains('user') ? 'user' : 'agent')),
  ).toEqual(['user', 'agent', 'agent', 'user'])
})

// UC-WP-55
test('agentLineHasNoGrayBlock', async () => {
  stubChat({ messages: [QUESTION, ANSWER] })
  render(<ChatPage project={project()} />)
  await flush()

  expect(screen.getByTestId('chat-line-jon').className).toContain('agent')

  // an answer is a line in the flow: no surface of its own, no card, no code-background block (R-WP-29)
  for (const selector of [
    '.chat-log .chat-line',
    '.chat-log .chat-line.user',
    '.chat-log .chat-line.agent',
    '.chat-log .chat-line.tool',
    '.chat-log .chat-line.pending',
  ]) {
    expect(declarations(selector), `${selector} paints no surface`).not.toMatch(/background|--code-bg/)
  }
  expect(declares('.chat-line.jon'), 'the gray block of the jon line is gone').toBe(false)
  // and the same rule keeps the chat in the flow: the log is no centred column, a line is no centred card
  // (R-WP-29, UC-WP-55 — Paul: nothing centred, nothing framed)
  expect(declarations('.chat-log'), 'the log centres nothing').not.toMatch(
    /justify-content: center|place-items: center/,
  )
  expect(declarations('.chat-log .chat-line'), 'a line centres nothing of itself').not.toMatch(
    /text-align: center|margin: 0 auto/,
  )
})

// UC-WP-54
test('toolLinesStayInlineInOrder', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  // the answer is interrupted twice: what the screen shows is the order it arrived in, not one text
  // plus a list of everything the turn reported (R-WP-28)
  await chat.push(event('partial', { kind: 'answer', text: 'Der Anfang. ' }))
  await chat.push(event('tool', { text: 'step 1' }))
  await chat.push(event('partial', { kind: 'answer', text: 'Der Schluss.' }))
  await chat.push(event('tool', { text: 'step 2' }))

  const shape = screen.getAllByTestId(/chat-(live|tool-line)/).map(entry =>
    entry.getAttribute('data-testid') === 'chat-live'
      ? `text ${entry.textContent?.trim() ?? ''}`
      : `tool ${[...entry.querySelectorAll('.chat-tool-line')].map(line => line.textContent).join('|')}`,
  )
  expect(shape).toEqual(['text Der Anfang.', 'tool step 1', 'text Der Schluss.', 'tool step 2'])
})

// UC-WP-54
test('toolLinesSurviveTheDoneRefresh', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await chat.push(event('tool', { text: 'ls in the checkout' }))
  expect(screen.getByTestId('chat-tool-line')).toHaveTextContent('ls in the checkout')

  // the turn ends and the history is read: the trace exists on the wire only, so this read is the moment
  // it would be lost — it is not (UC-WP-54)
  chat.setMessages([QUESTION, { role: 'user', text: 'zähl die Dateien' }, ANSWER])
  await chat.push(event('done', { millis: 7 }))

  const entries = [...screen.getByTestId('chat-log').children]
  const tool = screen.getByTestId('chat-tool-line')
  expect(tool).toHaveTextContent('ls in the checkout')
  // and it stands where it happened: after the input it belongs to, before the answer that followed it
  const answerAt = entries.findIndex(entry => entry.getAttribute('data-testid') === 'chat-line-jon')
  expect(entries.indexOf(tool)).toBeGreaterThanOrEqual(0)
  expect(answerAt).toBeGreaterThan(entries.indexOf(tool))
  // the streaming text gave way to the history and is not shown twice
  expect(screen.queryByTestId('chat-live')).toBeNull()
  expect(agentLinesSaying(ANSWER.text)).toHaveLength(1)
})

// UC-WP-54
test('aLiveAnswerKeepsItsAnchorWhenAnInputIsQueuedMidTurn', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await chat.push(event('partial', { kind: 'answer', text: 'Im Root liegen ' }))

  // he sends while the turn works, so the input queues (R-WP-10). The answer that is already streaming
  // belongs to the turn above it: it neither breaks into a second block nor slides below this input
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'und die Tests?' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await chat.push(event('partial', { kind: 'answer', text: 'pom.xml und README.md' }))

  const answers = screen.getAllByTestId('chat-live')
  expect(answers).toHaveLength(1)
  expect(answers[0]).toHaveTextContent('Im Root liegen pom.xml und README.md')
  const shape = [...screen.getByTestId('chat-log').children].map(line =>
    line.getAttribute('data-testid'),
  )
  expect(shape, 'the answer stands between its own input and the one queued behind it').toEqual([
    'chat-line-user',
    'chat-line-user',
    'chat-live',
    'chat-line-user',
  ])
})

// UC-WP-69
test('theQueuedInputStandsOnceInTheLog', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  // one turn works, so this input queues (R-WP-10)
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await chat.push(event('partial', { kind: 'answer', text: 'Im Root liegen ' }))
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'und die Tests?' } })
  fireEvent.click(screen.getByTestId('chat-send'))

  // the core writes a queued input into the memory with its marker — the history line says something else
  // than what he typed, and only the position recognises them as the same input (R-WP-37). The marker
  // stays visible: it is the reality of the history, the UI parses and shortens nothing (UC-WP-69)
  chat.setMessages([
    QUESTION,
    { role: 'user', text: 'zähl die Dateien' },
    { role: 'user', text: '[Queued Message] (14:32): und die Tests?' },
    ANSWER,
  ])
  await chat.push(event('done', { millis: 9 }))

  // his inputs stand exactly once in the log, in the order he sent them — the provisional line gave its
  // place to the history line that carries it now
  expect(userLineTexts()).toEqual([
    QUESTION.text,
    'zähl die Dateien',
    '[Queued Message] (14:32): und die Tests?',
  ])
  expect(agentLinesSaying(ANSWER.text)).toHaveLength(1)
  expect(screen.queryByTestId('chat-live')).toBeNull()
})

// UC-WP-57
test('theSameTextTwiceIsConfirmedTwice', async () => {
  const chat = stubChat({ messages: [] })
  render(<ChatPage project={project()} />)
  await flush()

  // the same text twice is two inputs (R-WP-37): one opens the turn, one queues behind it
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'nochmal' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await chat.push(event('partial', { kind: 'answer', text: 'Erledigt. ' }))
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'nochmal' } })
  fireEvent.click(screen.getByTestId('chat-send'))

  // the history carries both — the second one with the marker of the queued input
  chat.setMessages([
    { role: 'user', text: 'nochmal' },
    { role: 'user', text: '[Queued Message] (14:32): nochmal' },
    ANSWER,
  ])
  await chat.push(event('done', { millis: 8 }))

  // both provisional lines are gone: positions are counted, so the same text confirms twice and one
  // history line is never used for two of them (UC-WP-69)
  expect(userLineTexts()).toEqual(['nochmal', '[Queued Message] (14:32): nochmal'])
})

// UC-WP-69
test('aBundledQueuedLineReplacesBothPendingLines', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} user="Paul" />)
  await flush()

  // both inputs fall into the core's bundle window, which joins them into ONE memory line
  // (org.sterl.llmpeon.core/docs/queued-user-messages.md §1 "Join Short Burst Messages") — so the wire
  // carries one role: user for both of his inputs
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await chat.push(event('partial', { kind: 'answer', text: 'Im Root liegen ' }))
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'und die Tests?' } })
  fireEvent.click(screen.getByTestId('chat-send'))

  const bundled = '[Queued Message] (14:32): [User Paul]: zähl die Dateien\n[User Paul]: und die Tests?'
  chat.setMessages([QUESTION, { role: 'user', text: bundled, origin: 'Paul' }, ANSWER])
  await chat.push(event('done', { millis: 9 }))

  // one history line, two inputs: both provisional lines gave way to it, and the marker line stands
  // verbatim in the log — the UI parses nothing and shortens nothing (R-WP-37, Bündel-Fall)
  expect(userLineTexts()).toEqual([QUESTION.text, bundled])
  expect(screen.queryByTestId('chat-live')).toBeNull()
})

// UC-WP-69
test('aForeignUserLineConfirmsNoOwnPendingLine', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} user="Paul" />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'meine Frage' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await chat.push(event('partial', { kind: 'answer', text: 'Im Root liegen ' }))

  // the core drains Anna's queued input first (FIFO) — her line stands behind Paul's watermark, but it
  // is hers alone: his input still waits in the queue and keeps its provisional line (R-WP-37)
  chat.setMessages([QUESTION, { role: 'user', text: 'Annas Frage', origin: 'Anna' }, ANSWER])
  await chat.push(event('done', { millis: 7 }))
  expect(userLineTexts()).toEqual([QUESTION.text, 'Annas Frage', 'meine Frage'])

  // the next read brings HIS line with the marker: the provisional line is replaced by it, not doubled
  chat.setMessages([
    QUESTION,
    { role: 'user', text: 'Annas Frage', origin: 'Anna' },
    ANSWER,
    { role: 'user', text: '[Queued Message] (14:33): meine Frage', origin: 'Paul' },
  ])
  await chat.push(event('done', { millis: 11 }))
  expect(userLineTexts()).toEqual([
    QUESTION.text,
    'Annas Frage',
    '[Queued Message] (14:33): meine Frage',
  ])
})

test('consecutiveToolLinesAreMerged', async () => {
  const chat = stubChat({ messages: [] })
  render(<ChatPage project={project()} />)
  await flush()

  for (const step of Array.from({ length: 12 }, (_, index) => index + 1)) {
    await chat.push(event('tool', { text: `step ${step}` }))
  }

  // one box for a run of tool activity, and only its tail — what a turn reports is a trace beside the
  // answer and never a list that pushes the answer away (chat.html keeps ten lines)
  const boxes = screen.getAllByTestId('chat-tool-line')
  expect(boxes).toHaveLength(1)
  const lines = [...boxes[0].querySelectorAll('.chat-tool-line')].map(line => line.textContent)
  expect(lines).toHaveLength(10)
  expect(lines[0]).toBe('step 3')
  expect(lines[9]).toBe('step 12')
})

// UC-WP-72
test('aToolStepShowsTheNameTheCoreStamped', async () => {
  const chat = stubChat({ messages: [] })
  render(<ChatPage project={project()} />)
  await flush()

  // the stamp arrives on the raw wire and is read as it stands: the page lends no name to a step, and the
  // one the core left unstamped stays nameless (R-WP-37, R-WP-40, UC-WP-72 GIVEN 1)
  await chat.push(event('tool', { text: 'ls in the checkout', origin: 'Jon' }))
  await chat.push(event('tool', { text: 'file updated, diff skipped — binary content' }))

  const steps = [...screen.getByTestId('chat-tool-line').querySelectorAll('.chat-tool-line')]
  expect(steps).toHaveLength(2)
  expect(steps[0].querySelector('.chat-step-name')?.textContent).toBe('Jon')
  expect(steps[1].querySelector('.chat-step-name'), 'an unstamped step names nobody').toBeNull()
})

// UC-WP-72
test('theRunningStepEndsWithTheTurn', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await chat.push(event('tool', { text: 'ls in the checkout' }))
  await chat.push(event('tool', { text: 'wc -l pom.xml' }))

  const steps = [...screen.getByTestId('chat-tool-line').querySelectorAll('.chat-tool-line')]
  const working = [...screen.getByTestId('chat-tool-line').querySelectorAll('.chat-step-running')]
  expect(steps).toHaveLength(2)
  expect(working, 'one open turn has one working step').toHaveLength(1)
  expect(steps[1].contains(working[0]), 'and it is the step that came last').toBe(true)

  // the turn ends and the history is read: no step works any more, and the one that did keeps its place on
  // the screen with the clock of its arrival (R-WP-40, UC-WP-54, UC-WP-72 GIVEN 2)
  chat.setMessages([QUESTION, { role: 'user', text: 'zähl die Dateien' }, ANSWER])
  await chat.push(event('done', { millis: 12 }))

  expect(
    [...screen.getByTestId('chat-tool-line').querySelectorAll('.chat-step-running')],
    'with the turn the indicator ends',
  ).toEqual([])
  const finished = [...screen.getByTestId('chat-tool-line').querySelectorAll('.chat-tool-line')]
  expect(finished[1].getAttribute('title'), 'a finished step carries its arrival clock').toMatch(/^\d{2}:\d{2}$/)
})

/**
 * The indicator belongs to a step of the turn that is open and to no older one (R-WP-40, UC-WP-72 GIVEN 2):
 * the trace of a finished turn keeps its place on the screen (UC-WP-54) without working, and while the next
 * turn has reported no step of its own, no step works — `busy` is the waiting bar's statement (R-WP-31) and
 * never the statement of which step runs.
 */
// UC-WP-72
test('aSecondTurnDrawsNoIndicatorOnTheStepsOfTheFinishedOne', async () => {
  const steps = () =>
    screen.getAllByTestId('chat-tool-line').flatMap(box => [...box.querySelectorAll('.chat-tool-line')])
  const working = () => steps().filter(step => step.querySelector('.chat-step-running') !== null)

  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await chat.push(event('tool', { text: 'ls in the checkout' }))
  expect(working(), 'a step of the open turn works').toHaveLength(1)

  chat.setMessages([QUESTION, { role: 'user', text: 'zähl die Dateien' }, ANSWER])
  await chat.push(event('done', { millis: 12 }))
  await flush()

  // the turn is over and its trace stands: nothing of it works any more
  expect(screen.getByTestId('chat-tool-line')).toHaveTextContent('ls in the checkout')
  expect(working()).toEqual([])

  // the next input opens a turn that has reported nothing yet: the wait is on the screen, while the step of
  // the finished turn keeps its place without its indicator
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'und jetzt die README' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()
  expect(screen.getByTestId('chat-waiting'), 'a turn is open').toBeInTheDocument()
  expect(working(), 'the old step does not work for a turn it never belonged to').toEqual([])

  // the first step of the open turn is where the indicator goes — the rule is not simply switched off
  await chat.push(event('tool', { text: 'wc -l README.md' }))
  expect(working()).toHaveLength(1)
  expect(working()[0].textContent).toContain('wc -l README.md')
})

/**
 * The identity a step answers to is its turn, never the number of rows that turn anchored at (R-WP-40,
 * UC-WP-72 GIVEN 2). A Clear gives the log back exactly the row count the finished turn anchored at while
 * that turn's trace stays on the screen (UC-WP-54) — so both turns anchor at the same number, and only the
 * identity tells them apart.
 */
// UC-WP-72
test('twoTurnsCountingTheSameRowsAreNotTheSameTurn', async () => {
  const steps = () =>
    screen.getAllByTestId('chat-tool-line').flatMap(box => [...box.querySelectorAll('.chat-tool-line')])
  const working = () => steps().filter(step => step.querySelector('.chat-step-running') !== null)

  const chat = stubChat({ messages: [], agents: [tile('Jon', 'IDLE')] })
  render(<ChatPage project={project()} />)
  await flush()

  // the first turn anchors behind its own pending line, on a log of no row
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await chat.push(event('tool', { text: 'ls in the checkout' }))
  expect(working(), 'a step of the open turn works').toHaveLength(1)

  chat.setMessages([{ role: 'user', text: 'zähl die Dateien' }, ANSWER])
  await chat.push(event('done', { millis: 11 }))
  expect(userLineTexts(), 'the read of the finished turn has landed').toEqual(['zähl die Dateien'])
  expect(working(), 'the turn ended with its step').toEqual([])

  // the conversation is cleared: the log counts the rows of its log again from zero — the same number the
  // finished turn anchored at — while the trace of that turn stays
  chat.setMessages([])
  fireEvent.click(screen.getByTestId('tile-clear-Jon'))
  await waitFor(() => expect(userLineTexts()).toEqual([]))
  expect(screen.getByTestId('chat-tool-line')).toHaveTextContent('ls in the checkout')
  expect(working(), 'a cleared conversation works nothing').toEqual([])

  // the next input opens its turn at the same row number and answers with text before its first step
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'und jetzt die README' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await chat.push(event('partial', { kind: 'answer', text: 'Im Root liegen ' }))
  expect(
    working(),
    'the open turn takes no step of the finished one, though both anchor at the same number of rows',
  ).toEqual([])

  // the first step of the open turn is where the indicator goes — the rule is not simply switched off
  await chat.push(event('tool', { text: 'wc -l README.md' }))
  expect(working()).toHaveLength(1)
  expect(working()[0].textContent).toContain('wc -l README.md')
})

/**
 * A delegation is an entry of the log of its own (R-WP-40, UC-WP-72 GIVEN 3): the wire's pair opens it and
 * closes it, the word that the member works belongs to the open turn, and the entry survives the history
 * read that `done` triggers — it is a link of the timeline, not a second answer and not a line of the
 * history.
 */
// UC-WP-72
test('aDelegationStaysInTheLogAfterTheTurnEnds', async () => {
  const entry = () => screen.getByTestId('chat-delegation')
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'frag Dev nach einer Frucht' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  // an end that reaches this browser with no open entry of his: nothing to close, so nothing stands there
  // — an end never invents the entry it was meant to close (UC-WP-72 GIVEN 3)
  await chat.push(event('delegation', { member: 'Plan', working: false }))
  expect(screen.queryAllByTestId('chat-delegation'), 'an end that matches nothing draws no line').toEqual([])
  await chat.push(event('tool', { text: 'Dev start: nenne eine Frucht', origin: 'Jon' }))
  await chat.push(event('delegation', { member: 'Dev', working: true }))

  expect(entry().querySelector('.chat-delegation-name')?.textContent).toBe('Dev')
  expect(entry().textContent, 'the start of the delegation is what says he works').toContain(
    content.chat.working,
  )

  // the entry's own end, still inside the turn: the entry stands, the word is gone
  await chat.push(event('delegation', { member: 'Dev', working: false }))
  expect(entry().textContent, 'the end event ends the word').not.toContain(content.chat.working)

  // the turn ends and its history is read: the live entry stays where it happened, without its word
  chat.setMessages([QUESTION, { role: 'user', text: 'frag Dev nach einer Frucht' }, TEAM_ANSWER, JONS_ANSWER])
  await chat.push(event('done', { millis: 21 }))
  await flush()

  expect(entry().textContent, 'a read gives the delegation entry back like the trace of the turn').toContain(
    'Dev',
  )
  expect(entry().textContent).not.toContain(content.chat.working)
  expect(screen.getAllByTestId('chat-delegation'), 'one start stays one entry').toHaveLength(1)
})

/**
 * The second half of UC-WP-72 GIVEN 3: the entry stands <em>where the delegation happened</em>, so the
 * result reads under it and not at the end of the conversation (R-WP-40). Its place is the turn's frozen
 * anchor — neither an input queued while the member works may push it down, nor the history read that
 * `done` triggers may drop it behind the answer it delegated.
 */
// UC-WP-72
test('aDelegationStandsWhereItHappenedAndNotAtTheEndOfTheLog', async () => {
  const shape = () =>
    [...screen.getByTestId('chat-log').children].map(line => line.getAttribute('data-testid'))
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'frag Dev nach einer Frucht' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await chat.push(event('tool', { text: 'Dev start: nenne eine Frucht', origin: 'Jon' }))
  // he types on while the member works, so the input queues (R-WP-10) — the delegation happened before it
  // and stays above it, because the anchor is the turn's, not the moment's (D-A)
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'und was macht Plan?' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await chat.push(event('delegation', { member: 'Dev', working: true }))

  expect(shape(), 'below its own input, above the one queued after it, above every answer').toEqual([
    'chat-line-user',
    'chat-line-user',
    'chat-tool-line',
    'chat-delegation',
    'chat-line-user',
  ])

  // the turn ends and its history is read — the moment a live entry of the wrong anchor would fall behind
  // the last answer, since the read brings the two inputs and both answers
  chat.setMessages([
    QUESTION,
    { role: 'user', text: 'frag Dev nach einer Frucht' },
    { role: 'user', text: 'und was macht Plan?' },
    TEAM_ANSWER,
    JONS_ANSWER,
  ])
  await chat.push(event('done', { millis: 21 }))
  await flush()

  const entries = [...screen.getByTestId('chat-log').children]
  const at = entries.indexOf(screen.getByTestId('chat-delegation'))
  const ownInputAt = entries.findIndex(line =>
    (line.textContent ?? '').includes('frag Dev nach einer Frucht'),
  )
  const queuedAt = entries.findIndex(line => (line.textContent ?? '').includes('und was macht Plan?'))
  const answerAt = entries.findIndex(line => line.getAttribute('data-testid') === 'chat-line-jon')
  expect(at, 'the entry stays between both inputs').toBeGreaterThan(ownInputAt)
  expect(at, 'a read does not push it below the input queued after it').toBeLessThan(queuedAt)
  expect(at, 'and never behind the answer it delegated').toBeLessThan(answerAt)
})

/**
 * A delegation event is the pair or nothing, like a diff (R-WP-40, UC-WP-72 GIVEN 3): a name of blank text
 * names no member and opens nothing, and a payload without its flag says neither start nor end — so it may
 * not end the entry that stands either. The backend guards both; the client does not trust it.
 */
// UC-WP-72
test('aDelegationWithoutItsPairSaysNothing', async () => {
  const entry = () => screen.getByTestId('chat-delegation')
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'frag Dev nach einer Frucht' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await chat.push(event('delegation', { member: '   ', working: true }))
  expect(screen.queryAllByTestId('chat-delegation'), 'an entry naming nobody is no entry').toEqual([])

  await chat.push(event('delegation', { member: 'Dev', working: true }))
  expect(entry()).toHaveTextContent(content.chat.working)
  await chat.push(event('delegation', { member: 'Dev' }))
  expect(entry().textContent, 'a payload without its flag ends nothing').toContain(content.chat.working)
})



/**
 * The same rule, driven over the wire and not over the log's props: the indicator of a step ends with the
 * next step or with the end of the answer — never with the first word of that answer (R-WP-40,
 * UC-WP-72 GIVEN 2). A streaming text is not a step, so the step that worked last keeps working on the
 * screen until the wire reports another step or the turn ends, and the step then keeps its arrival clock.
 */
// UC-WP-72
test('theIndicatorOfAStepEndsWithTheNextStepOnTheWire', async () => {
  const steps = () =>
    screen.getAllByTestId('chat-tool-line').flatMap(box => [...box.querySelectorAll('.chat-tool-line')])
  const working = () => steps().filter(step => step.querySelector('.chat-step-running') !== null)

  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await chat.push(event('tool', { text: 'ls in the checkout' }))

  // the answer starts to stream over the same turn: a word is no step and no end, so the indicator stands
  await chat.push(event('partial', { kind: 'answer', text: 'Im Root liegen ' }))
  await chat.push(event('partial', { kind: 'answer', text: 'pom.xml und README.md.' }))
  expect(working(), 'streaming text does not end the indicator of a step').toHaveLength(1)
  expect(working()[0].textContent).toContain('ls in the checkout')

  // the next step takes the indicator, and only one step works
  await chat.push(event('tool', { text: 'wc -l pom.xml' }))
  expect(working()).toHaveLength(1)
  expect(working()[0].textContent).toContain('wc -l pom.xml')

  // the end of the answer ends it; both steps stay with their arrival clock and without a duration
  chat.setMessages([QUESTION, { role: 'user', text: 'zähl die Dateien' }, ANSWER])
  await chat.push(event('done', { millis: 9 }))
  expect(working(), 'with the end of the answer no step works').toEqual([])
  expect(
    steps().map(step => step.getAttribute('title')),
    'every finished step keeps its own arrival clock and no duration',
  ).toEqual([expect.stringMatching(/^\d{2}:\d{2}$/), expect.stringMatching(/^\d{2}:\d{2}$/)])
})


// UC-WP-68
test('aFileChangeIsLiveAndDoesNotOutliveTheReload', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  // the turn writes a file: the trace names the tool, the block names the file and shows what changed
  await chat.push(event('tool', { text: 'write src/App.java' }))
  await chat.push(event('diff', { file: 'src/App.java', diff: DIFF }))
  expect(screen.getByTestId('chat-diff-file')).toHaveTextContent('src/App.java')
  expect(screen.getByTestId('chat-diff')).toHaveTextContent('+b')
  // beside the trace and not inside it — a box is one place of the trace, a diff is a block
  expect(within(screen.getByTestId('chat-tool-line')).queryByTestId('chat-diff')).toBeNull()

  // the turn ends and the history is read: the block has no line in that history and keeps its place on
  // the screen, exactly like the trace of the same turn (R-WP-11, R-WP-36)
  chat.setMessages([QUESTION, ANSWER])
  await chat.push(event('done', { millis: 9 }))
  expect(screen.getByTestId('chat-diff')).toHaveTextContent('+b')
  expect(screen.getByTestId('chat-tool-line')).toHaveTextContent('write src/App.java')

  // a dropped connection is the reload's stand-in: what the wire will not send back leaves the screen,
  // and the history that comes back with the new connection carries no diff (R-WP-11)
  await chat.closeStream()
  await waitFor(() => expect(chat.countOf('/events', 'GET')).toBe(2), { timeout: 1500 })
  expect(screen.queryByTestId('chat-diff')).toBeNull()
  expect(screen.queryByTestId('chat-tool-line')).toBeNull()
  expect(agentLinesSaying(ANSWER.text)).toHaveLength(1)
})

// UC-WP-57
test('resendSendsTheLastMessageAgain', async () => {
  const chat = stubChat({ messages: [] })
  render(<ChatPage project={project()} />)
  await flush()
  // nothing has been sent yet, so nothing can be sent again
  expect(screen.getByTestId('chat-resend')).toBeDisabled()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()
  expect(chat.countOf('/messages', 'POST')).toBe(1)
  expect(screen.getByTestId('chat-resend')).toBeEnabled()

  fireEvent.click(screen.getByTestId('chat-resend'))
  await flush()

  // the same text, twice, on the same edge — and the field was not touched a second time (R-WP-30)
  expect(sentTexts(chat)).toEqual(['zähl die Dateien', 'zähl die Dateien'])
  expect(screen.getByTestId('chat-input')).toHaveValue('')
})

// UC-WP-57
test('aSendThatNeverLandedIsNotOfferedAgain', async () => {
  const chat = stubChat({ messages: [] })
  chat.refuseSend(503, 'the factory is not reachable')
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()

  expect(screen.getByTestId('chat-error')).toHaveTextContent('the factory is not reachable')
  // 202 is what makes a text "the last one": a text the queue never got is not something to send twice
  expect(screen.getByTestId('chat-resend')).toBeDisabled()
  expect(sentTexts(chat)).toEqual(['zähl die Dateien'])
})

// UC-WP-57
test('resendWorksAfterAnError', async () => {
  const chat = stubChat({ messages: [] })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()
  // the server already wrote the failure into the conversation when it reports it (R-WP-34)
  chat.setMessages([{ role: 'error', text: 'Es fehlt ein Modellschlüssel für OPEN_AI' }])
  await chat.push(event('error', { message: 'Es fehlt ein Modellschlüssel für OPEN_AI' }))
  expect(screen.getByTestId('chat-line-error')).toHaveTextContent('Es fehlt ein Modellschlüssel')

  // the answer to a failed turn needs no new typing, and what he writes in the meantime stays (R-WP-30)
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'und jetzt mit Schlüssel' } })
  fireEvent.click(screen.getByTestId('chat-resend'))
  await flush()

  expect(sentTexts(chat)).toEqual(['zähl die Dateien', 'zähl die Dateien'])
  expect(screen.getByTestId('chat-input')).toHaveValue('und jetzt mit Schlüssel')
})

// UC-WP-58
test('waitingBarShowsTheLivePreview', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()
  // no turn, no wait
  expect(screen.queryByTestId('chat-waiting')).toBeNull()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()

  // GIVEN a turn that has delivered nothing THEN the words of the plugin
  expect(screen.getByTestId('chat-waiting')).toHaveTextContent(content.chat.waitingForAi)

  // GIVEN the stream delivers THEN the bar shows what it last reported, on one line — a preview beside
  // the log and never a second copy of the answer (R-WP-31)
  await chat.push(event('tool', { text: 'Running: `ls` in /home/me' }))
  expect(screen.getByTestId('chat-waiting')).toHaveTextContent('Running: `ls`')

  await chat.push(event('partial', { kind: 'answer', text: 'Im Root liegen:\npom.xml' }))
  expect(screen.getByTestId('chat-waiting')).toHaveTextContent('pom.xml')
  expect(screen.getByTestId('chat-waiting').textContent).not.toContain('Im Root liegen')

  // and it is the plugin's line: mono, with a caret that blinks (R-WP-31). Where the bar stands is the
  // layout's call and not this rule's: the log scrolls in its own area, so the bar is a block directly
  // above the field and hangs on nothing any more (R-WP-35) — the `position: sticky` clause measured the
  // page scroll this increment took away, so it became its opposite.
  const painted = declarations('.chat-waiting')
  expect(painted, 'the wait keeps the plugin mono').toContain('font-family: var(--mono)')
  expect(painted, 'nothing sticks to a scroll the chat no longer has').not.toContain('position:')
  expect(declarations('.chat-waiting-caret')).toContain('animation')
  expect(declarations('.chat-log'), 'the log is the scroll area now').toContain('overflow-y: auto')
  expect(
    screen.getByTestId('chat-waiting').nextElementSibling?.className,
    'the wait sits directly above the field',
  ).toContain('chat-composer')
})

// UC-WP-58
test('waitingBarEndsWithTheTurn', async () => {
  const chat = stubChat({ messages: [] })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()
  await chat.push(event('partial', { kind: 'answer', text: 'Im Root liegen: pom.xml' }))
  expect(screen.getByTestId('chat-waiting')).toBeInTheDocument()

  chat.setMessages([ANSWER])
  await chat.push(event('done', { millis: 12 }))

  // the answer stays, the wait goes with the turn (R-WP-31)
  expect(screen.queryByTestId('chat-waiting')).toBeNull()
  expect(screen.getByTestId('chat-log')).toHaveTextContent('Im Root liegen: pom.xml')
})

// UC-WP-58
test('waitingBarEndsWithAFailedTurn', async () => {
  const chat = stubChat({ messages: [] })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()
  expect(screen.getByTestId('chat-waiting')).toBeInTheDocument()

  // a failed turn is an ended turn: no wait line left standing above the reason (UC-WP-58), and the reason
  // is a line of the conversation, not a notice beside it (R-WP-34)
  chat.setMessages([{ role: 'error', text: 'Es fehlt ein Modellschlüssel für OPEN_AI' }])
  await chat.push(event('error', { message: 'Es fehlt ein Modellschlüssel für OPEN_AI' }))

  expect(screen.getByTestId('chat-line-error')).toHaveTextContent('Es fehlt ein Modellschlüssel')
  expect(screen.queryByTestId('chat-error')).toBeNull()
  expect(screen.queryByTestId('chat-waiting')).toBeNull()
})

// UC-WP-65
test('pendingFrameExistsWhileTheTurnRuns', async () => {
  stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()
  // a line the history carries is a fact: before a turn starts, nothing is framed
  expect(framedLines()).toHaveLength(0)

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()

  // the positive half of the rule, so "never painted" can never pass for it: while this browser's turn is
  // open, its own input — the one the history does not carry yet — wears the waiting frame (R-WP-31)
  expect(framedLines()).toHaveLength(1)
  expect(framedLines()[0]).toHaveTextContent('zähl die Dateien')
})

// UC-WP-65
test('stopLeavesNoPendingFrame', async () => {
  const chat = stubChat({ messages: [QUESTION], agents: [tile('Jon', 'IDLE')] })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'schlaf drei sekunden' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()
  await chat.push(event('partial', { kind: 'answer', text: 'Ich schlafe gerade' }))
  expect(framedLines().length, 'the frame stands while the turn runs').toBeGreaterThan(0)

  fireEvent.click(screen.getByTestId('chat-stop'))
  await flush()

  // the accepted stop IS the end of the wait — the frame goes with the click, not with a later read, and
  // everything he was shown stays on the screen (UC-WP-50, D-16)
  expect(framedLines()).toHaveLength(0)
  expect(screen.getByTestId('chat-live')).toHaveTextContent('Ich schlafe gerade')
  expect(screen.getByTestId('chat-log')).toHaveTextContent('schlaf drei sekunden')

  chat.setMessages([QUESTION])
  await chat.push(event('done', { millis: 5 }))

  expect(framedLines()).toHaveLength(0)
  expect(screen.getByTestId('chat-live')).toHaveTextContent('Ich schlafe gerade')
})

// UC-WP-65
test('failedTurnLeavesNoPendingFrame', async () => {
  const chat = stubChat({ messages: [] })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'und die Ordner' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()
  expect(framedLines(), 'both of his own inputs wait on the turn').toHaveLength(2)

  // the turn fails with one input confirmed by the conversation and the other still waiting: the frame ends
  // with the turn either way, while the text of the waiting one stays where it was shown (R-WP-34, UC-WP-64)
  chat.setMessages([
    { role: 'user', text: 'zähl die Dateien', origin: 'Paul' },
    { role: 'error', text: 'Es fehlt ein Modellschlüssel für OPEN_AI' },
  ])
  await chat.push(event('error', { message: 'Es fehlt ein Modellschlüssel für OPEN_AI' }))

  expect(framedLines()).toHaveLength(0)
  expect(screen.getByTestId('chat-log')).toHaveTextContent('und die Ordner')
  expect(screen.getAllByTestId('chat-line-error')).toHaveLength(1)
})
// UC-WP-66
test('theLogIsTheScrollArea', () => {
  // jsdom lays out nothing, so "the chat scrolls in its own area" is read where it is written: the log owns
  // the scroll, the row owns the position, and the shell is a frame that never scrolls as a whole (R-WP-35)
  expect(declarations('.chat-log'), 'the log carries the scroll').toContain('overflow-y: auto')
  expect(declarations('.chat-log'), 'and it is the flexible part of the column').toContain('flex: 1')
  // without the zeroed floor a flex item keeps its content height and never scrolls — the line that looks
  // like a leftover is the one that makes the scroll work
  expect(declarations('.chat-log'), 'and may shrink below its own content').toContain('min-height: 0')
  expect(declarations('.chat-workspace'), 'the row is the box the file tab hangs on').toContain(
    'position: relative',
  )
  expect(declarations('.chat-workspace'), 'the row gives the chat its height, head to input').toContain(
    'align-items: stretch',
  )
  expect(declarations('.chat-workspace > .view'), 'the chat is a column').toContain(
    'flex-direction: column',
  )
  expect(declarations('.chat-workspace > .view'), 'and only its log scrolls').toContain(
    'overflow: hidden',
  )
  expect(declarations('.view'), 'every view scrolls for itself, so none of them is clipped').toContain(
    'overflow-y: auto',
  )
  expect(baseDeclarations('#root'), 'the shell is the fixed frame').toContain('height: 100dvh')
  expect(baseDeclarations('#root'), 'and it never scrolls as a whole').toContain('overflow: hidden')
})

/**
 * A scroll to measure: jsdom knows no layout, so the log gets its geometry from the test and keeps
 * `scrollTop` writable — the page's numbers stay what the test states (R-WP-35).
 */
function mockScrollOf(area: HTMLElement, scrollHeight: number, clientHeight: number): void {
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

// UC-WP-66
test('newLinesScrollToTheEnd', async () => {
  const chat = stubChat({ messages: [QUESTION, ANSWER] })
  render(<ChatPage project={project()} />)
  await flush()

  const log = screen.getByTestId('chat-log')
  mockScrollOf(log, 1_000, 300)
  expect(log.scrollTop, 'the test starts at the top of a long log').toBe(0)

  // GIVEN he reads the newest line WHEN the stream adds one THEN the log follows it to its end
  await chat.push(event('partial', { kind: 'answer', text: 'noch eine Zeile' }))
  expect(log.scrollTop, 'a log at its end keeps following the stream').toBe(1_000)
})

// UC-WP-66
test('readingUpIsNotRippedDown', async () => {
  const chat = stubChat({ messages: [QUESTION, ANSWER] })
  render(<ChatPage project={project()} />)
  await flush()

  const log = screen.getByTestId('chat-log')
  mockScrollOf(log, 1_000, 300)

  // GIVEN he has read up WHEN the next line arrives THEN the log stays where he left it
  log.scrollTop = 120
  fireEvent.scroll(log)
  await chat.push(event('partial', { kind: 'answer', text: 'noch eine Zeile' }))
  expect(log.scrollTop, 'a line he reads above the end is his place, not the stream\'s').toBe(120)

  // GIVEN he stands within the last few pixels of the end WHEN the stream adds THEN it follows again —
  // those pixels still count as "reading the newest line", so the log does not stop a hair too early
  log.scrollTop = 990
  fireEvent.scroll(log)
  await chat.push(event('partial', { kind: 'answer', text: 'und noch eine' }))
  expect(log.scrollTop, 'the end of the log is where the stream keeps him').toBe(1_000)
})




/**
 * The lines wearing the waiting frame — the dashed accent of R-WP-31, whatever voice they speak with. The
 * frame is a class the page hands down, so the screen is the place to ask for it (UC-WP-65).
 */
function framedLines(): Element[] {
  return Array.from(document.querySelectorAll('.chat-line.pending'))
}

/** The author labels of the agent lines, in reading order — the shape of R-WP-4's claim. */
function agentAuthorsOnScreen(): (string | null)[] {
  return authorsOfLines('chat-line-jon')
}

/** The texts the queue got, in the order they were posted — what a resend really put on the wire. */
function sentTexts(chat: Chat): string[] {
  return chat.calls
    .filter(call => call.method === 'POST' && call.path.endsWith('/messages'))
    .map(call => String(JSON.parse(call.body ?? '{}').text))
}

/**
 * The agent lines that say exactly this. An answer reads as Markdown (R-WP-28) and the renderer paints a
 * sentence in its own tokens, so the row is the unit a test asks for — never one span of it.
 */
function agentLinesSaying(text: string): HTMLElement[] {
  return screen.getAllByTestId('chat-line-jon').filter(line => line.textContent === text)
}

/** The author labels of the user lines, in reading order — what every viewer reads (R-WP-25, UC-WP-48). */
function userAuthorsOnScreen(): (string | null)[] {
  return authorsOfLines('chat-line-user')
}

/**
 * The texts of the user lines in reading order — his provisional ones and the history's, without the
 * author label (R-WP-37 counts lines, so the test counts them the same way).
 */
function userLineTexts(): (string | null)[] {
  return [
    ...screen
      .getByTestId('chat-log')
      .querySelectorAll('[data-testid="chat-line-user"] .chat-text'),
  ].map(line => line.textContent)
}

function authorsOfLines(lineTestId: string): (string | null)[] {
  return screen
    .getAllByTestId(lineTestId)
    .flatMap(line => within(line).queryAllByTestId('chat-line-author').map(label => label.textContent))
}
