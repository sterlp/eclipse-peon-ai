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
