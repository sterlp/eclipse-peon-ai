import type { Ref } from 'react'


import type { ChatMessage } from './chat.client'
import { ChatMarkdown } from './chat.markdown'
import { ChatTools } from './chat.tools'
import { liveRuns, type LiveItem } from './chat.live'
import { content } from '../shared/content'

interface Props {
  /** The finished lines — the only truth for text a turn has written (R-WP-11). */
  messages: ChatMessage[]
  /** Inputs the user sent that the history does not carry yet (a queued input is written at turn start). */
  outgoing: string[]
  /** The name of this browser (R-WP-25) — the author of its own, not yet confirmed lines; null shows the role label. */
  user?: string | null
  /** The live lines of the running turn, each anchored at the rows that already stood when it arrived. */
  live: LiveItem[]
  /** A turn this browser started is still open — the only thing that may paint a waiting frame (R-WP-31). */
  pending: boolean
  /** The scroll area of the chat, handed to the page: it keeps the end of the log in view (R-WP-35). */
  logRef?: Ref<HTMLUListElement>
  /** The page learns from this where the user reads (R-WP-35, UC-WP-66). */
  onScroll?: () => void
}

/**
 * One row of the log as the screen shows it (R-WP-29): whose speech it is, who the server named as its
 * author, and whether the history has confirmed it yet.
 */
interface Row {
  /** The voice of the row, and with it its colour: the person's blue, the agent's green, a failure's red. */
  voice: 'user' | 'agent' | 'error'
  /** The handle of the row on the wire: a team answer arrives as `jon` with an author, never as a third role. */
  handle: string
  author: string | null
  text: string
  /** True while the turn that started the line is open — its accent is not a fact yet (R-WP-31, UC-WP-65). */
  pending: boolean
}

/** One thing the log draws: a line of the conversation, a live answer, or one merged box of tool lines. */
type Entry =
  | { type: 'row'; row: Row; rule: boolean }
  | { type: 'live'; item: LiveItem; rule: boolean }
  | { type: 'tools'; lines: string[] }

/**
 * One project's conversation as the screen shows it: the history, the own inputs the history does not
 * carry yet, and the live lines of the running turn — each live line at the place it happened, so tool
 * activity stands between the parts of an answer it belongs to (R-WP-28, D-7).
 *
 * A row is speech and not a card — the accent line on its left names its voice, in the flow of the text
 * and never right-aligned, and one rule separates the two voices (R-WP-29). An answer reads as Markdown, so
 * the model's headings, lists and code blocks are what the user sees; the person's line stays the text he
 * typed.
 *
 * A user's line always names its author — the person the server named, or the role label of the doc —
 * because the chat is shared and every viewer has to read the same thing (R-WP-25, UC-WP-48). An agent's
 * line stays nameless when the server named none: no "System", no "Peon", no invented agent (R-WP-4).
 * A thinking delta never reaches here (§3.3.3, Q7).
 *
 * Tool activity is not a voice: it keeps the grey of `chat.html`, takes no part in the rule between the
 * voices, and never carries a name.
 */
export function ChatLog({ messages, outgoing, user, live, pending, logRef, onScroll }: Props) {
  const rows: Row[] = [
    ...messages.map((message): Row => ({
      voice: voiceOf(message),
      handle: `chat-line-${message.role}`,
      author: authorOf(message),
      text: message.text,
      // a line the history carries is a fact — the frame never comes back to it (R-WP-31)
      pending: false,
    })),
    ...outgoing.map((text): Row => ({
      voice: 'user',
      handle: 'chat-line-user',
      // his own name, so the line does not jump when the history brings the server's author back
      author: user ?? content.chat.you,
      text,
      // the frame is the page's call and not a property of the row: it ends with the turn, not with the
      // history read that confirms the text (R-WP-31, UC-WP-65)
      pending,
    })),
  ]

  const entries = entriesOf(rows, live)

  return (
    <ul
      className="chat-log"
      data-testid="chat-log"
      aria-label={content.chat.history}
      ref={logRef}
      onScroll={onScroll}
    >
      {entries.map((entry, index) => {
        if (entry.type === 'tools') {
          return <ChatTools key={`tools-${index}`} lines={entry.lines} />
        }
        if (entry.type === 'live') {
          // a live failure is already over, so it carries no waiting frame; its text is the server's
          // reason and never an answer to read as Markdown (R-WP-34)
          const failure = entry.item.kind === 'error'
          return (
            <li
              key={`live-${index}`}
              className={failure ? 'chat-line error' : `chat-line agent${pending ? ' pending' : ''}`}
              data-testid={failure ? 'chat-line-error' : 'chat-live'}
            >
              {entry.rule && <hr className="chat-rule" />}
              {failure ? (
                <span className="chat-text">{entry.item.text}</span>
              ) : (
                <ChatMarkdown text={entry.item.text} final={false} />
              )}
            </li>
          )
        }
        const { row } = entry
        return (
          <li
            key={`row-${index}`}
            className={`chat-line ${row.voice}${row.pending ? ' pending' : ''}`}
            data-testid={row.handle}
          >
            {entry.rule && <hr className="chat-rule" />}
            {row.author !== null && <ChatRole name={row.author} />}
            {row.voice === 'agent' ? (
              <ChatMarkdown text={row.text} />
            ) : (
              <span className="chat-text">{row.text}</span>
            )}
          </li>
        )
      })}
      {entries.length === 0 && <li className="empty-state">{content.chat.noMessages}</li>}
    </ul>
  )
}

/**
 * The rows and the live items in the order the screen shows them: a run of live items is placed after the
 * number of rows it was anchored at, and inside the run the arrival order holds. Consecutive tool lines
 * become one box; the rule between two voices is decided over the rows and the live answers only, because
 * tool activity is neither voice (R-WP-29).
 */
function entriesOf(rows: Row[], live: LiveItem[]): Entry[] {
  const entries: Entry[] = []
  let shown = 0
  let voice: Row['voice'] | null = null

  const pushRow = (row: Row) => {
    const rule = voice !== null && voice !== row.voice
    entries.push({ type: 'row', row, rule })
    voice = row.voice
  }

  for (const run of liveRuns(live)) {
    while (shown < Math.min(run[0].afterLines, rows.length)) {
      pushRow(rows[shown])
      shown += 1
    }
    let tools: string[] = []
    for (const item of run) {
      if (item.kind === 'tool') {
        tools.push(item.text)
        continue
      }
      if (tools.length > 0) {
        entries.push({ type: 'tools', lines: tools })
        tools = []
      }
      // a failure is a voice of its own for the rule: it separates what was said from what failed
      const spoken: Row['voice'] = item.kind === 'error' ? 'error' : 'agent'
      entries.push({ type: 'live', item, rule: voice !== null && voice !== spoken })
      voice = spoken
    }
    if (tools.length > 0) {
      entries.push({ type: 'tools', lines: tools })
    }
  }
  while (shown < rows.length) {
    pushRow(rows[shown])
    shown += 1
  }
  return entries
}

/** The voice a stored line speaks with — a failure speaks for nobody (R-WP-34). */
function voiceOf(message: ChatMessage): Row['voice'] {
  if (message.role === 'user') {
    return 'user'
  }
  return message.role === 'error' ? 'error' : 'agent'
}

/** A user's author is the person the server named, or the doc's role label; an agent's is only ever what the server named. */
function authorOf(message: ChatMessage): string | null {
  if (message.role === 'error') {
    // no author, and none to fall back to: the model was never reached, so nobody spoke (R-WP-4, R-WP-34)
    return null
  }
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
