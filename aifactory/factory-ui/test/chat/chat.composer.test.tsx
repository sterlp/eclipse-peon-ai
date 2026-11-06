import { afterEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'

import { ChatComposer } from '../../src/chat/chat.composer'
import { content } from '../../src/shared/content'

interface Over {
  draft?: string
  busy?: boolean
  lastSent?: string | null
}

/** The composer with everything off: a test names only what its scenario cares about. */
function renderComposer(over: Over = {}) {
  const props = {
    draft: over.draft ?? '',
    busy: over.busy ?? false,
    lastSent: over.lastSent ?? null,
    onDraftChange: vi.fn(),
    // the page prevents the browser's own submission, and the test double has to: jsdom would otherwise
    // report the form submission it does not implement
    onSubmit: vi.fn(event => event.preventDefault()),
    onResend: vi.fn(),
    onStop: vi.fn(),
  }
  const view = render(<ChatComposer {...props} />)

  /** The page's next render, the doubles kept — a call count has to survive it (UC-WP-74 GIVEN 4). */
  function rerender(next: Over): void {
    Object.assign(props, next)
    view.rerender(<ChatComposer {...props} />)
  }

  return { ...props, rerender }
}

afterEach(() => {
  cleanup()
})

/**
 * The field is controlled, so a test that types into it needs an owner of the value — the page in
 * production, a `useState` here.
 */
function renderOwnedComposer(): HTMLTextAreaElement {
  function OwnedComposer() {
    const [draft, setDraft] = useState('')
    return (
      <ChatComposer
        draft={draft}
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
  return screen.getAllByTestId('chat-input').at(-1) as HTMLTextAreaElement
}

/** The field of one render, its caret at the end — where a line break has to land. */
function fieldWithCaretAtTheEnd(draft: string): HTMLTextAreaElement {
  const field = screen.getByTestId('chat-input') as HTMLTextAreaElement
  field.setSelectionRange(draft.length, draft.length)
  return field
}

// UC-WP-56
test('enterSends', () => {
  const props = renderComposer({ draft: 'erste Zeile' })

  // Enter is the send now (R-WP-30): nothing is left to the browser, or its Umbruch would eat the send
  expect(fireEvent.keyDown(screen.getByTestId('chat-input'), { key: 'Enter' })).toBe(false)
  expect(props.onSubmit).toHaveBeenCalledTimes(1)
  expect(props.onDraftChange).not.toHaveBeenCalled()
})

// UC-WP-56
// ANTD-A7
test('shiftEnterAndCtrlEnterBreakTheLine', () => {
  // Shift is the browser's own line break: the default survives and no send happens
  const shifted = renderComposer({ draft: 'erste Zeile' })
  expect(fireEvent.keyDown(screen.getByTestId('chat-input'), { key: 'Enter', shiftKey: true })).toBe(true)
  expect(shifted.onSubmit).not.toHaveBeenCalled()
  expect(shifted.onDraftChange).not.toHaveBeenCalled()

  // the browser adds no Umbruch to Ctrl/Cmd+Enter, so the composer inserts it — on both platforms
  cleanup()
  const windows = renderComposer({ draft: 'erste Zeile' })
  expect(
    fireEvent.keyDown(fieldWithCaretAtTheEnd('erste Zeile'), { key: 'Enter', ctrlKey: true }),
  ).toBe(false)
  expect(windows.onDraftChange).toHaveBeenCalledWith('erste Zeile\n')
  expect(windows.onSubmit).not.toHaveBeenCalled()

  cleanup()
  const mac = renderComposer({ draft: 'erste Zeile' })
  expect(
    fireEvent.keyDown(fieldWithCaretAtTheEnd('erste Zeile'), { key: 'Enter', metaKey: true }),
  ).toBe(false)
  expect(mac.onDraftChange).toHaveBeenCalledWith('erste Zeile\n')
  expect(mac.onSubmit).not.toHaveBeenCalled()

  // and the field is one that holds a second line at all — a text input would drop it
  const owned = renderOwnedComposer()
  fireEvent.change(owned, { target: { value: 'erste Zeile\nzweite Zeile' } })
  expect(owned).toHaveValue('erste Zeile\nzweite Zeile')
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
