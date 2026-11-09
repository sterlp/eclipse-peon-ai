import type { Ref } from 'react'


import type { ChatMessage } from './chat.client'
import { ChatDiff } from './chat.diff'
import { ChatMarkdown } from './chat.markdown'
import { ChatTraceTimeline } from './chat.timeline'
import {
  liveRuns,
  openTimelineItem,
  type LiveItem,
  type ToolItem,
  type TraceItem,
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
  /** The identity of the turn this view holds open, or none: the only thing that may paint a working step,
   *  and it names WHICH turn — a step of a turn that ended never works for the next one (R-WP-40, UC-WP-72). */
  openTurn?: number | null
  /** The scroll area of the chat, handed to the page: it keeps the end of the log in view (R-WP-35). */
  logRef?: Ref<HTMLUListElement>
  /** The page learns from this where the user reads (R-WP-35, UC-WP-66). */
  onScroll?: () => void
}

/**
 * One row of the log as the screen shows it (R-WP-29): whose speech it is and who the server named as its
 * author.
 */
interface Row {
  /** The voice of the row, and with it its colour: the person's blue, the agent's green, a failure's red. */
  voice: 'user' | 'agent' | 'error'
  /** The handle of the row on the wire: a team answer arrives as `jon` with an author, never as a third role. */
  handle: string
  author: string | null
  text: string
  /** A step of the trace and not a voice: it never draws a row of its own, only the timeline of its place. */
  trace: boolean
}

/**
 * A live item that speaks: an answer or a failure. A tool step, a Think section, a diff block and a
 * delegation are drawn as themselves and never as a line of speech, so they are out of this type by
 * construction (R-WP-28, R-WP-36, R-WP-40). A Think section is what a model thought on its way to the
 * answer and not the answer: only what the turn says is speech (R-WP-28).
 */
type SpokenItem = Exclude<
  LiveItem,
  { kind: 'tool' } | { kind: 'think' } | { kind: 'diff' } | { kind: 'delegation' }
>

/**
 * One thing the log draws: a line of the conversation, a live answer, one timeline of the trace, or one diff
 * block. The last two are links of the account of what a turn did and never a voice of the conversation.
 */
type Entry =
  | { type: 'row'; row: Row }
  | { type: 'live'; item: SpokenItem }
  /** The trace of one place of the log: its steps, its Think sections and its delegations in the order they
   *  arrived, in the one timeline they share (R-WP-40). `open` is the one entry the open turn works on and
   *  stands in at most one of the widgets the log draws — identity, so neither the cap nor the place in the
   *  list decides it. `restored` marks the group the history handed back: it is the same account as the live
   *  entries beside it, so those join this widget instead of drawing a second one. */
  | { type: 'trace'; items: TraceItem[]; open: TraceItem | null; restored?: boolean }
  | { type: 'diff'; file: string; text: string }

/**
 * One project's conversation as the screen shows it: the history, the own inputs the history does not
 * carry yet, and the live lines of the running turn — each live line at the place it happened, so tool
 * activity stands between the parts of an answer it belongs to (R-WP-28, D-7).
 *
 * Every voice fills the measure of the log (R-WP-29): the person's own input as the reference's blue bubble,
 * an answer as a card on the page's own surface with a border of its own. Colour and border tell the voices
 * apart and never a side — no row carries an accent edge, none is centred or right-aligned, none leaves the
 * flow, and the log draws no line of its own between two rows. An answer reads as Markdown, so the model's
 * headings, lists and code blocks are what the user sees; the person's line stays the text he typed.
 *
 * A user's line always names its author — the person the server named, or the role label of the doc —
 * because the chat is shared and every viewer has to read the same thing (R-WP-25, UC-WP-48). An agent's
 * line stays nameless when the server named none: no "System", no "Peon", no invented agent (R-WP-4).
 * A thinking delta is no speech either — it is an entry of the trace, a link of the account of what the
 * turn did, and it names no author because the wire stamps none on a thought (R-WP-40).
 *
 * Tool activity is not a voice: it keeps the grey of `chat.html`, and a step names only the agent the wire
 * stamped on it — never one the log invented (R-WP-37, R-WP-40). A diff block is of the same family — a fact
 * about the project's files, seen by everybody watching (R-WP-36).
 */
