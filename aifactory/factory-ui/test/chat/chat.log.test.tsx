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

/**
 * A change of voice draws no line of its own (R-WP-29): the two rows are separated by the form each voice is
 * painted in, and the failure keeps the colour of the problem — never by a rule of the log between them. The
 * sheet's own half of that prohibition is `noAnswerLineIsCentredOrRightAligned`.
 */
// UC-WP-64
// UC-WP-55
// R-WP-29
test('roleChangeDrawsNoRule', () => {
  render(<ChatLog messages={[QUESTION, FAILURE]} outgoing={[]} live={[]} pending={false} />)

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
  render(<ChatLog messages={[QUESTION, separating, SENTENCE]} outgoing={[]} live={[]} pending={false} />)

  const log = screen.getByTestId('chat-log')
  expect(separatorsOf(log), 'the answer is the only line on the screen, and it is no separator').toEqual([])

  const drawn = log.querySelectorAll('hr')
  expect(drawn, 'the answer does draw its line').toHaveLength(1)
  expect(drawn[0].closest('[data-testid="chat-markdown"]'), 'inside the answer, not beside it').not.toBeNull()
})

/** The other half of the same rule: the failure shows the moment the stream reports it, in the log. */
// UC-WP-64, UC-WP-65
test('aLiveFailureIsALineAndNotAWaitingFrame', () => {
  render(
    <ChatLog
      messages={[QUESTION]}
      outgoing={[]}
      live={appendLiveError([], 'no model configured', 1)}
      // the page's frame is on for the rest of the log, and the failure still carries none of it
      pending
    />,
  )

  const failure = screen.getByTestId('chat-line-error')
  expect(failure).toHaveTextContent('no model configured')
  // the turn is over, so nothing about this line is "not a fact yet"
  expect(failure.className).not.toContain('pending')
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
      pending={false}
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
      pending={false}
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
