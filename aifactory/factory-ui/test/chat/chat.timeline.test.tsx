import { render, screen } from '@testing-library/react'

import { ChatLog } from '../../src/chat/chat.log'
import {
  appendLiveDelegation,
  appendLiveDiff,
  appendLiveText,
  appendLiveTool,
  arrivalTime,
  endLiveDelegation,
  livePreview,
  MAX_TOOL_LINES,
  type LiveItem,
} from '../../src/chat/chat.live'
import { appSheet, declarations } from '../shared/sheets'
import { content } from '../../src/shared/content'
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
  turn?: number | null
}

/** The turn a test holds open while its steps arrive — the identity a working step belongs to (R-WP-40). */
const TURN = 1

/** The steps of one run in arrival order, anchored behind the one question that stands in the history. */
function stepsOf(...steps: Step[]): LiveItem[] {
  let items: LiveItem[] = []
  for (const step of steps) {
    // a step names its turn only to say it names none — otherwise it belongs to the turn the test holds open
    const turn = step.turn === undefined ? TURN : step.turn
    items = appendLiveTool(items, step.text, 1, step.origin ?? null, step.at ?? null, turn)
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
  const view = render(
    <ChatLog messages={[QUESTION]} outgoing={[]} live={steps} pending={false} openTurn={TURN} />,
  )

  const running = drawnSteps()
  expect(running).toHaveLength(2)
  expect(running[1].querySelector('.chat-step-running'), 'the step being worked on blinks').not.toBeNull()
  expect(running[0].querySelector('.chat-step-running'), 'a finished step does not').toBeNull()
  // the clock of a step comes once it is over and not while it works (R-WP-40, UC-WP-72 GIVEN 2)
  expect(running[0].getAttribute('title'), 'a finished step already carries its clock').toBe(arrivalTime(FIRST))
  expect(running[1].getAttribute('title'), 'a working step carries no clock yet').toBeNull()

  view.unmount()
  render(<ChatLog messages={[QUESTION, ANSWER]} outgoing={[]} live={steps} pending={false} />)
  const ended = drawnSteps()
  expect(ended).toHaveLength(2)
  expect(
    [...screen.getByTestId('chat-tool-line').querySelectorAll('.chat-step-running')],
    'with the turn there is no working step left',
  ).toEqual([])
  expect(ended[1].getAttribute('title'), 'the step that worked stands with its arrival clock').toBe(
    arrivalTime(SECOND),
  )

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
  // the reloaded log is not empty: it draws the finished lines with the author the history named, so the
  // empty queries below say something about a log that has content in it (UC-WP-72 GIVEN 4)
  const view = render(
    <ChatLog
      messages={[QUESTION, ANSWER, { role: 'jon', text: 'Eine Banane.', origin: 'Dev' }]}
      outgoing={[]}
      live={[]}
      pending={false}
    />,
  )

  const log = screen.getByTestId('chat-log')
  expect(log.textContent).toContain(ANSWER.text)
  expect(
    [...log.querySelectorAll('[data-testid="chat-line-author"]')].map(line => line.textContent),
  ).toEqual(['Paul', 'Dev'])
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
      openTurn={TURN}
    />,
  )
  const step = drawnSteps()[0]
  expect(step.querySelector('.chat-step-name')?.textContent).toBe('Jon')
  expect(step.getAttribute('title'), 'a name on the wire is no time').toBeNull()
})

/**
 * A step works for the turn that reported it and for no other (R-WP-40, UC-WP-72 GIVEN 2): the step of a turn
 * that has ended keeps its place on the screen without an indicator, and a turn that is open without having
 * reported a step of its own has no working step either — the screen paints no indicator it did not see.
 */
