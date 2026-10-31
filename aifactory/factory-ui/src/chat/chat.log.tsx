import type { ChatMessage } from './chat.client'

interface Props {
  /** The finished lines — the only truth for text a turn has written (R-WP-11). */
  messages: ChatMessage[]
  /** Inputs the user sent that the history does not carry yet (a queued input is written at turn start). */
  outgoing: string[]
  live: string
  toolLines: string[]
}

/**
 * One project's conversation as the screen shows it: history, then the still unconfirmed own inputs,
 * then the live line and the tool lines of the running turn.
 *
 * Every line names its author when it has one and stays nameless when it has none — no "System", no
 * "Peon", no invented label (R-WP-4). A thinking delta never reaches here (§3.3.3, Q7).
 */
export function ChatLog({ messages, outgoing, live, toolLines }: Props) {
  const empty = messages.length === 0 && outgoing.length === 0 && live === ''
  return (
    <ul className="chat-log" data-testid="chat-log" aria-label="Verlauf">
      {messages.map((message, index) => (
        <li key={`history-${index}`} className={`chat-line ${message.role}`} data-testid={`chat-line-${message.role}`}>
          <ChatAuthor origin={message.origin} />
          <span className="chat-text">{message.text}</span>
        </li>
      ))}
      {outgoing.map((text, index) => (
        <li key={`outgoing-${index}`} className="chat-line user pending" data-testid="chat-line-user">
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
