import { afterEach, beforeEach, expect, test } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'

import { renderApp } from '../shared/app.harness'
import { declarations, declares } from '../shared/sheets'
import { separatorsOf } from '../shared/log.parts'
import { ChatPage } from '../../src/chat/chat.page'
import { project } from '../shared/fixtures'
import { content } from '../../src/shared/content'
import {
  agentAuthorsOnScreen,
  agentLinesSaying,
  ANSWER,
  ANNAS_QUESTION,
  closeChatStub,
  event,
  flush,
  JONS_ANSWER,
  QUESTION,
  resetChatStub,
  stubChat,
  TEAM_ANSWER,
  tile,
  userAuthorsOnScreen,
  userLineTexts,
} from './chat.page.stub'

beforeEach(() => resetChatStub())

afterEach(async () => {
  await closeChatStub()
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

// UC-WP-84
// UC-WP-94
test('aTurnEndingBeforeTheStreamStandsLeavesNoWaitingTile', async () => {
  // the row reads its roster while no subscription stands yet: the WORKING it reads then is a wait this view
  // only sees end through the fresh read on the stream's answer (R-WP-48, R-WP-57)
  const chat = stubChat({ messages: [QUESTION] })
  chat.setAgents([tile('Jon', 'WORKING')])
  const open = chat.deferNextStreamOpen()

  render(<ChatPage project={project()} />)
  await flush()

  expect(chat.countOf('/events', 'GET')).toBe(1)
  // UC-WP-94 GIVEN 1: the answer of the stream is still out — and the row already stands. The roster read is
  // not waiting for a subscription, so a stream that never opens cannot empty the head of the view
  expect(chat.countOf('/agents', 'GET'), 'the row read its roster while the stream has not answered').toBe(1)
  expect(screen.getByTestId('tile-state-Jon')).toHaveTextContent(content.tiles.state.WORKING)

  // the answer arrives and with it the subscription; the project says: nobody works any more
  chat.setAgents([tile('Jon', 'IDLE')])
  open.resolve()
  await flush()

  await waitFor(() =>
    expect(screen.getByTestId('tile-state-Jon')).toHaveTextContent(content.tiles.state.IDLE),
  )
  // UC-WP-94 GIVEN 2 / UC-WP-84 GIVEN 1: the first reading of the view, plus the fresh read the standing
  // connection answers with — that second stand is what retires the WORKING of the snapshot before it
  expect(
    chat.countOf('/agents', 'GET'),
    'the first reading of the view plus the fresh read of the standing connection',
  ).toBe(2)
})

// UC-WP-84
// UC-WP-94
test('theReconnectReadsTheRosterFresh', async () => {
  const chat = stubChat({ messages: [QUESTION], agents: [tile('Jon', 'WORKING')] })
  render(<ChatPage project={project()} />)
  await flush()
  expect(screen.getByTestId('tile-state-Jon')).toHaveTextContent(content.tiles.state.WORKING)
  const rosterReads = chat.countOf('/agents', 'GET')

  // the wire drops and the turn behind that WORKING ends while no one is watching: only a fresh read of
  // the roster can bring this board back from the wait (R-WP-48, R-WP-7)
  const ROSTER_FAILURE = 'the roster is not reachable'
  chat.setAgents([tile('Jon', 'IDLE')])
  chat.refuseRosterRead(503, ROSTER_FAILURE)
  await chat.closeStream()

  await waitFor(() => expect(chat.countOf('/events', 'GET')).toBe(2), { timeout: 1500 })
  // UC-WP-94 GIVEN 3: the fresh read of the standing connection fails. The page says why and the board keeps
  // the stand it read last — it never falls back to an empty row; the next reading is what corrects it
  expect(await screen.findByTestId('chat-error')).toHaveTextContent(ROSTER_FAILURE)
  expect(screen.getByTestId('chat-tiles'), 'no fall back to an empty row').toBeInTheDocument()
  expect(screen.getByTestId('tile-state-Jon'), 'the last read stand stays').toHaveTextContent(
    content.tiles.state.WORKING,
  )
  expect(chat.countOf('/agents', 'GET'), 'the refused read is the fresh read of the connection').toBe(
    rosterReads + 1,
  )

  // the positive replacement: the next reading lands, and the row moves with it (no second store, UC-WP-84)
  await chat.closeStream()
  // the window is wider than the first one: nothing was delivered in between, so this attempt waits 1000 ms (R-WP-58)
  await waitFor(() => expect(chat.countOf('/events', 'GET')).toBe(3), { timeout: 2500 })
  await waitFor(() =>
    expect(screen.getByTestId('tile-state-Jon')).toHaveTextContent(content.tiles.state.IDLE),
  )
  expect(chat.countOf('/agents', 'GET'), 'the next reading corrected the board').toBe(rosterReads + 2)
})

// UC-WP-84
test('aStaleRosterAnswerNeverOverwritesTheEndSnapshot', async () => {
  // the view's first reading is the one still on the wire while the turn worked; the stream's end snapshot
  // overtakes it, so the answer that arrives last is the older stand and has to lose (UC-WP-84 GIVEN 3)
  const chat = stubChat({ messages: [QUESTION] })
  chat.setAgents([tile('Jon', 'WORKING')])
  const held = chat.deferNextAgentsRead()

  render(<ChatPage project={project()} />)
  await flush()
  expect(
    chat.countOf('/agents', 'GET'),
    'the held first reading of the view plus the fresh read of the standing connection',
  ).toBe(2)

  await chat.push(event('agents', { tiles: [tile('Jon', 'IDLE')] }))
  expect(screen.getByTestId('tile-state-Jon')).toHaveTextContent(content.tiles.state.IDLE)

  held.resolve()
  await flush()
  expect(
    screen.getByTestId('tile-state-Jon'),
    'the read that began before the snapshot lost',
  ).toHaveTextContent(content.tiles.state.IDLE)
  expect(chat.countOf('/agents', 'GET'), 'no second read chased the lost answer').toBe(2)
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

  // the controls the chat has: one writes into THIS conversation — empty included, which repeats the last
  // line of the history (R-WP-30, UC-WP-57) — and one ends its running turn (UC-WP-50); none names an
  // addressee and none starts a conversation.
  // The tile actions of Inc 5b sit beside them and are asserted separately below (R-WP-7)
  const controls = within(opened)
    .queryAllByRole('button')
    .filter(control => control.closest('.chat-tiles') === null)
  // an action of the chat is an icon button: the catalog word is its name and its tooltip, no word stands in
  // the button itself (R-WP-59, UC-WP-96 GIVEN 1)
  const names = controls.map(control => control.getAttribute('aria-label') ?? '')
  expect(names).toEqual([content.chat.send, content.chat.stop])
  expect(controls.map(control => control.textContent?.trim() ?? ''), 'no text button left').toEqual(['', ''])
  expect(
    names.filter(label =>
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
  expect(
    [...new Set(tileActions.map(action => action.getAttribute('aria-label') ?? ''))].sort(),
    'the tile names its actions from the catalog',
  ).toEqual([content.tiles.clear, content.tiles.compact])
  expect(
    tileActions.filter(action => (action.textContent?.trim() ?? '') !== ''),
    'both tile actions are icon buttons (UC-WP-96 GIVEN 2)',
  ).toEqual([])
  const tileDoors = [...opened.querySelectorAll('.chat-tile-face[role="button"]')].map(
    door => door.getAttribute('aria-label') ?? '',
  )
  expect(tileDoors, 'only the tile with TRANSCRIPT opens a conversation').toEqual([
    content.tiles.open('Plan'),
  ])

  // A1 (R-WP-39): the frame's entry is the basis' menu item now — the click and its claim are unchanged.
  // Since R-WP-44 the way to the list is the header's page entry: the tree holds projects and their units.
  fireEvent.click(
    within(screen.getByRole('navigation', { name: content.nav.pages })).getByRole('menuitem', {
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

  // GIVEN 2 — what the model thinks on its way to the answer is no speech: it is an entry of the trace, it
  // names nobody and it opens no line of the conversation (R-WP-40, R-WP-4). A MEMBER's thinking never
  // reaches this wire at all — the core forwards only the thinking of the turn that answers, proven on the
  // core side by DelegatedAnswerTest and TeamInternalsTest — so the screen has nothing to filter and this
  // case proves the one channel it owns (R-WP-40, rewritten 2026-11-07)
  const thoughtEntries = () => [
    ...screen.getByTestId('chat-log').querySelectorAll('.chat-trace-item[data-trace="think"]'),
  ]
  await chat.push(event('partial', { kind: 'think', text: 'DENKTEXT-VON-JON' }))
  expect(screen.queryByTestId('chat-line-jon'), 'a think delta opens no line of the conversation').toBeNull()
  expect(thoughtEntries(), 'it is an entry of the trace and nothing else').toHaveLength(1)
  expect(
    thoughtEntries()[0].querySelector('.chat-step-name')?.textContent,
    'the entry says what it is and lends no author to the thought',
  ).toBe(content.chat.thinking)
  expect(thoughtEntries()[0]).toHaveTextContent('DENKTEXT-VON-JON')

  // GIVEN 1 — and a member's final answer is no live event either: while the turn runs, Jon's own trace
  // is the only named thing on the wire, and no line of the conversation carries a member's text
  await chat.push(event('tool', { text: 'a tool line of the running turn' }))
  expect(screen.queryByTestId('chat-line-jon')).toBeNull()
  expect(screen.getByTestId('chat-timeline')).toHaveTextContent('a tool line of the running turn')

  // with `done` the history is read once, and it carries the member's line under its own name — once
  chat.setMessages([QUESTION, TEAM_ANSWER, JONS_ANSWER])
  await chat.push(event('done', { millis: 4321 }))

  expect(agentAuthorsOnScreen()).toEqual(['Dev', 'Jon'])
  expect(screen.getAllByTestId('chat-line-jon')).toHaveLength(2)
  expect(screen.getAllByText('Eine Banane.')).toHaveLength(1)
  expect(
    agentLinesSaying('DENKTEXT-VON-JON'),
    'a think text never becomes a line of the conversation, not even after the read',
  ).toEqual([])
  expect(
    thoughtEntries(),
    'the thought stays an entry of the trace through the read, like the steps beside it (UC-WP-54)',
  ).toHaveLength(1)
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

// UC-WP-55
test('agentLineIsNamedByItsAgent', async () => {
  stubChat({ messages: [QUESTION, TEAM_ANSWER, JONS_ANSWER] })
  render(<ChatPage project={project()} />)
  await flush()

  const lines = screen.getAllByTestId('chat-line-jon')
  expect(lines).toHaveLength(2)
  for (const line of lines) {
    expect(line.className).toContain('agent')
    expect(line.className).not.toContain('user')
    // the label of an agent line is the agent that spoke (R-WP-4)
    expect(line.querySelector('.chat-role')).not.toBeNull()
  }
  expect(lines.map(line => within(line).getByTestId('chat-line-author').textContent)).toEqual(['Dev', 'Jon'])
})

// UC-WP-55
// R-WP-29
test('roleChangeDrawsNoRule', async () => {
  stubChat({ messages: [QUESTION, TEAM_ANSWER, JONS_ANSWER, { role: 'user', text: 'und im Root?' }] })
  render(<ChatPage project={project()} />)
  await flush()

  // user, agent, agent, user: two changes of voice and no line for either — bubble and card tell them
  // apart, no row carries an accent edge (R-WP-29, UC-WP-55 GIVEN 2)
  expect(separatorsOf(screen.getByTestId('chat-log')), 'the whole log draws no separator').toEqual([])
  expect(
    screen.getAllByTestId(/chat-line-(user|jon)/).map(line => (line.classList.contains('user') ? 'user' : 'agent')),
  ).toEqual(['user', 'agent', 'agent', 'user'])
})

/**
 * Only the person's own input has a side (R-WP-29, UC-WP-55 as the doc states it now): no answer stands
 * centred or right-aligned, and the one row that may step to an edge is the voice `user`. Read for the base
 * row and for the answer's own rule — a side on the base would take every voice with it. Where a row stands
 * is what a browser lays out from the sheet; jsdom measures nothing (see `test/shared/sheets.ts`).
 */
// UC-WP-55
test('noAnswerLineIsCentredOrRightAligned', async () => {
  stubChat({ messages: [QUESTION, ANSWER] })
  render(<ChatPage project={project()} />)
  await flush()

  expect(screen.getByTestId('chat-line-jon').className).toContain('agent')
  expect(declares('.chat-line.jon'), 'a row is painted by its voice, never by the name of its author').toBe(
    false,
  )

  // centred, pushed to a side or floated by a free margin — every way a row could leave the flow of the
  // conversation, whether the sheet spells it as an edge or as `auto`
  const outOfTheFlow =
    /text-align:\s*(center|right)|margin[^;]*auto|align-self:\s*(flex-end|center)|justify-self:\s*(end|center)/
  for (const selector of ['.chat-log .chat-line', '.chat-log .chat-line.agent']) {
    expect(declarations(selector), `${selector} stands centred or right-aligned`).not.toMatch(outOfTheFlow)
  }
})

