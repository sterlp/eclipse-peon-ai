import { content } from '../shared/content'

interface Props {
  /** The last thing the stream reported — empty while the turn has delivered nothing yet. */
  preview: string
}

/**
 * The wait of a running turn (R-WP-31): the line the plugin paints, sticky at the lower edge, dark, mono,
 * with a caret that keeps blinking while the turn works. It carries the live preview of the stream, so the
 * wait is never silent, and it goes away with the turn — `done` as well as a failed one (UC-WP-58).
 *
 * It shows the newest line, not the whole answer: the log above already grows, and a bar that repeated
 * the answer would push the screen away on every chunk.
 */
export function ChatWaiting({ preview }: Props) {
  return (
    <div className="chat-waiting" data-testid="chat-waiting" role="status">
      <span className="chat-waiting-text">{preview === '' ? content.chat.waitingForAi : preview}</span>
      <span aria-hidden="true" className="chat-waiting-caret" data-testid="chat-waiting-caret" />
    </div>
  )
}
