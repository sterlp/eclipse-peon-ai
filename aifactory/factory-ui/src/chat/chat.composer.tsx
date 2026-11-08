import { useEffect, useRef } from 'react'
import type { FormEvent, KeyboardEvent } from 'react'

import { Button, Input } from 'antd'
import type { GetRef } from 'antd'

import { content } from '../shared/content'

/** The growth's upper bound (R-WP-41 / UC-WP-74 GIVEN 4): five lines, then the field scrolls. */
export const MAX_ROWS = 5

/**
 * Is the user typing somewhere that is not this field? The end of a turn gives the field its focus back
 * (UC-WP-74 GIVEN 5) — it must not steal the caret out of the search field he is working in.
 */
function typingElsewhere(own: HTMLFormElement | null): boolean {
  const active = document.activeElement
  if (active === null || active === document.body) {
    return false
  }
  if (own?.contains(active) ?? false) {
    return false
  }
  return (
    active instanceof HTMLElement &&
    (active.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(active.tagName))
  )
}

interface Props {
  draft: string
  /** A turn is running on this project — the only thing that makes a stop mean something (UC-WP-50). */
  busy: boolean
  onDraftChange: (value: string) => void
  onSubmit: (event: FormEvent) => void
  onStop: () => void
}

/**
 * The chat's field and its two controls (UC-WP-6, UC-WP-50): one writes into this project's conversation, one
 * ends the turn that runs. Neither names an addressee — there is no agent to choose here — and neither writes
 * anything but into the queue: sending stays enabled while Jon works, because his queue takes the input
 * (R-WP-10, R-WP-41).
 *
 * The send is **never disabled** (R-WP-41, UC-WP-74 GIVEN 1) and this component decides nothing on the way
 * out: every submit — an empty draft included — goes to `onSubmit`, and whether it opens a turn, repeats the
 * last message or does nothing is the page's answer (R-WP-30, UC-WP-57). The draft is asked about in one
 * place only: the Enter gesture below.
 *
 * The field is the basis' auto-grow textarea: one line to `MAX_ROWS`, then it scrolls instead of pushing the
 * chat away (R-WP-41). Enter is the send and Shift+Enter the browser's own line break; Ctrl/Cmd+Enter is the
 * second way to a line break, which the browser does not add by itself (R-WP-30). An **empty** Enter is no
 * send at all — the keystroke keeps its prevented default and reaches no turn, while the same click on the
 * button may repeat a text (UC-WP-56 GIVEN 1, Paul 2026-11-08). The field owns its focus: it takes it
 * when the project opens and gets it back when a turn ends (UC-WP-74 GIVEN 5).
 */
export function ChatComposer({ draft, busy, onDraftChange, onSubmit, onStop }: Props) {
  const field = useRef<GetRef<typeof Input.TextArea> | null>(null)
  const form = useRef<HTMLFormElement>(null)
  /** The turn that just ended is the moment the field gets its focus back (UC-WP-74 GIVEN 5). */
  const wasBusy = useRef(busy)

  function keys(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key !== 'Enter') {
      return
    }
    if (event.shiftKey) {
      return // the browser's own line break (R-WP-30)
    }
    event.preventDefault() // Enter is the send: no default is left to the browser
    if (event.metaKey || event.ctrlKey) {
      // the second way to a new line — the browser adds no Umbruch for this keystroke
      const at = event.currentTarget
      const from = at.selectionStart ?? draft.length
      const to = at.selectionEnd ?? draft.length
      onDraftChange(`${draft.slice(0, from)}\n${draft.slice(to)}`)
      return
    }
    // an empty Enter is no send and no line break (UC-WP-56 GIVEN 1): the default is already prevented
    // above, so the keystroke stays inert — the repeat of UC-WP-57 lives on the click alone
    if (draft.trim() === '') {
      return
    }
    // the button's own requestSubmit way, so click and keystroke end at exactly one decider (UC-WP-57)
    form.current?.requestSubmit()
  }

  // the project opened with this field: it owns the focus from the first drawing (UC-WP-74 GIVEN 5)
  useEffect(() => {
    field.current?.focus()
  }, [])

  useEffect(() => {
    if (wasBusy.current && !busy && !typingElsewhere(form.current)) {
      field.current?.focus()
    }
    wasBusy.current = busy
  }, [busy])

  return (
    <form className="chat-composer" ref={form} onSubmit={event => void onSubmit(event)}>
      <Input.TextArea
        aria-label={content.chat.message}
        autoSize={{ minRows: 1, maxRows: MAX_ROWS }}
        classNames={{ textarea: 'chat-composer-field' }}
        data-testid="chat-input"
        placeholder={content.chat.messageToJon}
        ref={field}
        value={draft}
        onChange={event => onDraftChange(event.target.value)}
        onKeyDown={keys}
      />
      {/* the send stands beside the field, on its right and at its bottom (R-WP-35); the one secondary
          control keeps the quiet second row, so a long question keeps its width. Both are elements of the
          basis, like every action of the app (R-WP-43) */}
      <Button className="chat-send" htmlType="submit" data-testid="chat-send">
        {content.chat.send}
      </Button>
      <div className="chat-composer-actions">
        <Button
          className="chat-stop"
          data-testid="chat-stop"
          disabled={!busy}
          onClick={() => void onStop()}
        >
          {content.chat.stop}
        </Button>
      </div>
    </form>
  )
}
