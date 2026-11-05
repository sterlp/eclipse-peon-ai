import { afterEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { createRef, useState } from 'react'
import type { RefObject } from 'react'

import { ChatComposer } from '../../src/chat/chat.composer'
import { content } from '../../src/shared/content'

/** The composer with everything off: a test names only what its scenario cares about. */
function renderComposer(over: { draft?: string; busy?: boolean; lastSent?: string | null } = {}) {
  const props = {
    draft: over.draft ?? '',
    inputRef: createRef<HTMLTextAreaElement>() as RefObject<HTMLTextAreaElement | null>,
    busy: over.busy ?? false,
    lastSent: over.lastSent ?? null,
    onDraftChange: vi.fn(),
    // the page prevents the browser's own submission, and the test double has to: jsdom would otherwise
    // report the form submission it does not implement
    onSubmit: vi.fn(event => event.preventDefault()),
    onResend: vi.fn(),
    onStop: vi.fn(),
  }
  render(<ChatComposer {...props} />)
  return props
}

afterEach(() => {
  cleanup()
})

/**
 * The field is controlled, so a test that types into it needs an owner of the value — the page in
 * production, a `useState` here.
 */
function renderOwnedComposer(): HTMLElement {
  function OwnedComposer() {
    const [draft, setDraft] = useState('')
    return (
      <ChatComposer
        draft={draft}
        inputRef={createRef<HTMLTextAreaElement>() as RefObject<HTMLTextAreaElement | null>}
        busy={false}
        lastSent={null}
        onDraftChange={setDraft}
        onSubmit={() => undefined}
        onResend={() => undefined}
        onStop={() => undefined}
      />
    )
  }
  render(<OwnedComposer />)
  return screen.getAllByTestId('chat-input').at(-1) as HTMLElement
}

// UC-WP-56
test('enterAddsANewline', () => {
  const props = renderComposer({ draft: 'erste Zeile' })
  const field = screen.getByTestId('chat-input')

  // the browser's own line break has to survive the field: a cancelled default would eat the newline (R-WP-30)
  expect(fireEvent.keyDown(field, { key: 'Enter' })).toBe(true)
  expect(props.onSubmit).not.toHaveBeenCalled()

  // and the field must be one that holds a second line at all — a text input would drop it
  const owned = renderOwnedComposer()
  fireEvent.change(owned, { target: { value: 'erste Zeile\nzweite Zeile' } })
  expect(owned).toHaveValue('erste Zeile\nzweite Zeile')
})

// UC-WP-56
test('ctrlEnterSends', () => {
  const mac = renderComposer({ draft: 'zwei\nZeilen' })
  fireEvent.keyDown(screen.getByTestId('chat-input'), { key: 'Enter', metaKey: true })
  expect(mac.onSubmit).toHaveBeenCalledTimes(1)

  // the same keystroke on Windows and Linux is Ctrl, and the doc names both
  cleanup()
  const windows = renderComposer({ draft: 'zwei\nZeilen' })
  fireEvent.keyDown(screen.getByTestId('chat-input'), { key: 'Enter', ctrlKey: true })
  expect(windows.onSubmit).toHaveBeenCalledTimes(1)
})

// UC-WP-56
test('theSendButtonStaysTheClickableAlternative', () => {
  const props = renderComposer({ draft: 'zähl die Dateien' })

  // the keyboard adds a send, it does not replace one: the button reaches the same submit path
  fireEvent.click(screen.getByTestId('chat-send'))
  expect(props.onSubmit).toHaveBeenCalledTimes(1)
})

// UC-WP-57
test('resendSendsTheLastMessageAgain', () => {
  const props = renderComposer({ lastSent: 'zähl die Dateien' })

  const resend = screen.getByTestId('chat-resend')
  expect(resend).toBeEnabled()
  expect(resend).toHaveTextContent(content.chat.resend)

  fireEvent.click(resend)
  expect(props.onResend).toHaveBeenCalledTimes(1)
})

// UC-WP-57
test('nothingCanBeResentBeforeTheFirstSend', () => {
  renderComposer()

  // the button exists from the first moment — but it offers nothing until a send was accepted
  expect(screen.getByTestId('chat-resend')).toBeDisabled()
})
