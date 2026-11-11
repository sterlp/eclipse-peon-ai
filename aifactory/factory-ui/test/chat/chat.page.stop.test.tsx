import { afterEach, beforeEach, expect, test } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

import { ChatPage } from '../../src/chat/chat.page'
import type { TileCapability } from '../../src/chat/chat.agents.client'
import { project } from '../shared/fixtures'
import { content } from '../../src/shared/content'
import {
  ANSWER,
  closeChatStub,
  event,
  flush,
  QUESTION,
  resetChatStub,
  sentTexts,
  stubChat,
  tile,
  userLineTexts,
} from './chat.page.stub'

beforeEach(() => resetChatStub())

afterEach(async () => {
  await closeChatStub()
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

/** A stopped turn lives only live (D-16: its `done` reads nothing), so its rows are not in the Bestand — the
 *  rebuild of the action that follows takes its mark where they began, the rebuild after ends them (R-WP-63). */
// UC-WP-100
// UC-WP-50
test('aCompactAfterAStopKeepsTheLinesOfTheStoppedTurn', async () => {
  const FIRST = 'Session compacted: zwei Dateien, eine Banane.'
  const SECOND = 'Session compacted: und wieder.'
  const chat = stubChat({ messages: [QUESTION], agents: [tile('Jon', 'IDLE')] })
  render(<ChatPage project={project()} />)
  await flush()
  const historyReads = chat.countOf('/messages', 'GET')
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'schlaf drei sekunden' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await chat.push(event('tool', { text: 'sleep 3' }))
  await chat.push(event('partial', { kind: 'answer', text: 'Ich schlafe gerade' }))
  fireEvent.click(screen.getByTestId('chat-stop'))
  await flush()
  chat.setMessages([QUESTION])
  await chat.push(event('done', { millis: 5 }))
  await flush()

  // the conversation's own Compact ends now: its mark is the row the stopped turn started at
  chat.setMessages([{ role: 'user', text: FIRST }])
  fireEvent.click(screen.getByTestId('tile-compact-Jon'))
  await chat.push(event('agents', { tiles: [tile('Jon', 'COMPACTING')] }))
  await chat.push(event('agents', { tiles: [tile('Jon', 'IDLE')] }))
  await flush()
  expect(chat.countOf('/messages', 'GET'), 'one read, on the falling edge').toBe(historyReads + 1)
  expect(userLineTexts(), 'the Bestand stands, beside his line').toEqual([FIRST, 'schlaf drei sekunden'])
  expect(screen.getByTestId('chat-live'), 'the stopped answer stays').toHaveTextContent('Ich schlafe gerade')
  expect(traceSteps().map(step => step.textContent), 'and so is its step').toEqual(['sleep 3'])
  // R-WP-63's second sentence: from here they are an ordinary live trace — the next rebuild ends them
  chat.setMessages([{ role: 'user', text: SECOND }])
  fireEvent.click(screen.getByTestId('tile-compact-Jon'))
  await chat.push(event('agents', { tiles: [tile('Jon', 'COMPACTING')] }))
  await chat.push(event('agents', { tiles: [tile('Jon', 'IDLE')] }))
  await flush()
  expect(chat.countOf('/messages', 'GET'), 'again exactly one read').toBe(historyReads + 2)
  expect(userLineTexts(), 'the fresh Bestand stands').toEqual([SECOND, 'schlaf drei sekunden'])
  expect(screen.queryAllByTestId('chat-live'), 'the stopped turn ends like any live trace').toHaveLength(0)
  expect(screen.queryAllByTestId('chat-timeline'), 'and its step went with it').toHaveLength(0)
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
  expect(screen.queryByTestId('error-panel')).toBeNull()
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

/** The seat of a member: all four capabilities — the insight is what tells his seat from the conversation's own. */
const MEMBER_SEAT: TileCapability[] = ['CONTEXT', 'CLEAR', 'COMPACT', 'TRANSCRIPT']

/** The steps of the trace and the ones wearing the indicator — our own `data-*`, never a class of the basis. */
const traceSteps = () =>
  screen
    .getAllByTestId('chat-timeline')
    .flatMap(widget => [...widget.querySelectorAll('.chat-trace-item[data-trace="tool"]')])
const workingSteps = () => traceSteps().filter(step => step.hasAttribute('data-working'))

// R-WP-63
// UC-WP-100
test('theConversationIsRebuiltWhenItsCompactEnds', async () => {
  const STEP = '🗜 Compressing Jon: 12 messages 78000 tokens (estimate)'
  const SUMMARY = 'Session compacted: zwei Dateien, eine Banane.'
  const chat = stubChat({ messages: [QUESTION], agents: [tile('Jon', 'IDLE'), tile('Dev', 'IDLE')] })
  render(<ChatPage project={project()} />)
  await flush()
  const historyReads = chat.countOf('/messages', 'GET')

  fireEvent.click(screen.getByTestId('tile-compact-Jon'))
  await chat.push(event('agents', { tiles: [tile('Jon', 'COMPACTING'), tile('Dev', 'IDLE')] }))
  await chat.push(event('tool', { text: STEP, origin: 'Jon' }))
  expect(workingSteps(), 'the step of a running action works').toHaveLength(1)

  // what the core leaves in the memory: the one summary line, and nothing else to show
  chat.setMessages([{ role: 'user', text: SUMMARY }])
  await chat.push(event('agents', { tiles: [tile('Jon', 'IDLE'), tile('Dev', 'IDLE')] }))
  await flush()

  // the end of the area is the moment, not the acceptance: one read, taken when the Compact was over
  expect(chat.countOf('/messages', 'GET'), 'one read, on the falling edge').toBe(historyReads + 1)
  expect(userLineTexts(), 'the conversation is what the Bestand holds now').toEqual([SUMMARY])
  expect(screen.queryByText(QUESTION.text), 'the line the memory dropped is gone with it').toBeNull()
  expect(
    [...screen.getByTestId('chat-timeline').querySelectorAll('.chat-trace-item')].map(line => line.textContent),
    "the Compact wrote no line into any memory — it stays as the live trace of this action",
  ).toEqual([`Jon${STEP}`])
  expect(workingSteps(), 'nothing spins any more').toEqual([])
})

// UC-WP-100
test('clearEmptiesTheConversationWhenItsTileIsIdle', async () => {
  const chat = stubChat({ messages: [QUESTION, ANSWER], agents: [tile('Jon', 'IDLE')] })
  render(<ChatPage project={project()} />)
  await flush()
  const historyReads = chat.countOf('/messages', 'GET')
  const rosterReads = chat.countOf('/agents', 'GET')

  fireEvent.click(screen.getByTestId('tile-clear-Jon'))
  await chat.push(event('agents', { tiles: [tile('Jon', 'WORKING')] }))
  await flush()

  // 202 means <em>taken</em>: the memory still holds the conversation, and a read here is the defect the rule
  // rules out — so nothing may have been read while the tile stands in the action's area
  expect(userLineTexts(), 'the conversation still stands while the action runs').toEqual([QUESTION.text])
  expect(chat.countOf('/messages', 'GET')).toBe(historyReads)

  chat.setMessages([])
  await chat.push(event('agents', { tiles: [tile('Jon', 'IDLE')] }))
  await flush()

  expect(chat.countOf('/messages', 'GET'), 'one read, on the falling edge').toBe(historyReads + 1)
  expect(screen.queryAllByTestId('chat-line-user'), 'the conversation is empty, as the plugin leaves it').toEqual([])
  expect(screen.queryAllByTestId('chat-line-jon')).toEqual([])
  // the roster's read occasion moved with the action's end (its start arrives on the wire anyway, R-WP-60)
  expect(chat.countOf('/agents', 'GET')).toBe(rosterReads + 1)
})

// UC-WP-100
test('aTileSnapshotAloneNeverRebuildsTheConversation', async () => {
  const chat = stubChat({
    messages: [QUESTION],
    agents: [tile('Jon', 'IDLE'), tile('Dev', 'IDLE', MEMBER_SEAT)],
  })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()
  await chat.push(event('tool', { text: 'ls in the checkout' }))

  const historyReads = chat.countOf('/messages', 'GET')
  const rosterReads = chat.countOf('/agents', 'GET')
  const shown = userLineTexts()
  expect(workingSteps(), 'the turn is open and its step works').toHaveLength(1)

  // GIVEN 3: another turn's ends and states are nobody's action — the running turn keeps what it reported
  await chat.push(event('agents', { tiles: [tile('Jon', 'WORKING'), tile('Dev', 'IDLE', MEMBER_SEAT)] }))
  await chat.push(event('agents', { tiles: [tile('Jon', 'IDLE'), tile('Dev', 'IDLE', MEMBER_SEAT)] }))
  await flush()
  expect(chat.countOf('/messages', 'GET')).toBe(historyReads)
  expect(workingSteps(), 'a snapshot takes the indicator of an open turn away from nothing').toHaveLength(1)

  // GIVEN 4: the core's own Compact reports the same area and ends the same way — no click, no rebuild
  await chat.push(event('agents', { tiles: [tile('Jon', 'COMPACTING'), tile('Dev', 'IDLE', MEMBER_SEAT)] }))
  await chat.push(event('agents', { tiles: [tile('Jon', 'IDLE'), tile('Dev', 'IDLE', MEMBER_SEAT)] }))
  await flush()
  expect(chat.countOf('/messages', 'GET')).toBe(historyReads)
  expect(workingSteps()).toHaveLength(1)

  // Grenze 2: an accepted action on a member's tile changes his conversation and nobody's here
  fireEvent.click(screen.getByTestId('tile-compact-Dev'))
  await chat.push(event('agents', { tiles: [tile('Jon', 'IDLE'), tile('Dev', 'COMPACTING', MEMBER_SEAT)] }))
  await chat.push(event('agents', { tiles: [tile('Jon', 'IDLE'), tile('Dev', 'IDLE', MEMBER_SEAT)] }))
  await flush()

  expect(chat.countOf('/messages', 'POST'), 'the click sent its action, and only that').toBe(1)
  expect(chat.countOf('/messages', 'GET'), 'no snapshot of these read the conversation again').toBe(historyReads)
  expect(chat.countOf('/agents', 'GET'), "a member's action reads nothing at this chat").toBe(rosterReads)
  expect(userLineTexts(), 'the lines the turn showed stay the lines on the screen').toEqual(shown)
  expect(screen.getByTestId('chat-timeline')).toHaveTextContent('ls in the checkout')
})

// UC-WP-57
test('emptySendResendsTheStoppedUserMessage', async () => {
  const chat = stubChat({ messages: [QUESTION], agents: [tile('Jon', 'IDLE')] })
  render(<ChatPage project={project()} />)
  await flush()

  const stopped = 'mach die zweiten Dateien fertig'
  // the second question goes out; the history never confirms it, so it stays this browser's pending line
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: stopped } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()

  // the stop is accepted and its `done` reads nothing back (D-16): the history keeps **less** than the screen
  // shows, so the repeat has to answer to what is visible and not to the last line of the history
  fireEvent.click(screen.getByTestId('chat-stop'))
  await flush()
  chat.setMessages([QUESTION])
  await chat.push(event('done', { millis: 5 }))
  const reads = chat.countOf('/messages', 'GET')

  // no turn runs, the field stands empty, and the newest user line on the screen is the stopped one
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()

  expect(chat.countOf('/messages', 'POST')).toBe(2)
  expect(sentTexts(chat), 'the stopped input, never the older history line').toEqual([stopped, stopped])
  expect(userLineTexts(), 'history line, pending line, the repeated one').toEqual([
    QUESTION.text,
    stopped,
    stopped,
  ])
  expect(chat.countOf('/messages', 'GET'), 'a repeat reads nothing again').toBe(reads)
  expect(screen.getByTestId('chat-input')).toHaveValue('')
})

