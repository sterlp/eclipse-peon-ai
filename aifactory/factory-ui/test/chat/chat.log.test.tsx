import { render, screen } from '@testing-library/react'

import { ChatLog } from '../../src/chat/chat.log'
import { appendLiveError } from '../../src/chat/chat.live'
import { declarations } from '../shared/sheets'
import type { ChatMessage } from '../../src/chat/chat.client'

const QUESTION: ChatMessage = { role: 'user', text: 'wo liegen die Dateien?', origin: 'Paul' }
/** The wire text of a failed turn — the server's reason, which the log shows as it is (R-WP-27). */
const FAILURE: ChatMessage = { role: 'error', text: 'turn needs a model — set model in the factory config' }

/**
 * The failure is a voice of the log (R-WP-34): it takes the rule of a voice change and the colour of the
 * problem, and it owns no surface of its own. Colour is no fact of the DOM in jsdom, so the sheet is read
 * where the contract is written — see `test/shared/sheets.ts`.
 */
test('roleChangeToAnErrorDrawsARule', () => {
  const { container } = render(<ChatLog messages={[QUESTION, FAILURE]} outgoing={[]} live={[]} />)

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
test('aLiveFailureIsALineAndNotAWaitingFrame', () => {
  render(<ChatLog messages={[QUESTION]} outgoing={[]} live={appendLiveError([], 'no model configured', 1)} />)

  const failure = screen.getByTestId('chat-line-error')
  expect(failure).toHaveTextContent('no model configured')
  // the turn is over, so nothing about this line is "not a fact yet"
  expect(failure.className).not.toContain('pending')
  expect(screen.queryByTestId('chat-live')).toBeNull()
})
