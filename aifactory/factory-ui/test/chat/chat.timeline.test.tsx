import { render, screen } from '@testing-library/react'

import { ChatLog } from '../../src/chat/chat.log'
import { appendLiveTool, arrivalTime, MAX_TOOL_LINES, type LiveItem } from '../../src/chat/chat.live'
import { appSheet, declarations } from '../shared/sheets'
import type { ChatMessage } from '../../src/chat/chat.client'

const QUESTION: ChatMessage = { role: 'user', text: 'zähl die Dateien', origin: 'Paul' }
const ANSWER: ChatMessage = { role: 'jon', text: 'Zwei: pom.xml und README.md' }

/**
 * Fixed arrival moments, built from LOCAL date parts: `arrivalTime` reads the date's own fields, so the clock
 * a test expects is the clock the browser would show — on every machine, in every zone, without a locale.
 */
const FIRST = new Date(2026, 10, 6, 9, 5).getTime()
const SECOND = new Date(2026, 10, 6, 9, 7).getTime()

/** One step of a tool run, as the wire hands it: text, the stamp of who worked, the moment it arrived. */
interface Step {
  text: string
  origin?: string | null
  at?: number | null
}

/** The steps of one run in arrival order, anchored behind the one question that stands in the history. */
function stepsOf(...steps: Step[]): LiveItem[] {
  let items: LiveItem[] = []
  for (const step of steps) {
    items = appendLiveTool(items, step.text, 1, step.origin ?? null, step.at ?? null)
  }
  return items
}

/** The step lines of the one box a run draws, in the order the screen shows them. */
function drawnSteps(): Element[] {
  return [...screen.getByTestId('chat-tool-line').querySelectorAll('.chat-tool-line')]
}

/**
 * A step names the agent the core stamped on it, and only that (R-WP-40, UC-WP-72 GIVEN 1): the name is the
 * wire's, never one the screen lends to it — a step the wire left unstamped stands without a name.
 */
// UC-WP-72
test('aStepNamesItsAgentOnlyWhenTheWireNamesOne', () => {
  const view = render(
    <ChatLog
      messages={[QUESTION]}
      outgoing={[]}
      live={stepsOf({ text: 'ls in the checkout', origin: 'Jon', at: FIRST })}
      pending={false}
    />,
  )

  expect(drawnSteps()[0].querySelector('.chat-step-name')?.textContent).toBe('Jon')

  view.unmount()
  render(
    <ChatLog
      messages={[QUESTION]}
      outgoing={[]}
      live={stepsOf({ text: 'file updated, diff skipped — binary content', at: FIRST })}
      pending={false}
    />,
  )
  expect(drawnSteps()[0].textContent).toContain('binary content')
  expect(drawnSteps()[0].querySelector('.chat-step-name'), 'an unstamped step names nobody').toBeNull()

  // quiet by decision: capitals in the trace grey, above the report, on no surface and in no frame (R-WP-29)
  const painted = declarations('.chat-log .chat-line.tool .chat-step-name')
  expect(painted, 'the stamp is set as a small capital').toContain('text-transform: uppercase')
  expect(painted, 'the stamp stays the trace grey').toContain('var(--tool)')
  expect(painted, 'a stamp is no card').not.toMatch(/background|border/)
})

/**
 * „Working" is one step of a turn that is still open (R-WP-40, UC-WP-72 GIVEN 2): the indicator sits on the
 * last step alone, and it is gone as soon as no turn is open — the core reports no end of a single tool, so
 * nothing else may hold a step open.
 */