// UC-WP-72
test('aStepNeverWorksForATurnThatDidNotReportIt', () => {
  const steps = stepsOf({ text: 'ls in the checkout', origin: 'Jon', at: FIRST })
  const { rerender } = render(
    <ChatLog messages={[QUESTION]} outgoing={[]} live={steps} pending={false} openTurn={TURN} />,
  )
  expect(drawnSteps()[0].querySelector('.chat-step-running')).not.toBeNull()

  // the turn ended and a new one is open that has reported nothing yet: the step stands, without working
  rerender(
    <ChatLog
      messages={[QUESTION, ANSWER]}
      outgoing={[]}
      live={steps}
      pending={false}
      openTurn={TURN + 1}
    />,
  )
  expect(
    [...screen.getByTestId('chat-tool-line').querySelectorAll('.chat-step-running')],
    'a step of a finished turn never works for the next one',
  ).toEqual([])
  expect(drawnSteps()[0].getAttribute('title'), 'it keeps the clock of its arrival').toBe(arrivalTime(FIRST))

  // the new turn's answer is already streaming and its own first step has not come: still nothing works
  rerender(
    <ChatLog
      messages={[QUESTION, ANSWER]}
      outgoing={[]}
      live={appendLiveText(steps, 'Im Root liegen ', 2)}
      pending={false}
      openTurn={TURN + 1}
    />,
  )
  expect(
    [...screen.getByTestId('chat-tool-line').querySelectorAll('.chat-step-running')],
    'an open turn without a step of its own has no working step',
  ).toEqual([])

  // and a step the view cannot place in a turn never works — neither while a turn is open nor while none is
  rerender(
    <ChatLog
      messages={[QUESTION]}
      outgoing={[]}
      live={stepsOf({ text: 'ls in the checkout', at: FIRST, turn: null })}
      pending={false}
      openTurn={TURN}
    />,
  )
  expect(
    [...screen.getByTestId('chat-tool-line').querySelectorAll('.chat-step-running')],
    'a step of no turn never works',
  ).toEqual([])
  rerender(
    <ChatLog
      messages={[QUESTION]}
      outgoing={[]}
      live={stepsOf({ text: 'ls in the checkout', at: FIRST, turn: null })}
      pending={false}
      openTurn={null}
    />,
  )
  expect(
    [...screen.getByTestId('chat-tool-line').querySelectorAll('.chat-step-running')],
    'and no step works when no turn is open',
  ).toEqual([])
})

/**
 * The indicator of a step ends with the next step or with the end of the answer — not with a word of that
 * answer (R-WP-40, UC-WP-72 GIVEN 2): the core reports no end of a single tool, and neither the text a turn
 * is writing nor the file it changed is a step of the timeline.
 */
// UC-WP-72
test('theIndicatorOfAStepEndsWithTheNextStepAndNotWithTheAnswer', () => {
  const streaming = appendLiveText(
    stepsOf({ text: 'ls in the checkout', origin: 'Jon', at: FIRST }),
    'Im Root liegen ',
    1,
  )
  /** The step lines of every box the run drew, in the order the screen shows them. */
  const allSteps = () =>
    screen.getAllByTestId('chat-tool-line').flatMap(box => [...box.querySelectorAll('.chat-tool-line')])
  const { rerender } = render(
    <ChatLog messages={[QUESTION]} outgoing={[]} live={streaming} pending={false} openTurn={TURN} />,
  )
  expect(drawnSteps()).toHaveLength(1)
  expect(drawnSteps()[0].querySelector('.chat-step-running'), 'the answer of its turn is no step').not.toBeNull()
  expect(drawnSteps()[0].getAttribute('title'), 'while it works there is no clock yet').toBeNull()

  // a file the turn changed is a block of its own and no step either (R-WP-36)
  const withFile = appendLiveDiff(streaming, 'pom.xml', '--- a/pom.xml\n+++ b/pom.xml\n', 1)
  rerender(<ChatLog messages={[QUESTION]} outgoing={[]} live={withFile} pending={false} openTurn={TURN} />)
  expect(
    drawnSteps()[0].querySelector('.chat-step-running'),
    'a file the turn changed is no step of the timeline',
  ).not.toBeNull()

  // the next step takes the indicator, and the step behind it keeps its clock
  const nextStep = appendLiveTool(withFile, 'wc -l pom.xml', 1, 'Jon', SECOND, TURN)
  rerender(<ChatLog messages={[QUESTION]} outgoing={[]} live={nextStep} pending={false} openTurn={TURN} />)
  const steps = allSteps()
  expect(steps, 'a box is one place of the trace, so the run draws one box per place').toHaveLength(2)
  expect(steps[0].querySelector('.chat-step-running'), 'the next step took the indicator').toBeNull()
  expect(steps[0].getAttribute('title')).toBe(arrivalTime(FIRST))
  expect(steps[1].querySelector('.chat-step-running')).not.toBeNull()
  expect(steps[1].getAttribute('title'), 'the step that works carries no clock yet').toBeNull()

  // the end of the answer ends it, whatever stood behind the step
  rerender(
    <ChatLog messages={[QUESTION, ANSWER]} outgoing={[]} live={nextStep} pending={false} openTurn={null} />,
  )
  expect(
    allSteps().filter(step => step.querySelector('.chat-step-running') !== null),
    'with the end of the answer no step works',
  ).toEqual([])
  expect(allSteps()[1].getAttribute('title'), 'and the step that worked keeps its clock').toBe(
    arrivalTime(SECOND),
  )
})

