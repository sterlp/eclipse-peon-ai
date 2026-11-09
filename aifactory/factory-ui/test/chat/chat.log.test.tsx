import { render, screen } from '@testing-library/react'

import { ChatLog } from '../../src/chat/chat.log'
import { appendLiveError, appendLiveTool } from '../../src/chat/chat.live'
import { separatorsOf } from '../shared/log.parts'
import type { ChatMessage } from '../../src/chat/chat.client'

const QUESTION: ChatMessage = { role: 'user', text: 'wo liegen die Dateien?', origin: 'Paul' }
/** The wire text of a failed turn — the server's reason, which the log shows as it is (R-WP-27). */
const FAILURE: ChatMessage = { role: 'error', text: 'turn needs a model — set model in the factory config' }
/** The input the trace belongs to, read back from the history — the anchor of the trace counts it (D-A). */
const SENTENCE: ChatMessage = { role: 'user', text: 'zähl die Dateien', origin: 'Paul' }
const ANSWER: ChatMessage = { role: 'jon', text: 'Zwei: pom.xml und README.md' }
/** A step of the trace as the history hands it back: the tool's name in `origin`, its result as the excerpt (R-WP-47). */
const STEP_LS: ChatMessage = { role: 'tool', text: 'pom.xml README.md', origin: 'shellRunCommand' }
const STEP_WC: ChatMessage = { role: 'tool', text: '3 pom.xml', origin: 'shellRunCommand' }

/**
 * A change of voice draws no line of its own (R-WP-29): the two rows are separated by the form each voice is
 * painted in, and the failure keeps the colour of the problem — never by a rule of the log between them. The
 * sheet's own half of that prohibition is `noAnswerLineIsCentredOrRightAligned`.
 */
// UC-WP-64
// UC-WP-55
// R-WP-29
test('roleChangeDrawsNoRule', () => {
  render(<ChatLog messages={[QUESTION, FAILURE]} outgoing={[]} live={[]} />)

  const log = screen.getByTestId('chat-log')
  expect(separatorsOf(log), 'no line of the log separates two voices').toEqual([])
  // the row that opens the new voice carries no line of its own either
  expect([...screen.getByTestId('chat-line-error').children].some(child => child.tagName === 'HR')).toBe(false)
  expect(screen.getByTestId('chat-line-error').className).toContain('error')
})

/**
 * A line the model drew is content of its answer, and the log has no separator to confuse it with
 * (R-WP-29, UC-WP-55 GIVEN 3): the answer's `---` stands inside the answer, so the separators of the
 * **whole** log are empty while the screen still shows one line. `separatorsOf` is the one definition of
 * which is which, so a proof can neither count a model's line as ours nor overlook one of ours.
 */
// UC-WP-55
// R-WP-29
test('theLogNeverCountsAnAnswersRuleAsItsSeparator', () => {
  const separating: ChatMessage = { role: 'jon', text: 'zuerst\n\n---\n\nzuletzt' }
  render(<ChatLog messages={[QUESTION, separating, SENTENCE]} outgoing={[]} live={[]} />)

  const log = screen.getByTestId('chat-log')
  expect(separatorsOf(log), 'the answer is the only line on the screen, and it is no separator').toEqual([])

  const drawn = log.querySelectorAll('hr')
  expect(drawn, 'the answer does draw its line').toHaveLength(1)
  expect(drawn[0].closest('[data-testid="chat-markdown"]'), 'inside the answer, not beside it').not.toBeNull()
})

/** The other half of the same rule: the failure shows the moment the stream reports it, in the log. */
// UC-WP-64
test('aLiveFailureIsALineOfTheHistory', () => {
  render(
    <ChatLog messages={[QUESTION]} outgoing={[]} live={appendLiveError([], 'no model configured', 1)} />,
  )

  const failure = screen.getByTestId('chat-line-error')
  expect(failure).toHaveTextContent('no model configured')
  expect(screen.queryByTestId('chat-live')).toBeNull()
})

/**
 * The trace of a finished turn keeps its place in the history that arrived with `done`: the anchor counts
 * the rows of its own moment — the question plus the input that was still pending then — and the read has
 * made that input the history line above it. So the box stands between the input and the answer, which is
 * what the page's own read cannot show (D-A, UC-WP-54).
 */
// UC-WP-54
test('aLiveTraceStaysBetweenItsInputAndTheAnswer', () => {
  render(
    <ChatLog
      messages={[QUESTION, SENTENCE, ANSWER]}
      outgoing={[]}
      live={appendLiveTool(appendLiveTool([], 'ls in the checkout', 2), 'wc -l', 2)}
    />,
  )

  const shape = [...screen.getByTestId('chat-log').children].map(line =>
    line.getAttribute('data-testid'),
  )
  expect(shape).toEqual(['chat-line-user', 'chat-line-user', 'chat-timeline', 'chat-line-jon'])
  // one timeline for the run, and both of its entries
  expect([
    ...screen.getByTestId('chat-timeline').querySelectorAll('.chat-trace-item'),
  ]).toHaveLength(2)
})

/**
 * The same account, read back instead of streamed (R-WP-47, UC-WP-82): the steps the history carries are the
 * trace of their place, so consecutive steps draw ONE timeline at the place of the first, between the input
 * they belong to and the answer that followed them — and the voice between two steps ends the group, which
 * is the same rule R-WP-40 states for a live run. A step is never a line of the conversation: the log has
 * exactly one drawing path for it.
 */
