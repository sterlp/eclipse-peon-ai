import type { ChatMessage } from './chat.client'
import { content } from '../shared/content'

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
 * One row of the log as the screen shows it (R-WP-29): whose speech it is, who the server named as its
 * author, and whether the history has confirmed it yet.
 */
interface Row {
  /** The voice of the row, and with it its colour: the person's blue at the right edge, the agent's green. */
  voice: 'user' | 'agent'
  /** The handle of the row on the wire: a team answer arrives as `jon` with an author, never as a third role. */
  handle: string
  author: string | null
  text: string
  /** Sent but not confirmed by the history yet — the accent line of the row is not a fact yet. */
  pending: boolean
}

/**
 * One project's conversation as the screen shows it: history, then the still unconfirmed own inputs, then
 * the live line of the running turn. A row is speech and not a card — an accent line names its voice, the
 * person's row sits at the right edge, and one rule separates the two voices (R-WP-29).
 *
 * A user's line always names its author — the person the server named, or the role label of the doc —
 * because the chat is shared and every viewer has to read the same thing (R-WP-25, UC-WP-48). An agent's
 * line stays nameless when the server named none: no "System", no "Peon", no invented agent (R-WP-4).
 * A thinking delta never reaches here (§3.3.3, Q7).
 *
 * Tool lines are not a voice of the conversation: they keep the grey accent of `chat.html`, take no part
 * in the rules between the voices, and Inc 3b moves each one to the place in the flow where it appeared.
 */
export function ChatLog({ messages, outgoing, user, live, toolLines }: Props) {
  const rows: Row[] = [
    ...messages.map((message): Row => ({
      voice: message.role === 'user' ? 'user' : 'agent',
      handle: `chat-line-${message.role}`,
      author: authorOf(message),
      text: message.text,
      pending: false,
    })),
    ...outgoing.map((text): Row => ({
      voice: 'user',
      handle: 'chat-line-user',
      // his own name, so the line does not jump when the history brings the server's author back
      author: user ?? content.chat.you,
      text,
      pending: true,
    })),
    ...(live === ''
      ? []
      : [{ voice: 'agent', handle: 'chat-live', author: null, text: live, pending: true } satisfies Row]),
  ]

  return (
    <ul className="chat-log" data-testid="chat-log" aria-label={content.chat.history}>
      {rows.map((row, index) => (
        <li key={`row-${index}`} className={`chat-line ${row.voice}${row.pending ? ' pending' : ''}`} data-testid={row.handle}>
          {index > 0 && rows[index - 1].voice !== row.voice && <hr className="chat-rule" />}
          {row.author !== null && <ChatRole name={row.author} />}
          <span className="chat-text">{row.text}</span>
        </li>
      ))}
      {toolLines.map((line, index) => (
        <li key={`tool-${index}`} className="chat-line tool" data-testid="chat-tool-line">
          {line}
        </li>
      ))}
      {rows.length === 0 && <li className="empty-state">{content.chat.noMessages}</li>}
    </ul>
  )
}

/** A user's author is the person the server named, or the doc's role label; an agent's is only ever what the server named. */
function authorOf(message: ChatMessage): string | null {
  return message.role === 'user' ? (message.origin ?? content.chat.you) : (message.origin ?? null)
}

/** The voice of one line, above its text — drawn only where one exists, because an invented label would be a lie. */
function ChatRole({ name }: { name: string }) {
  return (
    <span className="chat-role" data-testid="chat-line-author">
      {name}
    </span>
  )
}