/**
 * A delegation is a link of the timeline of its own and works for its turn alone (R-WP-40, UC-WP-72
 * GIVEN 3): the entry names the member the order went to — the wire carries no task, so nothing here could
 * name one — and the word that he works ends with the entry's **own** end event, which is also what brings
 * its arrival clock. An end that finds no open entry of that member leaves the list alone, and an entry of
 * a turn this view does not hold open paints no indicator. The step above the entry works no longer: the
 * delegation is the last step of its turn, so one turn paints exactly one indicator.
 */
// UC-WP-72
test('theDelegationEntryNamesTheMemberAndStopsAtItsEnd', () => {
  const entry = () => screen.getByTestId('chat-delegation')
  const openDelegation = appendLiveDelegation(
    stepsOf({ text: 'Dev start: nenne eine Frucht', origin: 'Jon', at: FIRST }),
    'Dev',
    1,
    SECOND,
    TURN,
  )
  const { rerender } = render(
    <ChatLog messages={[QUESTION]} outgoing={[]} live={openDelegation} pending={false} openTurn={TURN} />,
  )

  expect(entry().querySelector('.chat-delegation-name')?.textContent).toBe('Dev')
  expect(entry().textContent, 'while his turn is open, the member works').toContain(content.chat.working)
  expect(entry().getAttribute('title'), 'a working entry carries no clock yet').toBeNull()
  expect(
    drawnSteps()[0].querySelector('.chat-step-running'),
    'the delegation is the last step of the turn, so the step above it works no longer',
  ).toBeNull()

  // the entry's own end: the entry stands, the word is gone, the clock of its arrival appears
  const ended = endLiveDelegation(openDelegation, 'Dev', TURN)
  rerender(<ChatLog messages={[QUESTION]} outgoing={[]} live={ended} pending={false} openTurn={TURN} />)
  expect(entry().textContent, 'the entry of a finished delegation stays where it happened').toContain('Dev')
  expect(entry().textContent, 'the word ends with the end event and not with a duration').not.toContain(
    content.chat.working,
  )
  expect(entry().getAttribute('title'), 'a closed entry carries its arrival clock').toMatch(/^\d{2}:\d{2}$/)

  // an end that matches nothing: the same list, so no entry of nothing and no line beside it
  expect(endLiveDelegation(ended, 'Plan', TURN), 'no open entry of that member, so nothing is added').toBe(
    ended,
  )
  expect(endLiveDelegation(ended, 'Dev', TURN), 'a closed entry is not closed a second time').toBe(ended)
  rerender(<ChatLog messages={[QUESTION]} outgoing={[]} live={ended} pending={false} openTurn={TURN} />)
  expect(screen.getAllByTestId('chat-delegation'), 'ends that match nothing draw no second line').toHaveLength(
    1,
  )

  // the open entry of a turn this view does not hold open paints nothing — identity decides, not the flag
  rerender(
    <ChatLog messages={[QUESTION]} outgoing={[]} live={openDelegation} pending={false} openTurn={TURN + 1} />,
  )
  expect(entry().textContent, 'an entry of another turn never works').not.toContain(content.chat.working)
  expect(endLiveDelegation(openDelegation, 'Dev', TURN + 1), 'an end of another turn closes nothing').toBe(
    openDelegation,
  )

  // quiet by decision: the member in small capitals in the trace grey, on no surface; the word blinks like
  // the waiting caret and reduced motion stops it in a block of its own
  const paintedName = declarations('.chat-log .chat-line.delegation .chat-delegation-name')
  expect(paintedName, 'the member is a small capital above his entry').toContain('text-transform: uppercase')
  expect(paintedName, 'the member stays the trace grey').toContain('var(--tool)')
  expect(paintedName, 'an entry is no card').not.toMatch(/background|border/)
  expect(
    declarations('.chat-log .chat-line.delegation .chat-delegation-working'),
    'the word is the blink of the waiting bar',
  ).toContain('animation: chat-waiting-blink')
  expect(appSheet(), 'and reduced motion stops it in its own block').toMatch(
    /@media \(prefers-reduced-motion: reduce\) \{\s*\.chat-log \.chat-line\.delegation \.chat-delegation-working \{\s*animation: none/,
  )
})

/**
 * „Working" is the LAST step of a running turn, and that is one entry of the timeline whatever it is (R-WP-40,
 * UC-WP-72 GIVEN 2 and 3): one decider answers for the whole log, so no order of the wire leaves two entries
 * blinking — the step above an open entry stops with it, a step arriving behind an open entry takes the
 * indicator over, and an entry the wire closed ends without handing it back.
 */
// UC-WP-72
test('onlyOneEntryOfTheTurnWorksAtATime', () => {
  const indicators = () => [
    ...screen.getByTestId('chat-log').querySelectorAll('.chat-step-running, .chat-delegation-working'),
  ]
  const delegating = appendLiveDelegation(
    stepsOf({ text: 'Dev start: nenne eine Frucht', origin: 'Jon', at: FIRST }),
    'Dev',
    1,
    SECOND,
    TURN,
  )
  const { rerender } = render(
    <ChatLog messages={[QUESTION]} outgoing={[]} live={delegating} pending={false} openTurn={TURN} />,
  )

  expect(indicators(), 'the entry the wire opened last works, and only it').toHaveLength(1)
  expect(indicators()[0].className).toContain('chat-delegation-working')
  expect(
    drawnSteps()[0].querySelector('.chat-step-running'),
    'the step above the entry stops with it',
  ).toBeNull()

  // a step that arrives behind the open entry is the last step of the turn from then on — the core reports
  // the two one after the other, so the rule has to hold in either order
  const stepBehind = appendLiveTool(delegating, 'wc -l README.md', 1, 'Jon', SECOND, TURN)
  rerender(
    <ChatLog messages={[QUESTION]} outgoing={[]} live={stepBehind} pending={false} openTurn={TURN} />,
  )
  expect(indicators(), 'still one entry works, now the step').toHaveLength(1)
  expect(indicators()[0].className).toContain('chat-step-running')
  expect(
    screen.getByTestId('chat-delegation').textContent,
    'the entry the step left behind works no longer',
  ).not.toContain(content.chat.working)

  // the entry's own end ends it: neither the entry nor the step above it works
  rerender(
    <ChatLog
      messages={[QUESTION]}
      outgoing={[]}
      live={endLiveDelegation(delegating, 'Dev', TURN)}
      pending={false}
      openTurn={TURN}
    />,
  )
  expect(
    indicators(),
    'an entry the wire closed works no longer, and neither does the step above it',
  ).toEqual([])
})

/**
 * The waiting bar names the last thing the wire reported (R-WP-31): after a delegation that is the
 * **member** — a diff is previewed by its file, and neither of the two is a body line of text
 * (R-WP-31, UC-WP-72 GIVEN 3).
 */
// UC-WP-72
test('theDelegationPreviewNamesTheMember', () => {
  const withFile = appendLiveDiff([], 'pom.xml', '--- a/pom.xml\n+++ b/pom.xml\n@@ -1,1 +1,2 @@\n a\n+b\n', 1)
  expect(livePreview(withFile), 'a diff is previewed by the file it changed').toBe('pom.xml')
  expect(
    livePreview(appendLiveDelegation(withFile, 'Dev', 1, FIRST, TURN)),
    'a delegation is previewed by the member it went to',
  ).toBe('Dev')
})
