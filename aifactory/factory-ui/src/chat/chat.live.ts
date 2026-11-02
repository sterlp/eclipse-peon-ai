/**
 * The live lines of the running turn (D-7): one list instead of the old streaming text plus a separate
 * tool list, because the order in which the two arrive IS the conversation.
 *
 * An item carries the number of rows the log already showed when it arrived — its anchor. The anchor is a
 * count and not a text, so it survives the history read that `done` triggers: the finished line lands in
 * the log, and the tool lines of that turn stay where they happened (UC-WP-54).
 */
export interface LiveItem {
  kind: 'text' | 'tool'
  text: string
  /** Rows of the log (history plus the own, unconfirmed inputs) at the moment this item arrived. */
  afterLines: number
}

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

/** Every tool line is its own item; the log merges the ones that stand in a row (R-WP-28). */
export function appendLiveTool(items: LiveItem[], text: string, afterLines: number): LiveItem[] {
  return [...items, { kind: 'tool', text, afterLines }]
}

/**
 * What a history read leaves of the live list: the answer has become a line of the history and would
 * stand there twice, while the tool lines exist on the wire only — losing them at `done` is exactly what
 * UC-WP-54 forbids.
 */
export function liveLinesAfterHistory(items: LiveItem[]): LiveItem[] {
  return items.filter(item => item.kind === 'tool')
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

