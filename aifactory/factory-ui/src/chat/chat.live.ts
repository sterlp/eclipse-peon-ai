/**
 * The live lines of the running turn (D-7): one list instead of the old streaming text plus a separate
 * tool list, because the order in which the two arrive IS the conversation.
 *
 * An item carries its anchor: the number of rows the log showed when its <em>turn</em> reported its first
 * line — one number for every item of one turn, not one per item. The anchor is a count and not a text, so
 * it survives the history read that `done` triggers: the finished line lands in the log, the tool lines of
 * that turn stay where they happened, and an input the user queues mid-turn cannot move the answer that is
 * still streaming (UC-WP-54).
 */
export type LiveItem =
  /** `afterLines` — the anchor of the item's turn: rows of the log (history plus the own, unconfirmed
   *  inputs) that stood when that turn reported its first line. Same number for every item of one turn. */
  | { kind: 'text' | 'error'; text: string; afterLines: number }
  /** One step of the turn's tool activity. `origin` is the wire's stamp of the agent that worked and `at`
   *  the moment this browser received it — both per item, so the cap of a long run keeps the times of what
   *  stays (R-WP-40). Neither is ever guessed: no stamp, no name; no time, no title. `turn` names the turn
   *  that reported the step: the anchor counts rows and two turns can count the same number, so which step
   *  works is decided by the turn's identity and never by its place. */
  | {
      kind: 'tool'
      text: string
      afterLines: number
      origin: string | null
      at: number | null
      turn: number | null
    }
  /** One Think section of the turn, drawn as an entry of the trace like a step (R-WP-40). `text` is the
   *  wire's wording and never Markdown, `at` the moment its FIRST delta reached this browser, `turn` the
   *  identity of the turn that reported it. The wire names no author of a thought, so the entry names none. */
  | { kind: 'think'; text: string; afterLines: number; at: number | null; turn: number | null }
  /** One file the turn changed: `text` is its unified diff, `file` the path the block names. */
  | { kind: 'diff'; file: string; text: string; afterLines: number }
  /** One delegation of the turn. `member` is the member the order went to and `at` the moment this browser
   *  received its start; `turn` is the identity of the turn it belongs to and `ended` is what the wire
   *  said — true only once this member's own end arrived, never derived from a place. There is no `open`
   *  field on purpose: open is `turn === openTurn && !ended`, so an entry of a turn that ended cannot work
   *  for the next one (R-WP-40, UC-WP-72 GIVEN 3). */
  | {
      kind: 'delegation'
      member: string
      afterLines: number
      turn: number | null
      at: number | null
      ended: boolean
    }

/** One step of a tool run, as the timeline draws it. */
export type ToolItem = Extract<LiveItem, { kind: 'tool' }>

/** One Think section, as the timeline draws it. */
export type ThinkItem = Extract<LiveItem, { kind: 'think' }>

/** One delegation, as the timeline draws it. */
export type DelegationItem = Extract<LiveItem, { kind: 'delegation' }>

/**
 * One entry of the trace — the steps, the Think sections and the delegations of a turn, which the screen
 * draws in ONE timeline (R-WP-40). A diff and a text of the answer are of no such family: the first is a
 * block of its own (R-WP-36), the second is a voice.
 */
export type TraceItem = ToolItem | ThinkItem | DelegationItem

/** What `chat.html` keeps of a tool block: the tail, so a chatty turn cannot push the answer away. */
export const MAX_TOOL_LINES = 10

/** A text chunk continues the live answer when that answer is the last thing on the screen. */
export function appendLiveText(items: LiveItem[], text: string, afterLines: number): LiveItem[] {
  const last = items[items.length - 1]
  if (last !== undefined && last.kind === 'text' && last.afterLines === afterLines) {
    return [...items.slice(0, -1), { ...last, text: last.text + text }]
  }
  return [...items, { kind: 'text', text, afterLines }]
}

/**
 * Every tool line is its own item; the log merges the ones that stand in a row (R-WP-28). `origin` and `at`
 * are optional because the wire may carry neither, and the screen invents nothing (R-WP-37, R-WP-40).
 * `turn` is the identity of the turn that reported the step — without it the step can never work.
 */
