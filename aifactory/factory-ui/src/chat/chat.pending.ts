import type { ChatMessage } from './chat.client'

/**
 * One input this browser sent that the history does not carry yet (R-WP-37). `afterHistory` is how many
 * history lines stood when it was sent — its own line cannot precede it; `origin` is the sender as
 * `senderName` stamps it, and `null` is one sender class of its own (UC-WP-48).
 */
export interface PendingLine {
  text: string
  afterHistory: number
  origin: string | null
}

/**
 * The pending lines the history does not carry yet, in send order — the rule is R-WP-37/UC-WP-69.
 *
 * Stateless per read: each sender's waterline is the index of the last user line of that sender in the
 * history handed in, and a pending line is confirmed when that index reaches its `afterHistory`. So one
 * history line confirms every pending line of its sender sent before it (the core bundles queued
 * inputs into one memory line), a line of another sender confirms nothing, and nothing but `role` +
 * `origin` of the wire is known here — no marker is parsed.
 *
 * Returns the given array when nothing was confirmed.
 */
export function withoutConfirmed(pending: PendingLine[], history: ChatMessage[]): PendingLine[] {
  if (pending.length === 0) {
    return pending
  }
  const lastOf = new Map<string | null, number>()
  history.forEach((line, at) => {
    if (line.role === 'user') {
      lastOf.set(line.origin ?? null, at)
    }
  })
  const rest = pending.filter(line => {
    const last = lastOf.get(line.origin)
    return last === undefined || last < line.afterHistory
  })
  return rest.length === pending.length ? pending : rest
}

/**
 * The last user line the screen shows — what an empty send repeats (R-WP-30, UC-WP-57 GIVEN 1).
 *
 * The order is the log's, not the wire's: `chat.log.tsx` draws the history rows and this browser's pending
 * ones after them, so an outstanding pending line wins — it is the one at the bottom, and the one a stop left
 * behind (the page keeps the history after an accepted stop, D-16, so the last line of the history would be
 * the older question). A line of another sender can be younger and still stand above it; the visible place is
 * the rule, not the timestamp.
 *
 * The text goes out as it stands, sender prefix and queue marker included: `ProjectRuntime.history()` parses
 * the sender out of the text again and `submit` of the same class puts the prefix back (factory-backend
 * `src/main/java/org/sterl/open/aifactory/chat/ProjectRuntime.java:519-541`), so a copy of it cannot double
 * it. No read here, and no marker is parsed. A blank line answers `null`: the edge refuses a blank with 400,
 * so the empty way has to end before any request.
 */
export function lastVisibleUserText(history: ChatMessage[], pending: PendingLine[]): string | null {
  const line = pending.at(-1)?.text ?? history.findLast(message => message.role === 'user')?.text
  return line === undefined || line.trim() === '' ? null : line
}
