import { render, screen } from '@testing-library/react'

import { ChatLog } from '../../src/chat/chat.log'
import { appendLiveError, appendLiveTool } from '../../src/chat/chat.live'
import { declarations } from '../shared/sheets'
import type { ChatMessage } from '../../src/chat/chat.client'

const QUESTION: ChatMessage = { role: 'user', text: 'wo liegen die Dateien?', origin: 'Paul' }
/** The wire text of a failed turn — the server's reason, which the log shows as it is (R-WP-27). */
const FAILURE: ChatMessage = { role: 'error', text: 'turn needs a model — set model in the factory config' }
/** The input the trace belongs to, read back from the history — the anchor of the trace counts it (D-A). */
const SENTENCE: ChatMessage = { role: 'user', text: 'zähl die Dateien', origin: 'Paul' }
const ANSWER: ChatMessage = { role: 'jon', text: 'Zwei: pom.xml und README.md' }

/**
 * The failure is a voice of the log (R-WP-34): it takes the rule of a voice change and the colour of the
 * problem, and it owns no surface of its own. Colour is no fact of the DOM in jsdom, so the sheet is read
 * where the contract is written — see `test/shared/sheets.ts`.
 */
// UC-WP-64, UC-WP-55
test('roleChangeToAnErrorDrawsARule', () => {
  const { container } = render(<ChatLog messages={[QUESTION, FAILURE]} outgoing={[]} live={[]} pending={false} />)

  // one rule for one change of voice, carried by the line that begins it, so the list stays a list
  expect(container.querySelectorAll('hr.chat-rule')).toHaveLength(1)
  const failure = screen.getByTestId('chat-line-error')
  expect([...failure.children].some(child => child.tagName === 'HR')).toBe(true)
  expect(failure.className).toContain('error')

  const painted = declarations('.chat-log .chat-line.error')
  expect(painted, 'the accent and the text of a failure are the problem token').toContain('var(--problem)')
  expect(painted, 'a grey block would read as a card and not as a line (UC-WP-55)').not.toContain('background')
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
  expect(shape).toEqual(['chat-line-user', 'chat-line-user', 'chat-tool-line', 'chat-line-jon'])
  // one box for the run, and both of its lines
  expect([
    ...screen.getByTestId('chat-tool-line').querySelectorAll('.chat-tool-line'),
  ]).toHaveLength(2)
})

/**
 * Only answers are Markdown (R-WP-28): what a person typed keeps its markers, so an input quoting a command
 * never reads as the agent's formatting — and never becomes markup of the page either.
 */
// UC-WP-73
test('anInputWithMarkdownCharactersStaysItsWord', () => {
  const quoted: ChatMessage = { role: 'user', text: 'mach das **fett** und ~~weg~~' }
  render(<ChatLog messages={[quoted, ANSWER]} outgoing={[]} live={[]} pending={false} />)

  const line = screen.getByTestId('chat-line-user')
  expect(line.textContent).toContain('**fett**')
  expect(line.textContent).toContain('~~weg~~')
  expect(line.querySelector('strong, del, br, table')).toBeNull()
  // the answer beside it is the line that draws markup
  expect(screen.getByTestId('chat-line-jon').querySelector('[data-testid="chat-markdown"]')).not.toBeNull()
})
