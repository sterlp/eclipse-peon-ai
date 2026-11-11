import { useCallback, useRef } from 'react'
import type { RefObject } from 'react'

import { listMessages, type ChatMessage } from './chat.client'
import { liveLinesAfterHistory, type LiveItem } from './chat.live'
import { withoutConfirmed, type PendingLine } from './chat.pending'
import { errorMessage } from '../shared/api.client'

/**
 * The reads of one conversation (R-WP-11, UC-WP-7): a connect, a dropped stream and the end of a turn each read
 * the history, and only the newest answer may stand on the screen. What a read writes stays with the page — its
 * lists and the counts beside them come in as writers. Owned here is the round trip alone: the number a read
 * carries, the answer that came too late, and the live cut that belongs to the moment the read went out (D-C).
 */
export interface HistoryReads {
  /**
   * One read of the conversation, numbered and stamped with the live list it has to meet — by the caller when
   * the read answers to a moment of its own (the end of a tile action, R-WP-63), always <em>before</em> the
   * request: what the stream adds while the server answers is not in the answer (D-C, UC-WP-7).
   */
  refresh(mark?: number): Promise<void>
}

/**
 * A read is a network round trip over the whole memory of the conversation, so it is slowest exactly when the
 * conversation is long, and a later read can answer first. `readSeq` is what tells the two answers apart, and
 * `appliedRead` which of them the screen shows (D-C, R-WP-11) — both live only as long as one read of one
 * project is in flight, so they are the module's own and never a second memory of the conversation.
 */
export function useHistoryReads(args: {
  projectId: string | null
  /** the own inputs the history does not carry yet — a read confirms them by position (R-WP-37) */
  pendingLines: RefObject<PendingLine[]>
  /** the live lines on the screen; the read drops what its own moment cannot have (D-B, UC-WP-7) */
  liveItems: RefObject<LiveItem[]>
  writeHistory(loaded: ChatMessage[]): void
  writePending(lines: PendingLine[]): void
  writeLive(next: LiveItem[]): void
  /** a read that failed is the page's trouble, shown in its one panel — the read itself keeps the screen as it stands */
  onFailure(message: string): void
  /**
   * Called after a read has really been applied — never after one dropped as older, which is why this runs here
   * and not beside the caller's `refresh`: the page's mark of a stopped turn must outlive a lost round trip
   * (R-WP-63). Identity-stable, so `refresh` stays one callback and the stream is not re-opened by a render.
   */
  afterRead(): void
}): HistoryReads {
  const { projectId, pendingLines, liveItems, writeHistory, writePending, writeLive, onFailure, afterRead } = args
  const readSeq = useRef(0)
  /** The number of the read whose content is on the screen. An older answer never overwrites a newer one. */
  const appliedRead = useRef(0)

  /**
   * The finished text arrives here, never as an event — so the live answer gives way to it (§3.3.3). What
   * gives way is nothing else: the tool lines of the turn exist on the wire only and stay where they were
   * placed, because a finished turn without its trace is not what UC-WP-54 asks for.
   *
   * Two things make a read a statement about one moment instead of about the conversation: `read` is the
   * number this view gave the request, and a read that answers after a newer one has been applied is
   * dropped — reads race, and the slower one would move the log back (R-WP-11). `mark` is how many live
   * lines stood when the request went out; text and error behind it belong to a turn the read never saw and
   * stay on the screen (UC-WP-7). The same cut moved the row a stopped turn's rows hang at (R-WP-63).
   */
  const applyHistory = useCallback(
    (loaded: ChatMessage[], mark: number, read: number) => {
      if (read < appliedRead.current) {
        return
      }
      appliedRead.current = read
      writeHistory(loaded)
      // his own lines are confirmed by their position in what the read brings, not by their text: a queued
      // input stands in the memory with the core's marker and would never be recognised again (R-WP-37)
      writePending(withoutConfirmed(pendingLines.current, loaded))
      writeLive(liveLinesAfterHistory(liveItems.current, mark))
      afterRead()
    },
    [afterRead, liveItems, pendingLines, writeHistory, writeLive, writePending],
  )

  const refresh = useCallback(
    (mark = liveItems.current.length): Promise<void> => {
      if (projectId === null) {
        return Promise.resolve()
      }
      const read = ++readSeq.current
      return listMessages(projectId)
        .then(loaded => applyHistory(loaded, mark, read))
        .catch(reason => onFailure(errorMessage(reason)))
    },
    [applyHistory, liveItems, onFailure, projectId],
  )

  return { refresh }
}
