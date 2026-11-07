import type { Ref } from 'react'


import type { ChatMessage } from './chat.client'
import { ChatDiff } from './chat.diff'
import { ChatMarkdown } from './chat.markdown'
import { ChatDelegation, ChatTools } from './chat.tools'
import {
  liveRuns,
  openTimelineItem,
  type DelegationItem,
  type LiveItem,
  type ToolItem,
} from './chat.live'
import type { PendingLine } from './chat.pending'
import { content } from '../shared/content'

interface Props {
  /** The finished lines — the only truth for text a turn has written (R-WP-11). */
  messages: ChatMessage[]
  /** Inputs this browser sent that the history does not carry yet — provisional lines, confirmed by
   *  their sender and position in it, not by their text (a queued input carries the core's marker;
   *  R-WP-37). */
  outgoing: PendingLine[]
  /** The live lines of the running turn, each anchored at the rows that already stood when it arrived. */
  live: LiveItem[]
  /** A turn this browser started is still open — the only thing that may paint a waiting frame (R-WP-31). */
  pending: boolean
  /** The identity of the turn this view holds open, or none: the only thing that may paint a working step,
   *  and it names WHICH turn — a step of a turn that ended never works for the next one (R-WP-40, UC-WP-72). */
  openTurn?: number | null
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

/**
 * A live item that speaks: an answer or a failure. A tool step, a diff block and a delegation are drawn as
 * themselves and never as a line of speech, so they are out of this type by construction (R-WP-28, R-WP-36,
 * R-WP-40).
 */
type SpokenItem = Exclude<LiveItem, { kind: 'tool' } | { kind: 'diff' } | { kind: 'delegation' }>

/**
 * One thing the log draws: a line of the conversation, a live answer, one merged box of tool steps, one diff
 * block, or one delegation. The last three are links of the timeline and never a voice of the conversation.
 */
type Entry =
  | { type: 'row'; row: Row }
  | { type: 'live'; item: SpokenItem }
  /** The steps of one tool run; `open` is the one entry the open turn works on and stands in at most one of
   *  the boxes the log draws — identity, so neither the cap nor the place in the list decides it. A
   *  delegation beside the box takes the indicator away from it (`openTimelineItem`). */
  | { type: 'tools'; items: ToolItem[]; open: ToolItem | DelegationItem | null }
  | { type: 'diff'; file: string; text: string }
  /** One delegation; `open` says this entry IS the one the log's single decider names — the entry's own end
   *  event and the identity of the open turn decide that, never a flag the wire carried (R-WP-40,
   *  UC-WP-72 GIVEN 3). */
  | { type: 'delegation'; item: DelegationItem; open: boolean }

/**
 * One project's conversation as the screen shows it: the history, the own inputs the history does not
 * carry yet, and the live lines of the running turn — each live line at the place it happened, so tool
 * activity stands between the parts of an answer it belongs to (R-WP-28, D-7).
 *
 * A row is speech and not a card — the accent line on its left names its voice, in the flow of the text
 * and never right-aligned. Voices separate by those accents alone: the log draws no line of its own
 * between two of them (R-WP-29). An answer reads as Markdown, so the model's headings, lists and code
 * blocks are what the user sees; the person's line stays the text he typed.
 *
 * A user's line always names its author — the person the server named, or the role label of the doc —
 * because the chat is shared and every viewer has to read the same thing (R-WP-25, UC-WP-48). An agent's
 * line stays nameless when the server named none: no "System", no "Peon", no invented agent (R-WP-4).
 * A thinking delta never reaches here (§3.3.3, Q7).
 *
 * Tool activity is not a voice: it keeps the grey of `chat.html`, and a step names only the agent the wire
 * stamped on it — never one the log invented (R-WP-37, R-WP-40). A diff block is of the same family — a fact
 * about the project's files, seen by everybody watching (R-WP-36).
 */
export function ChatLog({ messages, outgoing, live, pending, openTurn = null, logRef, onScroll }: Props) {
  const rows: Row[] = [
    ...messages.map((message): Row => ({
      voice: voiceOf(message),
      handle: `chat-line-${message.role}`,
      author: authorOf(message),
      text: message.text,
      // a line the history carries is a fact — the frame never comes back to it (R-WP-31)
      pending: false,
    })),
    ...outgoing.map((line): Row => ({
      voice: 'user',
      handle: 'chat-line-user',
      // the sender stamped at send time — the same value the history will hand back as `origin`, so the
      // line does not jump when it is confirmed (R-WP-37, UC-WP-48)
      author: line.origin ?? content.chat.you,
      text: line.text,
      // the frame is the page's call and not a property of the row: it ends with the turn, not with the
      // history read that confirms the text (R-WP-31, UC-WP-65)
      pending,
    })),
  ]

  const entries = entriesOf(rows, live, openTurn)

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
          return <ChatTools key={`tools-${index}`} items={entry.items} open={entry.open} />
        }
        if (entry.type === 'diff') {
          return <ChatDiff key={`diff-${index}`} file={entry.file} diff={entry.text} />
        }
        if (entry.type === 'delegation') {
          return <ChatDelegation key={`delegation-${index}`} item={entry.item} open={entry.open} />
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
              {failure ? (
                <span className="chat-text">{entry.item.text}</span>
              ) : (
                <ChatMarkdown text={entry.item.text} />
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
 * number of rows its turn was anchored at, and inside the run the arrival order holds. The anchor counts
 * what stood at that moment — the history plus the inputs that were still pending then — so it names the
 * same place before and after the read that confirms those inputs, and a run pointing past the rows that
 * stand today falls to the end (D-A, UC-WP-54). Consecutive tool lines become one box, a diff block stands
 * beside it as a block of its own (R-WP-36).
 */
function entriesOf(rows: Row[], live: LiveItem[], openTurn: number | null): Entry[] {
  const entries: Entry[] = []
  let shown = 0
  // one entry of the whole live list works: the last step of the turn that is open, be it a step or a
  // delegation — one decider for both, so the log cannot paint two indicators for one turn
  // (R-WP-40, UC-WP-72 GIVEN 2 and 3)
  const open = openTimelineItem(live, openTurn)

  const pushRows = (until: number) => {
    while (shown < Math.min(until, rows.length)) {
      entries.push({ type: 'row', row: rows[shown] })
      shown += 1
    }
  }

  for (const run of liveRuns(live)) {
    pushRows(run[0].afterLines)
    let tools: ToolItem[] = []
    const pushTools = () => {
      if (tools.length === 0) {
        return
      }
      entries.push({ type: 'tools', items: tools, open })
      tools = []
    }
    for (const item of run) {
      if (item.kind === 'tool') {
        tools.push(item)
        continue
      }
      if (item.kind === 'delegation') {
        // the entry ends the tool box before it and takes the indicator itself: identity decides, never a
        // second formula and never a place in the list (R-WP-40, UC-WP-72 GIVEN 3)
        pushTools()
        entries.push({ type: 'delegation', item, open: open === item })
        continue
      }
      pushTools()
      if (item.kind === 'diff') {
        // a diff is neither voice nor trace: it ends the tool run before it and stands as a block of its
        // own (R-WP-36)
        entries.push({ type: 'diff', file: item.file, text: item.text })
        continue
      }
      entries.push({ type: 'live', item })
    }
    pushTools()
  }
  pushRows(rows.length)
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
