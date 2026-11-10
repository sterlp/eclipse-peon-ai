import { afterEach, beforeEach, expect, test } from 'vitest'
import { render, screen } from '@testing-library/react'

import { ChatLog } from '../../src/chat/chat.log'
import { ChatPage } from '../../src/chat/chat.page'
import { appendLiveProblem, appendLiveTool, type LiveItem } from '../../src/chat/chat.live'
import { content } from '../../src/shared/content'
import { declarations } from '../shared/sheets'
import { project } from '../shared/fixtures'
import {
  ANSWER,
  closeChatStub,
  event,
  flush,
  QUESTION,
  resetChatStub,
  stubChat,
} from './chat.page.stub'

/**
 * The refusal of a tool is its own entry of the timeline — red text, the word „Problem", a red icon — and
 * never a grey step (R-WP-61, UC-WP-98). Its own file because the two possible hosts of these cases stand
 * over the 600-line gate (`chat.timeline.test.tsx`, `chat.trace.page.test.tsx`), and because G3 of the use
 * case asks for the counter-half as well: a step that worked stays exactly as grey as it was.
 *
 * Which red answers `--problem` is the look's and never a test's (jsdom computes no colour), so the colour is
 * proven where it is written: the handle our entry carries, plus the declaration the sheet gives it.
 */

beforeEach(() => resetChatStub())

afterEach(async () => {
  await closeChatStub()
})

/** The refusal as the core words it and the wire hands it: the tool's name, the denial, the path. */
const DENIAL = 'diskWriteFile: Write denied: /tmp/x.txt liegt außerhalb der freigegebenen Wurzeln'

/** Fixed arrival moments, built from LOCAL date parts — `arrivalTime` reads the date's own fields. */
const FIRST = new Date(2026, 10, 6, 9, 5).getTime()
const SECOND = new Date(2026, 10, 6, 9, 7).getTime()
const THIRD = new Date(2026, 10, 6, 9, 9).getTime()

/** The turn a test holds open while its entries arrive — the identity a working entry belongs to. */
const TURN = 1

/** One refusal on the screen: a problem line with the stamp of the agent that tried, nothing more. */
function problemOf(
  origin: string | null,
  at: number | null = FIRST,
  turn: number | null = TURN,
): LiveItem[] {
  return appendLiveProblem([], DENIAL, 1, origin, at, turn)
}

/** One step of a tool run, as the wire hands it: text, the stamp of who worked, the moment it arrived. */
function stepOf(text: string, origin: string | null, at: number, turn: number | null = TURN): LiveItem[] {
  return appendLiveTool([], text, 1, origin, at, turn)
}

/** The entries of one kind that the one timeline of a run draws — asked of our handle, never of a basis one. */
function drawnTrace(kind: 'tool' | 'problem'): Element[] {
  return [...screen.getByTestId('chat-timeline').querySelectorAll(`.chat-trace-item[data-trace="${kind}"]`)]
}

/** The marks the log shows as working, asked of the widget itself (R-WP-40, UC-WP-72). */
function workingMarks(): Element[] {
  return [...screen.getByTestId('chat-log').querySelectorAll('[data-testid="working-indicator"]')]
}

/**
 * A refusal of the wire is an entry of its own (UC-WP-98 GIVEN 1): our handle, the word that says what it is,
 * the red icon of the design basis, and no step of the run beside it. The icon stands in the head of the
 * timeline item and not in its content — the widget paints it there, so that is where the test asks for it.
 */
// UC-WP-98
test('theProblemIsItsOwnRedEntry', () => {
  render(<ChatLog messages={[QUESTION]} outgoing={[]} live={problemOf('Jon')} openTurn={TURN} />)

  const problems = drawnTrace('problem')
  expect(problems).toHaveLength(1)
  expect(drawnTrace('tool'), 'a refusal is no longer a step of the run').toHaveLength(0)

  const entry = problems[0]
  expect(entry).toHaveClass('chat-problem')
  expect(entry.querySelector('.chat-problem-name')?.textContent).toBe(content.chat.problem)
  expect(entry.textContent).toContain(DENIAL)

  const item = entry.closest('.ant-timeline-item')
  expect(item, 'the refusal is a link of the one timeline').not.toBeNull()
  expect(item?.querySelector('.chat-problem-icon'), 'the icon is ours, on the basis widget').not.toBeNull()
  expect(
    entry.querySelector('[data-testid="working-indicator"]'),
    'a refusal never works',
  ).toBeNull()
  expect(entry.getAttribute('data-working'), 'and says so on its own handle').toBeNull()

  // the colour is painted from the token at every handle of the entry — never inherited, since the step's own
  // name sets the trace grey and would swallow a red that only relied on inheritance
  for (const handle of ['.chat-problem', '.chat-problem-name', '.chat-problem-icon']) {
    expect(
      declarations(`.chat-log .chat-line.trace ${handle}`),
      `${handle} reads in the problem's colour`,
    ).toMatch(/color:\s*var\(--problem\)/)
  }
})

/**
 * The stamp of a refusal is the wire's and nothing else (UC-WP-98 GIVEN 2, R-WP-37): the same name in the
 * same idiom as a step's, and no name at all where the core named no producer.
 */
// UC-WP-98
test('aProblemNamesItsAgentOnlyWhenTheWireNamesOne', () => {
  const view = render(<ChatLog messages={[QUESTION]} outgoing={[]} live={problemOf('Dev')} />)
  expect(drawnTrace('problem')[0].querySelector('.chat-step-name')?.textContent).toBe('Dev')

  view.unmount()
  render(<ChatLog messages={[QUESTION]} outgoing={[]} live={problemOf(null)} />)
  const entry = drawnTrace('problem')[0]
  expect(entry.querySelector('.chat-step-name'), 'an unstamped refusal names nobody').toBeNull()
  expect(entry.textContent).toContain(DENIAL)
})