// UC-WP-82
// UC-WP-72
test('restoredToolLinesStandAsOneTimelineBetweenTheirInputAndTheAnswer', () => {
  const shape = () =>
    [...screen.getByTestId('chat-log').children].map(line => line.getAttribute('data-testid'))

  const view = render(
    <ChatLog messages={[QUESTION, SENTENCE, STEP_LS, STEP_WC, ANSWER]} outgoing={[]} live={[]} />,
  )

  expect(shape(), 'one group of steps is one widget, where the first of them stands').toEqual([
    'chat-line-user',
    'chat-line-user',
    'chat-timeline',
    'chat-line-jon',
  ])
  const widget = screen.getByTestId('chat-timeline')
  expect([...widget.querySelectorAll('.chat-trace-item')], 'both steps are links of it').toHaveLength(2)
  expect(widget.textContent).toContain('pom.xml README.md')
  expect(widget.textContent).toContain('3 pom.xml')
  expect(
    screen.queryAllByTestId('chat-line-tool'),
    'a step is no voice, so the log draws no line of one',
  ).toEqual([])

  // a voice between two steps ends the account of one place and opens the next (R-WP-40)
  view.rerender(
    <ChatLog messages={[QUESTION, SENTENCE, STEP_LS, ANSWER, STEP_WC]} outgoing={[]} live={[]} />,
  )
  expect(shape(), 'an answer ends the group, so the step behind it is a widget of its own').toEqual([
    'chat-line-user',
    'chat-line-user',
    'chat-timeline',
    'chat-line-jon',
    'chat-timeline',
  ])
})

/**
 * UC-WP-82 GIVEN 3: a conversation the model answered without a tool gains no step and no placeholder —
 * the log of a chat with no tool activity is the conversation and nothing else.
 */
// UC-WP-82
test('aConversationWithoutStepsDrawsNoTimeline', () => {
  render(<ChatLog messages={[QUESTION, ANSWER]} outgoing={[]} live={[]} />)

  expect(screen.queryByTestId('chat-timeline'), 'no step, no widget').toBeNull()
  expect(
    [...screen.getByTestId('chat-log').children].map(line => line.getAttribute('data-testid')),
  ).toEqual(['chat-line-user', 'chat-line-jon'])
})

/**
 * Only answers are Markdown (R-WP-28, UC-WP-73): the three other kinds of line keep what was typed — a
 * person's input quoting a command, the server's reason for a failed turn, a tool's report — while the same
 * characters in an answer become that answer's formatting. Both forms of a failure are read: the line the
 * history carries and the one the stream brings.
 */
// UC-WP-73
test('onlyAnswersAreMarkdown', () => {
  const quoted: ChatMessage = { role: 'user', text: 'mach das **fett** und ~~weg~~' }
  const marked: ChatMessage = { role: 'jon', text: 'die **Datei** ~~alt~~ ist gemeint' }
  const failure: ChatMessage = { role: 'error', text: 'model **nicht** erreichbar ~~heute~~' }
  render(
    <ChatLog
      messages={[quoted, marked, failure]}
      outgoing={[]}
      live={appendLiveError(appendLiveTool([], 'ls **fett**', 3), 'model **nicht** erreichbar', 3)}
    />,
  )

  const wordLiteral = [
    screen.getByTestId('chat-line-user'),
    ...screen.getAllByTestId('chat-line-error'),
    screen.getByTestId('chat-timeline'),
  ]
  expect(wordLiteral, 'input, both failures and the trace are drawn').toHaveLength(4)
  for (const line of wordLiteral) {
    const name = line.getAttribute('data-testid')
    expect(line.querySelector('strong, del, br, table'), `${name} stays its wording`).toBeNull()
  }
  expect(wordLiteral[0].textContent).toContain('**fett**')
  expect(wordLiteral[0].textContent).toContain('~~weg~~')
  expect(wordLiteral[1].textContent).toContain('**nicht**')
  expect(wordLiteral[1].textContent).toContain('~~heute~~')
  expect(wordLiteral[2].textContent).toContain('**nicht**')
  expect(wordLiteral[3].textContent).toContain('ls **fett**')

  // the answer beside them is the line that does draw — same characters, formatting here
  const answer = screen.getByTestId('chat-line-jon')
  expect(answer.querySelector('[data-testid="chat-markdown"]')).not.toBeNull()
  expect(answer.querySelector('strong')?.textContent).toBe('Datei')
  expect(answer.querySelector('del')?.textContent).toBe('alt')
  expect(answer.textContent, 'the markers are gone from an answer').not.toContain('**')
})

/**
 * The look into one member draws through this log (R-WP-53), so the log answers to the name of the widget it
 * stands in, and a `user` line the history leaves unnamed stays unnamed where no author may be lent. Both are
 * the caller's choice and neither needs a test of its own, so one test asks both.
 */
// R-WP-53
test('theLogTakesAnotherTestIdAndNoUserAuthor', () => {
  render(
    <ChatLog
      messages={[
        { role: 'user', text: 'baue die Kachel' },
        { role: 'jon', text: 'sie steht', origin: 'Dev' },
      ]}
      outgoing={[]}
      live={[]}
      testId="insight-log"
      userAuthor={null}
    />,
  )

  expect(screen.queryByTestId('chat-log'), 'one page, two logs, two handles').toBeNull()
  const log = screen.getByTestId('insight-log')
  expect(log).toHaveClass('chat-log')
  expect(
    screen.getByTestId('chat-line-user').querySelector('[data-testid="chat-line-author"]'),
    'a task nobody typed into this widget names no author',
  ).toBeNull()
  // the member's name on his own answer is the line's and not a label this view lends
  expect(log.textContent).toContain('Dev')
})
