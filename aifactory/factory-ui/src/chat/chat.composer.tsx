import type { FormEvent, RefObject } from 'react'

interface Props {
  draft: string
  /** The page focuses the field when the project opens — so the ref stays with the page's effect. */
  inputRef: RefObject<HTMLInputElement | null>
  onDraftChange: (value: string) => void
  onSubmit: (event: FormEvent) => void
}

/**
 * The only control the chat has (UC-WP-6): it writes into this project's conversation and names no
 * addressee — there is no agent to choose here.
 */
export function ChatComposer({ draft, inputRef, onDraftChange, onSubmit }: Props) {
  return (
    <form className="chat-composer" onSubmit={event => void onSubmit(event)}>
      <input
        ref={inputRef}
        type="text"
        aria-label="Nachricht"
        data-testid="chat-input"
        placeholder="Nachricht an Jon"
        value={draft}
        onChange={event => onDraftChange(event.target.value)}
      />
      <button type="submit" data-testid="chat-send" disabled={draft.trim() === ''}>
        Senden
      </button>
    </form>
  )
}
