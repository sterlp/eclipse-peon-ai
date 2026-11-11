import { afterEach, beforeEach, expect, test } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'

import { ChatPage } from '../../src/chat/chat.page'
import { project } from '../shared/fixtures'
import { content } from '../../src/shared/content'
import {
  agentLinesSaying,
  ANSWER,
  closeChatStub,
  DIFF,
  event,
  flush,
  JONS_ANSWER,
  QUESTION,
  resetChatStub,
  stubChat,
  TEAM_ANSWER,
  tile,
  userLineTexts,
} from './chat.page.stub'

beforeEach(() => resetChatStub())

afterEach(async () => {
  await closeChatStub()
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

  const shape = screen.getAllByTestId(/chat-(live|timeline)/).map(entry =>
    entry.getAttribute('data-testid') === 'chat-live'
      ? `text ${entry.textContent?.trim() ?? ''}`
      : `tool ${[...entry.querySelectorAll('.chat-trace-item')].map(line => line.textContent).join('|')}`,
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
  expect(screen.getByTestId('chat-timeline')).toHaveTextContent('ls in the checkout')

  // the turn ends and the history is read: since R-WP-47 the step is a line of that history — the read is
  // the moment it would be lost, and it is not (UC-WP-54, UC-WP-82)
  chat.setMessages([
    QUESTION,
    { role: 'user', text: 'zähl die Dateien' },
    { role: 'tool', text: 'ls in the checkout', origin: 'shellRunCommand' },
    ANSWER,
  ])
  await chat.push(event('done', { millis: 7 }))

  const entries = [...screen.getByTestId('chat-log').children]
  const tool = screen.getByTestId('chat-timeline')
  expect(tool).toHaveTextContent('ls in the checkout')
  expect(tool, 'and it names the tool the memory names').toHaveTextContent('shellRunCommand')
  // and it stands where it happened: after the input it belongs to, before the answer that followed it
  const answerAt = entries.findIndex(entry => entry.getAttribute('data-testid') === 'chat-line-jon')
  expect(entries.indexOf(tool)).toBeGreaterThanOrEqual(0)
  expect(answerAt).toBeGreaterThan(entries.indexOf(tool))
  // the streaming text gave way to the history and is not shown twice
  expect(screen.queryByTestId('chat-live')).toBeNull()
  expect(agentLinesSaying(ANSWER.text)).toHaveLength(1)
})

/**
 * The reload's own account of what the tools did (R-WP-47, UC-WP-82 GIVEN 1): the history carries a line per
 * tool with its name and an excerpt of its result, and the page draws them between the input that caused them
 * and the answer that followed — one widget, no second block, and no line of a voice, since a step is not a
 * voice. A delegation's step names the member it asked, not the tool that asked him.
 */
// UC-WP-82
test('aReloadedChatDrawsTheStepsOfTheBestand', async () => {
  stubChat({
    messages: [
      QUESTION,
      { role: 'tool', text: 'Im Root liegen pom.xml und README.md', origin: 'shellRunCommand' },
      { role: 'tool', text: '', origin: 'Dev' },
      ANSWER,
    ],
  })
  render(<ChatPage project={project()} />)
  await flush()

  expect([...screen.getByTestId('chat-log').children].map(line => line.getAttribute('data-testid'))).toEqual([
    'chat-line-user',
    'chat-timeline',
    'chat-line-jon',
  ])
  const steps = [
    ...screen.getByTestId('chat-timeline').querySelectorAll('.chat-trace-item[data-trace="tool"]'),
  ]
  expect(
    steps.map(step => step.textContent),
    'one step per tool, in the order of the Bestand, each named by the name it carries',
  ).toEqual(['shellRunCommandIm Root liegen pom.xml und README.md', 'Dev'])
  expect(
    screen.queryAllByTestId('chat-line-tool'),
    'a step has exactly one drawing path — the timeline, never a line of a voice',
  ).toEqual([])
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

  // one timeline for a run of tool activity, and only its tail — what a turn reports is a trace beside the
  // answer and never a list that pushes the answer away (chat.html keeps ten lines)
  const widgets = screen.getAllByTestId('chat-timeline')
  expect(widgets).toHaveLength(1)
  const lines = [...widgets[0].querySelectorAll('.chat-trace-item[data-trace="tool"]')].map(
    line => line.textContent,
  )
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

  const widget = screen.getByTestId('chat-timeline')
  const steps = [...widget.querySelectorAll('.chat-trace-item[data-trace="tool"]')]
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

  const widget = screen.getByTestId('chat-timeline')
  const steps = [...widget.querySelectorAll('.chat-trace-item[data-trace="tool"]')]
  const working = [...widget.querySelectorAll('[data-working="true"]')]
  expect(steps).toHaveLength(2)
  expect(working, 'one open turn has one working step').toHaveLength(1)
  expect(working[0], 'and it is the step that came last').toBe(steps[1])
  expect(
    steps[0].getAttribute('title'),
    'the step the next one ended carries the clock of its arrival — the wire brought the moment',
  ).toMatch(/^\d{2}:\d{2}$/)
  expect(steps[1], 'a step that works has no clock yet').not.toHaveAttribute('title')

  // the turn ends and the history is read: no step works any more, and both keep their place — as lines of
  // the Bestand, which knows no moment, so no step carries a title now (R-WP-40, UC-WP-54, UC-WP-72 GIVEN 2,
  // R-WP-47)
  chat.setMessages([
    QUESTION,
    { role: 'user', text: 'zähl die Dateien' },
    { role: 'tool', text: 'ls in the checkout', origin: 'shellRunCommand' },
    { role: 'tool', text: '3 pom.xml', origin: 'shellRunCommand' },
    ANSWER,
  ])
  await chat.push(event('done', { millis: 12 }))
  await flush()

  const restored = screen.getByTestId('chat-timeline')
  expect(
    [...restored.querySelectorAll('[data-working="true"]')],
    'with the turn the indicator ends, and a step of the Bestand works for nobody',
  ).toEqual([])
  const finished = [...restored.querySelectorAll('.chat-trace-item[data-trace="tool"]')]
  expect(finished.map(step => step.textContent), 'both steps stand, named by the memory').toEqual([
    'shellRunCommandls in the checkout',
    'shellRunCommand3 pom.xml',
  ])
  for (const step of finished) {
    expect(step, 'the Bestand knows no moment — a restored step carries no title').not.toHaveAttribute('title')
  }
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
    screen
      .getAllByTestId('chat-timeline')
      .flatMap(widget => [...widget.querySelectorAll('.chat-trace-item[data-trace="tool"]')])
  const working = () => steps().filter(step => step.hasAttribute('data-working'))

  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await chat.push(event('tool', { text: 'ls in the checkout' }))
  expect(working(), 'a step of the open turn works').toHaveLength(1)

  chat.setMessages([
    QUESTION,
    { role: 'user', text: 'zähl die Dateien' },
    { role: 'tool', text: 'ls in the checkout', origin: 'shellRunCommand' },
    ANSWER,
  ])
  await chat.push(event('done', { millis: 12 }))
  await flush()

  // the turn is over and its trace stands — since R-WP-47 as a line of the Bestand: nothing of it works
  expect(screen.getByTestId('chat-timeline')).toHaveTextContent('ls in the checkout')
  expect(working()).toEqual([])

  // the next input opens a turn that has reported nothing yet: the wait is on the screen, while the step of
  // the finished turn keeps its place without its indicator
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'und jetzt die README' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await flush()
  expect(screen.getByTestId('chat-waiting'), 'a turn is open').toBeInTheDocument()
  expect(
    steps(),
    'the step of the Bestand still stands there while the new turn waits',
  ).toHaveLength(1)
  expect(working(), 'the old step does not work for a turn it never belonged to').toEqual([])

  // the first step of the open turn is where the indicator goes — the rule is not simply switched off
  await chat.push(event('tool', { text: 'wc -l README.md' }))
  expect(working()).toHaveLength(1)
  expect(working()[0].textContent).toContain('wc -l README.md')
})

/**
 * The identity a step answers to is its turn, never the number of rows that turn anchored at (R-WP-40,
 * UC-WP-72 GIVEN 2). A Clear hands the log back exactly the row count the finished turn anchored at — and
 * takes the step with it, since R-WP-47 makes it a line of the Bestand like every answer (UC-WP-54). So the
 * next turn, opening at that same count, finds no step of the finished one left to work on.
 */
// UC-WP-72
test('twoTurnsCountingTheSameRowsAreNotTheSameTurn', async () => {
  // queries, not getters: a cleared conversation draws no widget at all, and that is one of the facts here
  const steps = () =>
    screen
      .queryAllByTestId('chat-timeline')
      .flatMap(widget => [...widget.querySelectorAll('.chat-trace-item[data-trace="tool"]')])
  const working = () => steps().filter(step => step.hasAttribute('data-working'))

  const chat = stubChat({ messages: [], agents: [tile('Jon', 'IDLE')] })
  render(<ChatPage project={project()} />)
  await flush()

  // the first turn anchors behind its own pending line, on a log of no row
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await chat.push(event('tool', { text: 'ls in the checkout' }))
  expect(working(), 'a step of the open turn works').toHaveLength(1)

  // R-WP-47: the read hands the step back as a line of its own, so the finished turn's step stands there
  // without working — it belongs to nobody's open turn (UC-WP-82)
  chat.setMessages([
    { role: 'user', text: 'zähl die Dateien' },
    { role: 'tool', text: 'ls in the checkout', origin: 'shellRunCommand' },
    ANSWER,
  ])
  await chat.push(event('done', { millis: 11 }))
  await flush()
  expect(userLineTexts(), 'the read of the finished turn has landed').toEqual(['zähl die Dateien'])
  expect(steps(), 'the step of the finished turn stands, named by the memory').toHaveLength(1)
  expect(working(), 'the turn ended with its step').toEqual([])

  // the conversation is cleared: since R-WP-47 a step is a line of the Bestand, so it leaves with the rest
  // of it (UC-WP-54) — and the log counts its rows from zero again, the same number the finished turn
  // anchored at
  chat.setMessages([])
  fireEvent.click(screen.getByTestId('tile-clear-Jon'))
  await chat.push(event('agents', { tiles: [tile('Jon', 'WORKING')] }))
  // R-WP-63: `202` says taken, so what rebuilds the conversation is the tile leaving WORKING — the read of a
  // cleared conversation is asked for by that snapshot, never by the answer
  await chat.push(event('agents', { tiles: [tile('Jon', 'IDLE')] }))
  await waitFor(() => expect(userLineTexts()).toEqual([]))
  expect(
    screen.queryByTestId('chat-timeline'),
    'a step is no longer a live-only line, so a cleared conversation shows no trace',
  ).toBeNull()
  expect(working(), 'a cleared conversation works nothing').toEqual([])

  // the next input opens its turn at the same row number and answers with text before its first step
  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'und jetzt die README' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await chat.push(event('partial', { kind: 'answer', text: 'Im Root liegen ' }))
  expect(
    working(),
    'a word is no step: the open turn works nowhere before the wire reports one',
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
 * `done` triggers may drop it behind the answer it delegated. Since R-WP-40 draws the trace of one place as
 * ONE timeline, what stands at that place is the widget, and the member is a link of it.
 */
// UC-WP-72
test('aDelegationStandsWhereItHappenedAndNotAtTheEndOfTheLog', async () => {
  const shape = () =>
    [...screen.getByTestId('chat-log').children].map(line => line.getAttribute('data-testid'))
  /** The kinds of the entries one timeline draws, in the order the screen shows them. */
  const tracesOf = (widget: Element) =>
    [...widget.querySelectorAll('.chat-trace-item')].map(item => item.getAttribute('data-trace'))
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
    'chat-timeline',
    'chat-line-user',
  ])
  expect(
    tracesOf(screen.getByTestId('chat-timeline')),
    'the member is a link of the same timeline as the step above him, at his own place',
  ).toEqual(['tool', 'delegation'])

  // the turn ends and its history is read — the moment a live entry of the wrong anchor would fall behind
  // the last answer, since the read brings the two inputs and both answers, and since R-WP-47 the step of
  // the delegation as well: it names the member and stands where the call happened (UC-WP-82)
  chat.setMessages([
    QUESTION,
    { role: 'user', text: 'frag Dev nach einer Frucht' },
    { role: 'tool', text: '', origin: 'Dev' },
    { role: 'user', text: 'und was macht Plan?' },
    TEAM_ANSWER,
    JONS_ANSWER,
  ])
  await chat.push(event('done', { millis: 21 }))
  await flush()

  const entries = [...screen.getByTestId('chat-log').children]
  const at = entries.indexOf(screen.getByTestId('chat-timeline'))
  const ownInputAt = entries.findIndex(line =>
    (line.textContent ?? '').includes('frag Dev nach einer Frucht'),
  )
  const queuedAt = entries.findIndex(line => (line.textContent ?? '').includes('und was macht Plan?'))
  const answerAt = entries.findIndex(line => line.getAttribute('data-testid') === 'chat-line-jon')
  expect(at, 'the timeline of the entry stays between both inputs').toBeGreaterThan(ownInputAt)
  expect(at, 'a read does not push it below the input queued after it').toBeLessThan(queuedAt)
  expect(at, 'and never behind the answer it delegated').toBeLessThan(answerAt)
  expect(tracesOf(screen.getByTestId('chat-timeline')), 'and the member is still the link of it it was').toEqual(
    ['tool', 'delegation'],
  )
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
    screen
      .getAllByTestId('chat-timeline')
      .flatMap(widget => [...widget.querySelectorAll('.chat-trace-item[data-trace="tool"]')])
  const working = () => steps().filter(step => step.hasAttribute('data-working'))

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

  // the end of the answer ends it. The read that follows carries the two steps as lines of the Bestand
  // (R-WP-47), which knows no moment: both stand, named, and neither carries a clock or a duration
  // (UC-WP-72 GIVEN 2 and 5, UC-WP-82)
  chat.setMessages([
    QUESTION,
    { role: 'user', text: 'zähl die Dateien' },
    { role: 'tool', text: 'pom.xml README.md', origin: 'shellRunCommand' },
    { role: 'tool', text: '3 pom.xml', origin: 'shellRunCommand' },
    ANSWER,
  ])
  await chat.push(event('done', { millis: 9 }))
  await flush()
  expect(working(), 'with the end of the answer no step works').toEqual([])
  expect(
    steps().map(step => step.textContent),
    'both steps stand on the read, named by the memory',
  ).toEqual(['shellRunCommandpom.xml README.md', 'shellRunCommand3 pom.xml'])
  for (const step of steps()) {
    expect(step, 'the Bestand knows no moment and no duration — a restored step carries no title').not.toHaveAttribute(
      'title',
    )
  }
})


// UC-WP-68, UC-WP-82
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
  expect(within(screen.getByTestId('chat-timeline')).queryByTestId('chat-diff')).toBeNull()

  // the turn ends and the history is read: the block has no line in that history and keeps its place on
  // the screen, while the step of the same turn is handed back as a line of its own (R-WP-11, R-WP-36,
  // R-WP-47)
  chat.setMessages([
    QUESTION,
    { role: 'tool', text: 'write src/App.java', origin: 'diskWriteFile' },
    ANSWER,
  ])
  await chat.push(event('done', { millis: 9 }))
  await flush()
  // the order of the place: the read hands the step back as a line, and the block of the same turn reads
  // BEHIND it — the diff ends the group that lay before it (R-WP-40, UC-WP-54)
  expect(
    [...screen.getByTestId('chat-log').children].map(line => line.getAttribute('data-testid')),
    'the diff-Block stands under the timeline of its own turn, not above it',
  ).toEqual(['chat-line-user', 'chat-timeline', 'chat-diff', 'chat-line-jon'])
  expect(screen.getByTestId('chat-diff')).toHaveTextContent('+b')
  const step = screen.getByTestId('chat-timeline')
  expect(step).toHaveTextContent('write src/App.java')
  expect(step, 'the read names the tool it carries').toHaveTextContent('diskWriteFile')

  // a dropped connection is the reload's stand-in: what the wire will not send back leaves the screen —
  // the block is of that family, the step is not, since the history of the new connection carries it
  // as a line (R-WP-11, R-WP-36, UC-WP-82)
  await chat.closeStream()
  await waitFor(() => expect(chat.countOf('/events', 'GET')).toBe(2), { timeout: 1500 })
  expect(screen.queryByTestId('chat-diff'), 'a diff is no line of the Bestand').toBeNull()
  expect(screen.getByTestId('chat-timeline')).toHaveTextContent('write src/App.java')
  expect(agentLinesSaying(ANSWER.text)).toHaveLength(1)
})

