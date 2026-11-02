import { useRef } from 'react'
import type { FormEvent, KeyboardEvent, RefObject } from 'react'

import { content } from '../shared/content'

interface Props {
  draft: string
  /** The page focuses the field when the project opens — so the ref stays with the page's effect. */
  inputRef: RefObject<HTMLTextAreaElement | null>
  /** A turn is running on this project — the only thing that makes a stop mean something (UC-WP-50). */
  busy: boolean
  /** The text of the last send the server took (UC-WP-57); nothing can be re-sent before the first one. */
  lastSent: string | null
  onDraftChange: (value: string) => void
  onSubmit: (event: FormEvent) => void
  onResend: () => void
  onStop: () => void
}

/**
 * The chat's three controls (UC-WP-6, UC-WP-50, UC-WP-57): one writes into this project's conversation,
 * one sends the last accepted text again, one ends the turn that runs. Neither of the first two names an
 * addressee — there is no agent to choose here — and none of them writes anything but into the queue:
 * sending stays enabled while Jon works, because his queue takes the input (R-WP-10).
 *
 * The field is a textarea, so a question can be more than one line: Enter is the newline of that question
 * and never a send, Cmd/Ctrl+Enter is the send the keyboard adds to the button (R-WP-30).
 */
export function ChatComposer({
  draft,
  inputRef,
  busy,
  lastSent,
  onDraftChange,
  onSubmit,
  onResend,
  onStop,
}: Props) {
  const form = useRef<HTMLFormElement>(null)

  function sendFromKeyboard(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key !== 'Enter' || !(event.metaKey || event.ctrlKey)) {
      return
    }
    event.preventDefault()
    // the button's requestSubmit path, so the modifier cannot send what the button would refuse
    form.current?.requestSubmit()
  }

  return (
    <form className="chat-composer" ref={form} onSubmit={event => void onSubmit(event)}>
      <textarea
        aria-label={content.chat.message}
        data-testid="chat-input"
        placeholder={content.chat.messageToJon}
        ref={inputRef}
        rows={3}
        value={draft}
        onChange={event => onDraftChange(event.target.value)}
        onKeyDown={sendFromKeyboard}
      />
      <div className="chat-composer-actions">
        <button type="submit" data-testid="chat-send" disabled={draft.trim() === ''}>
          {content.chat.send}
        </button>
        <button
          type="button"
          className="chat-resend"
          data-testid="chat-resend"
          disabled={lastSent === null}
          onClick={onResend}
        >
          {content.chat.resend}
        </button>
        <button
          type="button"
          className="chat-stop"
          data-testid="chat-stop"
          disabled={!busy}
          onClick={() => void onStop()}
        >
          {content.chat.stop}
        </button>
      </div>
    </form>
  )
}
