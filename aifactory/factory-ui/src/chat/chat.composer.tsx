import type { FormEvent, RefObject } from 'react'

import { content } from '../shared/content'

interface Props {
  draft: string
  /** The page focuses the field when the project opens — so the ref stays with the page's effect. */
  inputRef: RefObject<HTMLInputElement | null>
  /** A turn is running on this project — the only thing that makes a stop mean something (UC-WP-50). */
  busy: boolean
  onDraftChange: (value: string) => void
  onSubmit: (event: FormEvent) => void
  onStop: () => void
}

/**
 * The chat's two controls (UC-WP-6, UC-WP-50): one writes into this project's conversation, one ends the
 * turn that runs. Neither names an addressee — there is no agent to choose here — and the second one writes
 * nothing: sending stays enabled while Jon works, because his queue takes the input (R-WP-10).
 */
export function ChatComposer({ draft, inputRef, busy, onDraftChange, onSubmit, onStop }: Props) {
  return (
    <form className="chat-composer" onSubmit={event => void onSubmit(event)}>
      <input
        ref={inputRef}
        type="text"
        aria-label={content.chat.message}
        data-testid="chat-input"
        placeholder={content.chat.messageToJon}
        value={draft}
        onChange={event => onDraftChange(event.target.value)}
      />
      <button type="submit" data-testid="chat-send" disabled={draft.trim() === ''}>
        {content.chat.send}
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
    </form>
  )
}
