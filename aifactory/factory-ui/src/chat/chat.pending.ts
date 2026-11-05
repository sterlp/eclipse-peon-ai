import type { ChatMessage } from './chat.client'

/**
 * One input this browser sent that the history does not carry yet (R-WP-37). `afterHistory` is how many
 * history lines stood when it was sent — the position its own line cannot precede. That position is what
 * confirms the line later, never its text: the core writes a queued input with its marker
 * `[Queued Message] (hh:mm): `, so the history line says something else than what the user typed and a
 * text comparison would keep the provisional line on the screen forever (C-5, UC-WP-69).
 */
export interface PendingLine {
  text: string
  afterHistory: number
}

/**
 * The pending lines the history does not carry yet, in send order. Each one is confirmed by the first
 * not-yet-used user line at or behind its position — which knows no format (the queued marker is simply a
 * user line) and no text, so the same input twice is two lines (UC-WP-69) and an identically worded older
 * line in the history confirms nothing.
 *
 * A line that stays keeps the position the search reached: across reads, one history line may confirm one
 * pending line and no second one. Returns the given array when nothing was confirmed.
 */
export function withoutConfirmed(pending: PendingLine[], history: ChatMessage[]): PendingLine[] {
  if (pending.length === 0) {
    return pending
  }
  const rest: PendingLine[] = []
  let used = 0
  for (const line of pending) {
    const from = Math.max(used, line.afterHistory)
    const at = indexOfUserLine(history, from)
    if (at < 0) {
      rest.push(line.afterHistory === from ? line : { ...line, afterHistory: from })
      continue
    }
    used = at + 1
  }
  return rest.length === pending.length ? pending : rest
}

/** The first user line at or behind `from`, or -1 — a role is all of its shape the search knows. */
function indexOfUserLine(history: ChatMessage[], from: number): number {
  for (let at = from; at < history.length; at++) {
    if (history[at].role === 'user') {
      return at
    }
  }
  return -1
}