export function appendLiveTool(
  items: LiveItem[],
  text: string,
  afterLines: number,
  origin: string | null = null,
  at: number | null = null,
  turn: number | null = null,
): LiveItem[] {
  return [...items, { kind: 'tool', text, afterLines, origin, at, turn }]
}

/**
 * A Think delta continues the Think entry that stands last, and only that one: the wire streams one thought
 * in pieces and one thought is ONE entry of the timeline. A delta behind a step, behind an answer or behind
 * a thought of another turn opens a new entry — the merge is by the turn's identity and never by a place,
 * so a second turn cannot append itself to the first one's thought (R-WP-40, UC-WP-72 GIVEN 1). `at` stays
 * the moment of the FIRST delta: an entry that has been growing since 09:05 is titled by 09:05.
 */
export function appendLiveThink(
  items: LiveItem[],
  text: string,
  afterLines: number,
  at: number | null = null,
  turn: number | null = null,
): LiveItem[] {
  const last = items[items.length - 1]
  if (last !== undefined && last.kind === 'think' && last.turn === turn) {
    return [...items.slice(0, -1), { ...last, text: last.text + text }]
  }
  return [...items, { kind: 'think', text, afterLines, at, turn }]
}

/**
 * The ONE entry of the timeline that works: the last step of the turn this view holds open — a tool step, a
 * Think section or a delegation entry, never both, because this is the only place the log asks who works
 * (R-WP-40, UC-WP-72 GIVEN 2 and 3). A step of a turn that has ended never works for the next one, and an
 * open turn that has reported no step of its own has no indicator — the core reports no end of a single
 * tool, so only the next step, the entry's own end or the end of the turn takes the indicator away. The
 * answer a turn is writing, and the file changes it reports, are steps of neither: a step keeps its
 * indicator while the answer streams to it.
 */
export function openTimelineItem(
  items: LiveItem[],
  turn: number | null,
): TraceItem | null {
  if (turn === null) {
    return null
  }
  for (let index = items.length - 1; index >= 0; index -= 1) {
    const item = items[index]
    if (item.kind === 'tool' || item.kind === 'think') {
      // a Think section is a step of the account like a tool: it works while it is the last thing the turn
      // reported and stops with the next one (R-WP-40)
      return item.turn === turn ? item : null
    }
    if (item.kind === 'delegation') {
      // the wire opened it, so the entry IS the last step of its turn: the step above it works no longer,
      // and its own end ends it without giving the indicator back to that step (UC-WP-72 GIVEN 3)
      return item.turn === turn && !item.ended ? item : null
    }
    if (item.kind === 'text' || item.kind === 'diff') {
      continue
    }
    // a failure is an end of the answer, so nothing that stands behind it works
    return null
  }
  return null
}

/**
 * The clock of one step's arrival, from the browser's own local time: the wire carries the moment and
 * nothing else, never a duration (R-WP-40). Built from the date's own parts and not from a locale format,
 * so the same instant reads the same on every machine and the proof does not depend on one.
 */
export function arrivalTime(at: number): string {
  const arrived = new Date(at)
  const hours = String(arrived.getHours()).padStart(2, '0')
  const minutes = String(arrived.getMinutes()).padStart(2, '0')
  return `${hours}:${minutes}`
}

/**
 * The failure of the running turn, shown where it happened — the log, not a paragraph beside it. It is its
 * own item and never joined to an answer, and `liveLinesAfterHistory` drops it on purpose: the history read
 * that follows the event brings the same line as a line of the conversation (R-WP-34, UC-WP-64).
 */
export function appendLiveError(items: LiveItem[], text: string, afterLines: number): LiveItem[] {
  return [...items, { kind: 'error', text, afterLines }]
}

/**
 * One file the turn changed, as its own item (R-WP-36): never merged into the tool box, because a diff is
 * a block that names its file, and the trace beside it is the running account of what the turn reported.
 */
export function appendLiveDiff(
  items: LiveItem[],
  file: string,
  diff: string,
  afterLines: number,
): LiveItem[] {
  return [...items, { kind: 'diff', file, text: diff, afterLines }]
}