export function ChatLog({ messages, outgoing, live, openTurn = null, logRef, onScroll }: Props) {
  const rows: Row[] = [
    ...messages.map((message): Row => ({
      voice: voiceOf(message),
      handle: `chat-line-${message.role}`,
      author: authorOf(message),
      text: message.text,
      // the step the memory handed back (R-WP-47): its name is the Bestand's and it joins the timeline
      // of its place instead of speaking as a row of its own
      trace: message.role === 'tool',
    })),
    ...outgoing.map((line): Row => ({
      voice: 'user',
      handle: 'chat-line-user',
      // the sender stamped at send time — the same value the history will hand back as `origin`, so the
      // line does not jump when it is confirmed (R-WP-37, UC-WP-48)
      author: line.origin ?? content.chat.you,
      text: line.text,
      trace: false,
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
        if (entry.type === 'trace') {
          return (
            <li className="chat-line trace" data-testid="chat-timeline" key={`trace-${index}`}>
              <ChatTraceTimeline items={entry.items} open={entry.open} />
            </li>
          )
        }
        if (entry.type === 'diff') {
          return <ChatDiff key={`diff-${index}`} file={entry.file} diff={entry.text} />
        }
        if (entry.type === 'live') {
          // a live failure is no answer to read: its text is the server's reason, shown as it is (R-WP-34)
          const failure = entry.item.kind === 'error'
          return (
            <li
              key={`live-${index}`}
              className={failure ? 'chat-line error' : 'chat-line agent'}
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
            className={`chat-line ${row.voice}`}
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
 * stand today falls to the end (D-A, UC-WP-54). The steps, Think sections and delegations of one run become
 * ONE timeline, a diff block stands beside it as a block of its own (R-WP-36, R-WP-40). A step the history
 * handed back is a row like any other and groups by the same rule (R-WP-47): consecutive steps are one
 * widget, and the live entries beside them join it instead of drawing a second account of one place.
 */
function entriesOf(rows: Row[], live: LiveItem[], openTurn: number | null): Entry[] {
  const entries: Entry[] = []
  let shown = 0
  // one entry of the whole live list works: the last step of the turn that is open, be it a step or a
  // delegation — one decider for both, so the log cannot paint two indicators for one turn
  // (R-WP-40, UC-WP-72 GIVEN 2 and 3)
  const open = openTimelineItem(live, openTurn)

  // one widget per uninterrupted group (R-WP-40): the steps the Bestand handed back are the same account as
  // the live entries beside them, so a group joins the widget that stood first instead of opening a second
  // one for one place. Two live runs of two turns never join — only the read moves a step of the same run
  // next to the entries that streamed live.
  const pushTrace = (items: TraceItem[], openItem: TraceItem | null, restored: boolean) => {
    const previous = entries[entries.length - 1]
    if (previous !== undefined && previous.type === 'trace' && (previous.restored === true || restored)) {
      entries[entries.length - 1] = {
        type: 'trace',
        // the restored step stands at its row and the live entries arrived behind it, so a group the read
        // hands back leads the merged account — the wire's order, not the order the two groups were pushed
        items:
          restored && previous.restored !== true
            ? [...items, ...previous.items]
            : [...previous.items, ...items],
        open: previous.open ?? openItem,
        restored: true,
      }
      return
    }
    entries.push({ type: 'trace', items, open: openItem, restored })
  }

  const pushRows = (until: number) => {
    while (shown < Math.min(until, rows.length)) {
      const row = rows[shown]
      if (!row.trace) {
        entries.push({ type: 'row', row })
        shown += 1
        continue
      }
      // R-WP-40 for the restored trace: steps that stand in a row are ONE widget at the place of the
      // first, and a voice, a failure or the anchor of a live run ends the group. Each step still counts
      // as one row, so the anchor of a live run keeps naming the same place before and after the read.
      const steps: TraceItem[] = []
      while (shown < Math.min(until, rows.length) && rows[shown].trace) {
        steps.push(restoredStep(rows[shown]))
        shown += 1
      }
      pushTrace(steps, null, true)
    }
  }

  for (const run of liveRuns(live)) {
    pushRows(run[0].afterLines)
    // the trace of this place: the steps, the Think sections and the delegations of one run are ONE account
    // and end only where the account is interrupted — by a voice, by a failure, by a block of its own, or by
    // the next turn (R-WP-40: a message of the model ends the group; a diff is a block of its own, R-WP-36)
    let trace: TraceItem[] = []
    const flushTrace = () => {
      if (trace.length === 0) {
        return
      }
      pushTrace(trace, open, false)
      trace = []
    }
    for (const item of run) {
      if (item.kind === 'tool' || item.kind === 'think' || item.kind === 'delegation') {
        // a Think section and a delegation join the run instead of breaking it: the indicator still moves to
        // them, because ONE decider names the entry that works (`openTimelineItem`) and not the order of the
        // widgets
        trace.push(item)
        continue
      }
      flushTrace()
      if (item.kind === 'diff') {
        // a diff is neither voice nor trace: it ends the trace before it and stands as a block of its
        // own (R-WP-36)
        entries.push({ type: 'diff', file: item.file, text: item.text })
        continue
      }
      entries.push({ type: 'live', item })
    }
    flushTrace()
  }
  pushRows(rows.length)
  return entries
}

/**
 * A step the memory handed back, drawn as what it is (R-WP-47): `origin` is the Bestand's name and `text`
 * his excerpt. `at` stays null — the history knows no moment, so the entry carries no title (UC-WP-72
 * GIVEN 5) — and `turn` stays null, because no turn works on a line that is already written. `afterLines`
 * is the anchor a live run places itself by; the log has already placed this one.
 */
function restoredStep(row: Row): ToolItem {
  return { kind: 'tool', text: row.text, afterLines: 0, origin: row.author, at: null, turn: null }
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