// UC-WP-72
test('theLastStepOfARunningTurnCarriesTheIndicator', () => {
  const steps = stepsOf(
    { text: 'ls in the checkout', origin: 'Jon', at: FIRST },
    { text: 'wc -l pom.xml', origin: 'Jon', at: SECOND },
  )
  const view = render(<ChatLog messages={[QUESTION]} outgoing={[]} live={steps} pending={false} running />)

  const running = drawnSteps()
  expect(running).toHaveLength(2)
  expect(running[1].querySelector('.chat-step-running'), 'the step being worked on blinks').not.toBeNull()
  expect(running[0].querySelector('.chat-step-running'), 'a finished step does not').toBeNull()

  view.unmount()
  render(<ChatLog messages={[QUESTION, ANSWER]} outgoing={[]} live={steps} pending={false} />)
  const ended = drawnSteps()
  expect(ended).toHaveLength(2)
  expect(
    [...screen.getByTestId('chat-tool-line').querySelectorAll('.chat-step-running')],
    'with the turn there is no working step left',
  ).toEqual([])

  const painted = declarations('.chat-log .chat-line.tool .chat-step-running')
  expect(painted, 'the indicator is the blink of the waiting bar').toContain('animation: chat-waiting-blink')
  expect(appSheet(), 'and reduced motion stops it in its own block').toMatch(
    /@media \(prefers-reduced-motion: reduce\) \{\s*\.chat-log \.chat-line\.tool \.chat-step-running \{\s*animation: none/,
  )
})

/**
 * A finished step carries the clock of its arrival and nothing more (R-WP-40, UC-WP-72 GIVEN 2): a duration
 * belongs to the turn alone, so a step's title is `HH:mm` — or, where the browser received no moment, there
 * is no title at all.
 */
// UC-WP-72
test('aFinishedStepCarriesItsArrivalClockAndNoDuration', () => {
  render(
    <ChatLog
      messages={[QUESTION]}
      outgoing={[]}
      live={stepsOf(
        { text: 'ls in the checkout', at: FIRST },
        { text: 'wc -l pom.xml', at: SECOND },
        { text: 'a line that arrived without a moment' },
      )}
      pending={false}
    />,
  )

  const steps = drawnSteps()
  expect(arrivalTime(FIRST), 'the clock is the local hour and minute').toBe('09:05')
  expect(steps[0].getAttribute('title')).toBe(arrivalTime(FIRST))
  expect(steps[1].getAttribute('title')).toBe(arrivalTime(SECOND))
  expect(steps[1].getAttribute('title'), 'a step never carries a duration').toMatch(/^\d{2}:\d{2}$/)
  expect(steps[2].getAttribute('title'), 'without a moment there is nothing to title').toBeNull()
})

/**
 * The cap keeps a run short (R-WP-28) and every step keeps the moment it arrived with (UC-WP-72 GIVEN 5):
 * trimming the list must not stamp a fresh time onto what stayed on the screen.
 */
// UC-WP-72
test('theCapKeepsTheArrivalTimesOfWhatStays', () => {
  const total = MAX_TOOL_LINES + 2
  const arrivals = Array.from({ length: total }, (_, index) => FIRST + index * 60_000)
  render(
    <ChatLog
      messages={[QUESTION]}
      outgoing={[]}
      live={stepsOf(
        ...arrivals.map((at, index) => ({ text: `step ${index + 1}`, at })),
      )}
      pending={false}
    />,
  )

  const steps = drawnSteps()
  expect(steps, 'the box keeps its tail of the run').toHaveLength(MAX_TOOL_LINES)
  expect(steps[0].textContent).toBe(`step ${total - MAX_TOOL_LINES + 1}`)
  const titles = steps.map(step => step.getAttribute('title'))
  expect(titles, 'what stayed shows the moment it arrived, not the moment of the cut').toEqual(
    arrivals.slice(-MAX_TOOL_LINES).map(at => arrivalTime(at)),
  )
})

/**
 * A reload knows only what the memory holds (R-WP-11, UC-WP-72 GIVEN 4): the trace exists on the wire only,
 * so the reloaded log draws no step, no working indicator and no clock. A step whose arrival the browser
 * never received stays without a title — the screen invents no time to look complete.
 */
// UC-WP-72
test('aReloadedLogInventsNoTimes', () => {
  const view = render(<ChatLog messages={[QUESTION, ANSWER]} outgoing={[]} live={[]} pending={false} />)

  const log = screen.getByTestId('chat-log')
  expect(log.textContent).toContain(ANSWER.text)
  expect(log.querySelector('[title]'), 'nothing of a finished turn is titled by a time').toBeNull()
  expect([...log.querySelectorAll('.chat-step-running')], 'no step works after a reload').toEqual([])
  expect([...log.querySelectorAll('.chat-step-name')], 'and none names an agent the wire did not name').toEqual(
    [],
  )
  expect(log.textContent, 'no clock appears anywhere in a reloaded log').not.toMatch(/\d{2}:\d{2}/)

  view.unmount()
  render(
    <ChatLog
      messages={[QUESTION]}
      outgoing={[]}
      live={stepsOf({ text: 'ls in the checkout', origin: 'Jon' })}
      pending={false}
      running
    />,
  )
  const step = drawnSteps()[0]
  expect(step.querySelector('.chat-step-name')?.textContent).toBe('Jon')
  expect(step.getAttribute('title'), 'a name on the wire is no time').toBeNull()
})