/**
 * The counter-half of the same rule (UC-WP-98 GIVEN 3): a tool that worked stays a grey step — no red class,
 * no red icon, and the mini idiom of its stamp keeps the trace's grey.
 */
// UC-WP-98
test('aToolLineStaysAGreyStep', () => {
  const live = problemOf('Jon', SECOND).concat(stepOf('ls in the checkout', 'Jon', FIRST))
  render(<ChatLog messages={[QUESTION]} outgoing={[]} live={live} openTurn={TURN} />)

  const steps = drawnTrace('tool')
  expect(steps).toHaveLength(1)
  expect(drawnTrace('problem'), 'the step is not a refusal and the refusal not a step').toHaveLength(1)

  const step = steps[0]
  expect(step, 'a step of a run keeps no handle of a refusal').not.toHaveClass('chat-problem')
  expect(step.querySelector('.chat-problem-icon'), 'and paints no red icon').toBeNull()
  expect(step.querySelector('.chat-step-name')?.textContent).toBe('Jon')
  expect(
    declarations('.chat-log .chat-line.trace .chat-step-name'),
    'the stamp of a step keeps the trace grey',
  ).toMatch(/color:\s*var\(--tool\)/)
})

/**
 * The whole path of one refusal at once: the wire's `problem` event, the reducer, the entry (UC-WP-98). An
 * unknown event name is dropped by this client on purpose, so a test on the screen is the only proof that the
 * server's new event is not silently swallowed.
 */
// UC-WP-98
test('aProblemOnTheWireBecomesARedEntry', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  await chat.push(event('problem', { text: DENIAL, origin: 'Jon' }))

  const problems = drawnTrace('problem')
  expect(problems).toHaveLength(1)
  expect(problems[0]).toHaveClass('chat-problem')
  expect(problems[0]).toHaveTextContent(DENIAL)
  expect(problems[0].querySelector('.chat-step-name')?.textContent).toBe('Jon')
  expect(drawnTrace('tool'), 'the refusal never arrives as a step').toHaveLength(0)

  // the stamp rule of a step and of a refusal is one rule: no field, or an empty one, names nobody
  await chat.push(event('problem', { text: 'a second refusal', origin: '' }))
  const entries = drawnTrace('problem')
  expect(entries).toHaveLength(2)
  expect(entries[1].querySelector('.chat-step-name'), 'an empty stamp names nobody').toBeNull()
})

/**
 * A refusal is an end, not a step in progress (UC-WP-98, decision D11): the step above it worked until the
 * tool refused, and the refusal takes the indicator away without claiming the work itself. The next step of
 * the turn takes it back.
 */
// UC-WP-98
test('theProblemEntryNeverWorksAndEndsTheStepAbove', () => {
  const refused = appendLiveProblem(stepOf('ls in the checkout', 'Jon', FIRST), DENIAL, 1, 'Jon', SECOND)
  const view = render(<ChatLog messages={[QUESTION]} outgoing={[]} live={refused} openTurn={TURN} />)

  expect(screen.getByTestId('chat-log').querySelectorAll('[data-working="true"]')).toHaveLength(0)
  expect(workingMarks(), 'nothing works behind a refusal').toHaveLength(0)

  view.unmount()
  render(
    <ChatLog
      messages={[QUESTION]}
      outgoing={[]}
      live={appendLiveTool(refused, 'wc -l pom.xml', 1, 'Jon', THIRD, TURN)}
      openTurn={TURN}
    />,
  )
  const working = [...screen.getByTestId('chat-log').querySelectorAll('[data-working="true"]')]
  expect(working, 'the step behind the refusal works again').toHaveLength(1)
  expect(working[0].getAttribute('data-trace')).toBe('tool')
  expect(working[0].textContent).toContain('wc -l pom.xml')
})

/**
 * The same account, once and in one colour (UC-WP-98, decision D10, R-WP-61 last sentence): the Bestand of a
 * turn carries a refusal as a tool step, so the red live entry gives way to the read that `done` triggers
 * exactly like a live step does — the same sentence stays standing once, grey, and not twice.
 */
// UC-WP-98
test('theProblemYieldsToTheRestoredStep', async () => {
  const chat = stubChat({ messages: [QUESTION] })
  render(<ChatPage project={project()} />)
  await flush()

  await chat.push(event('problem', { text: DENIAL, origin: 'diskWriteFile' }))
  expect(drawnTrace('problem'), 'while the turn runs the refusal is red').toHaveLength(1)

  // the turn ends and the read answers with the turn's own account: the refusal is in it, as a step
  chat.setMessages([
    QUESTION,
    { role: 'user', text: 'schreib die Datei' },
    { role: 'tool', text: DENIAL, origin: 'diskWriteFile' },
    ANSWER,
  ])
  await chat.push(event('done', { millis: 7 }))

  const steps = drawnTrace('tool')
  expect(steps, 'the Bestand leads the refusal once').toHaveLength(1)
  expect(steps[0]).toHaveTextContent(DENIAL)
  expect(steps[0], 'and the red live entry yielded to it').not.toHaveClass('chat-problem')
  expect(drawnTrace('problem'), 'no red entry is left standing beside the Bestand').toHaveLength(0)
  expect(screen.getByTestId('chat-log').querySelectorAll('.chat-problem')).toHaveLength(0)
})