/**
 * What a read leaves of the wire's order inside ONE widget (R-WP-40, R-WP-11, UC-WP-72 GIVEN 2): the Bestand
 * leads, the rest of the wire follows. A Think that streamed before the call therefore reads BEHIND the step
 * the history hands back — arrival order across the two sources is not rebuildable without inventing an
 * identity neither the line nor the event carries, and every reader has to read the same account (R-WP-25).
 */
// UC-WP-72
// UC-WP-82
test('aThinkStreamedBeforeTheCallStandsBehindTheStepTheHistoryHandsBack', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'zähl die Dateien' } })
  fireEvent.click(screen.getByTestId('chat-send'))
  await chat.push(event('partial', { kind: 'think', text: 'Ich prüfe das Root' }))
  await chat.push(event('tool', { text: 'ls in the checkout' }))

  chat.setMessages([
    QUESTION,
    { role: 'user', text: 'zähl die Dateien' },
    { role: 'tool', text: 'pom.xml README.md', origin: 'shellRunCommand' },
    ANSWER,
  ])
  await chat.push(event('done', { millis: 14 }))
  await flush()

  expect([...screen.getByTestId('chat-log').children].map(line => line.getAttribute('data-testid'))).toEqual([
    'chat-line-user',
    'chat-line-user',
    'chat-timeline',
    'chat-line-jon',
  ])
  const widget = screen.getByTestId('chat-timeline')
  expect(
    [...widget.querySelectorAll('.chat-trace-item')].map(item => item.getAttribute('data-trace')),
    'the step the Bestand carries leads its widget, the Think that only the wire carried follows it',
  ).toEqual(['tool', 'think'])
  expect(widget, 'and it is the Bestand that speaks, not the wire').toHaveTextContent('pom.xml README.md')
  expect(
    [...widget.querySelectorAll('[data-working="true"]')],
    'with the turn ended works nothing, neither the step nor the thought',
  ).toEqual([])
})
