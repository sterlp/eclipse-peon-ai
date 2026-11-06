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
   *  stays (R-WP-40). Neither is ever guessed: no stamp, no name; no time, no title. */
  | { kind: 'tool'; text: string; afterLines: number; origin: string | null; at: number | null }
  /** One file the turn changed: `text` is its unified diff, `file` the path the block names. */
  | { kind: 'diff'; file: string; text: string; afterLines: number }

/** One step of a tool run, as the timeline draws it. */
export type ToolItem = Extract<LiveItem, { kind: 'tool' }>

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
 */
export function appendLiveTool(
  items: LiveItem[],
  text: string,
  afterLines: number,
  origin: string | null = null,
  at: number | null = null,
): LiveItem[] {
  return [...items, { kind: 'tool', text, afterLines, origin, at }]
}

/**
 * The step that is working: the last thing the wire reported, and only while a turn is open. The core
 * reports no end of a single tool, so this is the whole meaning of "working" — the next step or the end of
 * the turn takes the indicator away (R-WP-40, UC-WP-72 GIVEN 2).
 */
export function openStep(items: LiveItem[], running: boolean): ToolItem | undefined {
  const last = items[items.length - 1]
  return running && last !== undefined && last.kind === 'tool' ? last : undefined
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
 * What a history read leaves of the live list: the answer has become a line of the history and would
 * stand there twice, while the tool lines and the diff blocks exist on the wire only — losing them at
 * `done` is exactly what UC-WP-54 forbids, and R-WP-36 puts its block in the same family.
 *
 * `mark` is how many items stood when that read was issued. Everything behind it arrived while the server
 * was already answering, so the read cannot carry it and it stays on the screen: a read may only give up
 * what it could know. Without the mark the read of a finished turn deletes the text of the turn that is
 * already streaming, and since chunks are deltas the head of that answer is gone until the next read
 * (UC-WP-7). The default is the whole list — a read that names no mark knows everything it found.
 */
export function liveLinesAfterHistory(items: LiveItem[], mark = items.length): LiveItem[] {
  return items.filter((item, index) => item.kind === 'tool' || item.kind === 'diff' || index >= mark)
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
  const lines = last.text.split('\n').filter(line => line.trim() !== '')
  return lines[lines.length - 1] ?? ''
}