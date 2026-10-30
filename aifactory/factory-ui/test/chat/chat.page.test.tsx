import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'

import AppShell from '../../src/app/app.shell'
import { ChatPage } from '../../src/chat/chat.page'
import { project } from '../shared/fixtures'
import type { ChatMessage } from '../../src/chat/chat.client'

interface Chat {
  calls: { method: string; path: string; body?: string }[]
  /** How often an edge was called — the count <em>is</em> the assertion behind "ohne neu zu laden". */
  countOf(pathEnd: string, method: string): number
  /** The answer of the next history read, so a test can hand back what the turn wrote. */
  setMessages(messages: ChatMessage[]): void
  /** Writes bytes onto the open stream — may stop in the middle of an event. */
  push(text: string): Promise<void>
  closeStream(): Promise<void>
}

const QUESTION: ChatMessage = { role: 'user', text: 'wo liegen die Dateien?' }
const ANSWER: ChatMessage = { role: 'jon', text: 'Im Root liegen: pom.xml und README.md' }
/** A team member's final answer — role `jon` with an author, never a third role (UC-WP-39). */
const TEAM_ANSWER: ChatMessage = { role: 'jon', text: 'Eine Banane.', origin: 'Da Mek' }
const JONS_ANSWER: ChatMessage = { role: 'jon', text: 'Da Mek nennt eine Banane.', origin: 'Jon' }

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
 * `fetch` stand-in for the three chat edges. The stream body is a `ReadableStream` the test writes
 * into byte by byte, so a chunk may end anywhere — including inside one event's JSON.
 */
function stubChat(initial: { messages?: ChatMessage[]; projects?: unknown[] } = {}): Chat {
  const calls: Chat['calls'] = []
  const encoder = new TextEncoder()
  const state = { messages: initial.messages ?? [] }
  let controller: ReadableStreamDefaultController<Uint8Array> | undefined

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
      if (path.endsWith('/messages') && method === 'POST') {
        return new Response(null, { status: 202 })
      }
      if (path.endsWith('/messages')) {
        return json(200, state.messages)
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
    push: async (text: string) => {
      await act(async () => {
        controller?.enqueue(encoder.encode(text))
        await new Promise(resolve => setTimeout(resolve, 0))
      })
    },
    closeStream: async () => {
      await act(async () => {
        controller?.close()
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

  expect(screen.getByText('Kein Projekt geöffnet')).toBeInTheDocument()
  expect(screen.queryByTestId('chat-input')).toBeNull()
})

// UC-WP-5
test('openProjectBindsWorkDir', async () => {
  const chat = stubChat({ messages: [QUESTION], projects: [project()] })
  render(<AppShell />)
  expect(await screen.findByLabelText('Git-URL')).toBeInTheDocument()

  fireEvent.click(await screen.findByRole('button', { name: 'demo' }))

  expect(screen.getByTestId('chat-log')).toBeInTheDocument()
  await flush()
  expect(screen.getByText('wo liegen die Dateien?')).toBeInTheDocument()
  expect(screen.getByTestId('chat-input')).toHaveFocus()
  // the project list is gone — opening a project opens its chat, it does not only remember it
  expect(screen.queryByLabelText('Git-URL')).toBeNull()
  const reads = chat.calls.filter(call => call.path.endsWith('/messages'))
  expect(reads.map(call => call.path)).toEqual(['/api/projects/demo-1a2b3c4/messages'])
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
  expect(screen.queryByTestId('chat-tool-line')).toBeNull()
  // the own input lives in the history only now — the pending copy is gone, no line twice
  expect(screen.getAllByTestId('chat-line-user')).toHaveLength(2)
})

test('turnFailureIsALineAndTheComposerStaysUsable', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  await chat.push(event('error', { message: 'Es fehlt ein Modellschlüssel für OPEN_AI' }))

  expect(screen.getByTestId('chat-error')).toHaveTextContent('Es fehlt ein Modellschlüssel für OPEN_AI')
  // no alarm dialog, no dead input: the next turn is one line away (Q1)
  expect(screen.getByTestId('chat-input')).toBeEnabled()
  expect(screen.getByTestId('chat-send')).toBeDisabled()
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'und jetzt mit Schlüssel' } })
  expect(screen.getByTestId('chat-send')).toBeEnabled()
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


// UC-WP-16
test('oneChatPerProject', async () => {
  const chat = stubChat({ messages: [QUESTION, ANSWER], projects: [project()] })
  render(<AppShell />)
  expect(await screen.findByLabelText('Git-URL')).toBeInTheDocument()

  fireEvent.click(await screen.findByRole('button', { name: 'demo' }))
  const opened = await screen.findByRole('region', { name: 'Chat' })
  await screen.findByText(ANSWER.text)

  // the only control the chat has is the one that writes into THIS conversation
  const controls = within(opened)
    .queryAllByRole('button')
    .map(control => control.textContent?.trim() ?? '')
  expect(controls).toEqual(['Senden'])
  expect(controls.filter(label => /neue|gespräch|dialog|verlauf|wechseln|öffnen|clear|lösch/i.test(label))).toEqual(
    [],
  )

  fireEvent.click(screen.getByRole('tab', { name: 'Projekte' }))
  expect(screen.queryByTestId('chat-log')).toBeNull()

  // the list re-reads itself on mount, so the project has to be found again before it can be opened
  fireEvent.click(await screen.findByRole('button', { name: 'demo' }))
  const again = await screen.findByRole('region', { name: 'Chat' })
  await screen.findByText(ANSWER.text)

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

  // exactly two lines have an agent relationship, and exactly those two carry a name (R-WP-4)
  expect(authorsOnScreen()).toEqual(['Da Mek', 'Jon'])

  // the own input has no agent relationship: no label, and nothing standing in for one
  const own = screen.getByTestId('chat-line-user')
  expect(within(own).queryAllByTestId('chat-line-author')).toHaveLength(0)
  expect(screen.queryByText(/System|Peon|Unbekannt/i)).toBeNull()
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

  expect(authorsOnScreen()).toEqual(['Da Mek', 'Jon'])
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
  expect(authorsOnScreen()).toEqual(['Da Mek', 'Jon'])
  expect(screen.queryByTestId('chat-live')).toBeNull()
})

/** The author labels on the screen, in reading order — the shape of R-WP-4's claim. */
function authorsOnScreen(): (string | null)[] {
  return screen.getAllByTestId('chat-line-author').map(label => label.textContent)
}