/**
 * A delegation of the turn, opened by its own wire event (R-WP-40, UC-WP-72 GIVEN 3). Like a step it carries
 * the identity of the turn that reported it — the anchor alone cannot tell two turns apart — and it carries
 * no order text, because the wire hands none: the task stays the wording of the tool line above it.
 */
export function appendLiveDelegation(
  items: LiveItem[],
  member: string,
  afterLines: number,
  at: number | null = null,
  turn: number | null = null,
): LiveItem[] {
  return [...items, { kind: 'delegation', member, afterLines, turn, at, ended: false }]
}

/**
 * Closes the open entry of one member. The end event of the core carries neither a turn nor a call id
 * (`CallStats.CallStatsListener` hands `(CallStats, AiAgent)`), so the member is the only handle there is:
 * the last entry of his the wire has not closed is the entry his end ends. `turn` is the identity this view
 * holds open — a number naming another turn closes nothing, and `null` closes without claiming to know
 * which turn the end belongs to. Nothing to close answers with **the same list**: an end never invents an
 * entry and never adds a second line (UC-WP-72 GIVEN 3).
 */
export function endLiveDelegation(items: LiveItem[], member: string, turn: number | null): LiveItem[] {
  for (let index = items.length - 1; index >= 0; index -= 1) {
    const item = items[index]
    if (item.kind !== 'delegation' || item.ended || item.member !== member) {
      continue
    }
    if (turn !== null && item.turn !== turn) {
      continue
    }
    return [...items.slice(0, index), { ...item, ended: true }, ...items.slice(index + 1)]
  }
  return items
}

/**
 * What a history read leaves of the live list: the answer has become a line of the history and would
 * stand there twice, while a Think section, a diff block and a delegation entry exist on the wire only —
 * losing them at `done` is exactly what UC-WP-54 forbids, and R-WP-36 puts its block in the same family,
 * as does R-WP-40 its delegation and its thinking.
 *
 * A tool step belongs to the history's family since R-WP-47: the read carries it as a line of its own, so
 * a live step that stayed would stand twice under one answer — one widget, not two (R-WP-40). What
 * arrived behind `mark` is another matter: the read could not know it, so it stays until the next read.
 *
 * `mark` is how many items stood when that read was issued. Everything behind it arrived while the server
 * was already answering, so the read cannot carry it and it stays on the screen: a read may only give up
 * what it could know. Without the mark the read of a finished turn deletes the text of the turn that is
 * already streaming, and since chunks are deltas the head of that answer is gone until the next read
 * (UC-WP-7). The default is the whole list — a read that names no mark knows everything it found.
 */
export function liveLinesAfterHistory(items: LiveItem[], mark = items.length): LiveItem[] {
  return items.filter(
    (item, index) =>
      item.kind === 'think' ||
      item.kind === 'diff' ||
      item.kind === 'delegation' ||
      index >= mark,
  )
}

/**
 * The runs of one anchor, in arrival order. One run is one place in the log; inside it the log keeps the
 * order of the items, so an answer interrupted by tool activity reads as it happened.
 */
export function liveRuns(items: LiveItem[]): LiveItem[][] {
  const runs: LiveItem[][] = []
  for (const item of items) {
    const run = runs[runs.length - 1]
    if (run !== undefined && run[0].afterLines === item.afterLines) {
      run.push(item)
    } else {
      runs.push([item])
    }
  }
  return runs
}


/**
 * What the waiting bar of the running turn shows (R-WP-31): the last thing the stream reported, on one
 * line. It is a preview beside the log and never a second copy of the answer, so markup shows as typed.
 * A Think section needs no branch of its own: it has a text and reads like one.
 */
export function livePreview(items: LiveItem[]): string {
  const last = items[items.length - 1]
  if (last === undefined) {
    return ''
  }
  if (last.kind === 'diff') {
    // a body line of a diff names no file; the bar names what changed (R-WP-31, UC-WP-68)
    return last.file
  }
  if (last.kind === 'delegation') {
    // a delegation names a member and nothing else — which is exactly what the bar has to say here
    // (R-WP-31, UC-WP-72 GIVEN 3)
    return last.member
  }
  const lines = last.text.split('\n').filter(line => line.trim() !== '')
  return lines[lines.length - 1] ?? ''
}
