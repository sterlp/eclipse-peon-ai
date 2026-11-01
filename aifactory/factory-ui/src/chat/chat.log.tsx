import type { ChatMessage } from './chat.client'

/** The label of R-WP-25 for a user line without a name — a role, not an invented author. */
const USER_ROLE_LABEL = 'YOU'

interface Props {
  /** The finished lines — the only truth for text a turn has written (R-WP-11). */
  messages: ChatMessage[]
  /** Inputs the user sent that the history does not carry yet (a queued input is written at turn start). */
  outgoing: string[]
  /** The name of this browser (R-WP-25) — the author of its own, not yet confirmed lines; null shows the role label. */
  user?: string | null
  live: string
  toolLines: string[]
}

/**
 * One project's conversation as the screen shows it: history, then the still unconfirmed own inputs,
 * then the live line and the tool lines of the running turn.
 *
 * A user's line always names its author — the person the server named, or the role label of the doc —
 * because the chat is shared and every viewer has to read the same thing (R-WP-25, UC-WP-48). An agent's
 * line stays nameless when the server named none: no "System", no "Peon", no invented agent (R-WP-4).
 * A thinking delta never reaches here (§3.3.3, Q7).
 */
export function ChatLog({ messages, outgoing, user, live, toolLines }: Props) {
  const empty = messages.length === 0 && outgoing.length === 0 && live === ''
  return (
    <ul className="chat-log" data-testid="chat-log" aria-label="Verlauf">
      {messages.map((message, index) => (
        <li key={`history-${index}`} className={`chat-line ${message.role}`} data-testid={`chat-line-${message.role}`}>
          <ChatAuthor origin={authorOf(message)} />
          <span className="chat-text">{message.text}</span>
        </li>
      ))}
      {outgoing.map((text, index) => (
        <li key={`outgoing-${index}`} className="chat-line user pending" data-testid="chat-line-user">
          {/* his own name, so the line does not jump when the history brings the server's author back */}
          <ChatAuthor origin={user ?? USER_ROLE_LABEL} />
          <span className="chat-text">{text}</span>
        </li>
      ))}
      {live !== '' && (
        <li className="chat-line jon live" data-testid="chat-live">
          <span className="chat-text">{live}</span>
        </li>
      )}
      {toolLines.map((line, index) => (
        <li key={`tool-${index}`} className="chat-tool-line" data-testid="chat-tool-line">
          {line}
        </li>
      ))}
      {empty && <li className="empty-state">Noch keine Nachrichten</li>}
    </ul>
  )
}

/** A user's author is the person the server named, or the doc's role label; an agent's is only ever what the server named. */
function authorOf(message: ChatMessage): string | null | undefined {
  return message.role === 'user' ? (message.origin ?? USER_ROLE_LABEL) : message.origin
}

/** The author of one line — drawn only where one exists, because an invented label would be a lie. */
function ChatAuthor({ origin }: { origin?: string | null }) {
  if (!origin) {
    return null
  }
  return (
    <span className="chat-line-author" data-testid="chat-line-author">
      {origin}
    </span>
  )
}
