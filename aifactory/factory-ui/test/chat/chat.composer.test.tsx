import { afterEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'

import { ChatComposer, MAX_ROWS } from '../../src/chat/chat.composer'
import { block } from '../shared/sheets'

interface Over {
  draft?: string
  busy?: boolean
}

/** The composer with everything off: a test names only what its scenario cares about. */
function renderComposer(over: Over = {}) {
  const props = {
    draft: over.draft ?? '',
    busy: over.busy ?? false,
    onDraftChange: vi.fn(),
    // the page prevents the browser's own submission, and the test double has to: jsdom would otherwise
    // report the form submission it does not implement
    onSubmit: vi.fn(event => event.preventDefault()),
    onStop: vi.fn(),
  }
  const view = render(<ChatComposer {...props} />)

  /** The page's next render, the doubles kept — a call count has to survive it (UC-WP-74 GIVEN 4). */
  function rerender(next: Over): void {
    Object.assign(props, next)
    view.rerender(<ChatComposer {...props} />)
  }

  return { ...props, container: view.container, rerender }
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
        onDraftChange={setDraft}
        onSubmit={() => undefined}
        onStop={() => undefined}
      />
    )
  }
  render(<OwnedComposer />)
  return screen.getAllByTestId('chat-input').at(-1) as HTMLTextAreaElement
}

/**
 * Ctrl/Cmd+Enter on a fresh composer with the selection the test names, and the text the field was handed.
 * Never a send, never the browser's default — what varies is the selection: the Umbruch has to land where
 * the caret stands, not where the text ends.
 */
function pressLineBreak(
  modifier: { ctrlKey?: boolean; metaKey?: boolean },
  draft: string,
  from: number,
  to = from,
): string {
  cleanup()
  const props = renderComposer({ draft })
  const field = screen.getByTestId('chat-input') as HTMLTextAreaElement
  field.setSelectionRange(from, to)

  expect(fireEvent.keyDown(field, { key: 'Enter', ...modifier }), 'no default to keep here').toBe(false)
  expect(props.onSubmit, 'and the keystroke never sends').not.toHaveBeenCalled()
  expect(props.onDraftChange, 'one text, not two').toHaveBeenCalledTimes(1)
  return props.onDraftChange.mock.calls[0]?.[0] as string
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
test('emptyEnterSendsNothing', () => {
  const props = renderComposer()

  // an empty Enter is neither a send nor a line break (UC-WP-56 GIVEN 1): no default is left to the browser,
  // and the page is never asked — the repeat of UC-WP-57 lives on the click alone
  expect(fireEvent.keyDown(screen.getByTestId('chat-input'), { key: 'Enter' })).toBe(false)
  expect(props.onSubmit).not.toHaveBeenCalled()
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

  // the browser adds no Umbruch to Ctrl/Cmd+Enter, so the composer inserts it — on both platforms, and at
  // the place the user stands: mid-text it splits the line, a selection it replaces
  expect(pressLineBreak({ ctrlKey: true }, 'erste Zeile', 'erste Zeile'.length)).toBe('erste Zeile\n')
  expect(pressLineBreak({ metaKey: true }, 'erste Zeile', 'erste Zeile'.length)).toBe('erste Zeile\n')
  expect(pressLineBreak({ ctrlKey: true }, 'erste Zeile', 5), 'the Umbruch lands at the caret').toBe(
    'erste\n Zeile',
  )
  expect(pressLineBreak({ ctrlKey: true }, 'erste Zeile', 6, 11), 'a selection is replaced by it').toBe(
    'erste \n',
  )

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

// UC-WP-74
test('theSendButtonIsNeverDisabled', () => {
  const page = renderComposer()
  const field = screen.getByTestId('chat-input')

  // an empty draft is no reason to refuse the button (R-WP-41, UC-WP-74 GIVEN 1) — and the composer decides
  // nothing on the way out: the click reaches the page even with nothing typed (UC-WP-57)
  expect(screen.getByTestId('chat-send')).toBeEnabled()
  fireEvent.click(screen.getByTestId('chat-send'))
  expect(page.onSubmit).toHaveBeenCalledTimes(1)

  // whitespace and text leave the button as they found it, and the field itself never locks — not here, not
  // in a turn
  page.rerender({ draft: '   ' })
  expect(screen.getByTestId('chat-send')).toBeEnabled()
  page.rerender({ draft: 'x' })
  expect(screen.getByTestId('chat-send')).toBeEnabled()
  expect(field).toBeEnabled()
})

// UC-WP-74
test('aRunningTurnNeverLocksTheInput', () => {
  const page = renderComposer({ draft: 'zähl die Dateien', busy: true })

  // the reference app locks its field because it has no queue; ours sends into the core's queue (R-WP-10)
  expect(screen.getByTestId('chat-input')).toBeEnabled()
  expect(screen.getByTestId('chat-send')).toBeEnabled()

  fireEvent.click(screen.getByTestId('chat-send'))
  expect(page.onSubmit).toHaveBeenCalledTimes(1)
})

// UC-WP-74
// ANTD-A8
test('theFieldGrowsWithItsText', () => {
  // jsdom lays out nothing (no `scrollHeight`, no sheet), so a height is not measurable here and therefore
  // not assertable: what is proven is the *form* the growth comes in — the basis' own auto-grow field with
  // its inline artefact and the bound as a constant. The optics ("five lines, then it scrolls") are what
  // the smoke test looks at.
  const page = renderComposer({ draft: 'erste\nzweite' })

  expect(MAX_ROWS, 'the bound of the growth is a named constant (UC-WP-74 GIVEN 3)').toBe(5)

  const fields = page.container.querySelectorAll('textarea.chat-composer-field')
  expect(fields, 'one field, and it is the basis own').toHaveLength(1)
  const field = fields[0] as HTMLTextAreaElement
  expect(field, 'no rows: the height belongs to the basis, not the sheet').not.toHaveAttribute('rows')
  expect(field.style.resize, 'and no handle to fight it with').toBe('none')
  expect(field.style.overflowY, 'hidden while it fits, auto once it does — autoSize alone writes this').toBe(
    'hidden',
  )

  // the same reason the other way round: a `min-height` of our sheet would outvote the inline height
  const sheet = block('.chat-composer')
  expect(sheet, 'the height stays with the basis').not.toContain('min-height')
  expect(sheet, 'and the handle too').not.toContain('resize')
})

// UC-WP-74
test('theFocusReturnsWhenTheTurnEnds', () => {
  const page = renderComposer({ draft: 'noch eine', busy: true })
  const field = screen.getByTestId('chat-input')

  // the turn ends and nothing has the caret: the field takes it, the user types on without clicking
  field.blur()
  page.rerender({ busy: false })
  expect(field).toHaveFocus()

  // and the turn ending does not steal the caret out of a field he is working in
  render(<input data-testid="other-input" />)
  page.rerender({ busy: true })
  screen.getByTestId('other-input').focus()
  page.rerender({ busy: false })
  expect(screen.getByTestId('other-input')).toHaveFocus()
  expect(field).not.toHaveFocus()
})
