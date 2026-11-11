import { useCallback, useRef, useState } from 'react'
import type { RefObject } from 'react'

import { isAlreadyOver, sendMessage, senderName, stopTurn } from './chat.client'
import type { PendingLine } from './chat.pending'
import { errorMessage } from '../shared/api.client'

/**
 * What the page knows about the turn it started, and the two controls of the composer (UC-WP-50, UC-WP-57):
 * the send that opens a turn, the stop that ends it, and the one message the page's panel shows.
 *
 * The anchor of the turn is deliberately <b>not</b> here: which rows stood when the turn reported its first
 * line, and the identity of the open turn, belong to the stream logic that writes the live list
 * (`chat.page.tsx`, D-A/R-WP-47). This module only asks for what it cannot know — the live row the stopped
 * turn began at (R-WP-63) and the board's roster read, which it gets as an argument of `stop`.
 */
export interface TurnControls {
  /** this browser opened a turn and has not seen it end (D-13) */
  ownTurn: boolean
  error: string | null
  /** stable (React setter) — the page's one panel message, also written by the roster read */
  setError(message: string | null): void
  send(text: string): Promise<void>
  /**
   * Stop the running turn (UC-WP-50). `from` is the live row its lines began at (R-WP-63, the page's anchor
   * block knows it); `refreshRoster` is the board's read, called once the click changed its stand.
   */
  stop(from: number | null, refreshRoster: () => void): Promise<void>
  /** stable: the end of a turn as the page reports it (`done`, `error`) */
  endTurn(): void
  /** stable: `true` when the `done` arriving is the one of this project's stopped turn; consumes the id */
  takeStoppedTurn(): boolean
  /** stable: the live row the stopped turn began at, or `null` — the rebuild's mark (R-WP-63) */
  stoppedFrom(): number | null
  /** stable: forgets that row once a history read has landed (`applyHistory`) */
  forgetStoppedFrom(): void
}

/**
 * The turn this browser started, held by project id: switching the project ends it here without an effect
 * writing state (UC-WP-50). The stop flag follows the <em>intent</em>, not the answer (D-13) — 202 only says
 * "taken", and a stop that comes one moment later must already address this turn.
 */
export function useTurnControls(args: {
  projectId: string | null
  user?: string | null
  /** what the last read holds, in the two units that read it (chat.page.tsx, R-WP-47) */
  historyCount: RefObject<{ lines: number; voices: number }>
  /** the own inputs the history does not carry yet (R-WP-37) */
  pendingLines: RefObject<PendingLine[]>
  writePending(lines: PendingLine[]): void
  followNow(): void
}): TurnControls {
  const { projectId, user, historyCount, pendingLines, writePending, followNow } = args
  const [error, setError] = useState<string | null>(null)
  const [runningTurn, setRunningTurn] = useState<string | null>(null)
  /** The turn he stopped: its project, whose `done` reads nothing (D-16), and the live row its rows began at. */
  const stoppedTurn = useRef<{ projectId: string | null; from: number | null }>({ projectId: null, from: null })

  const ownTurn = runningTurn !== null && runningTurn === projectId

  const endTurn = useCallback(() => setRunningTurn(null), [])

  const takeStoppedTurn = useCallback(() => {
    if (stoppedTurn.current.projectId !== projectId) {
      return false
    }
    // UC-WP-50: after an accepted stop the lines he has seen stay on the screen — there is no
    // second store to read them back from, and a history read would throw them away (D-16, R-WP-11)
    stoppedTurn.current.projectId = null
    return true
  }, [projectId])

  const stoppedFrom = useCallback(() => stoppedTurn.current.from, [])

  const forgetStoppedFrom = useCallback(() => {
    stoppedTurn.current.from = null
  }, [])

  /**
   * One text into this project's conversation — whether the field wrote it or an empty send repeated the last
   * line the screen shows makes no difference on the wire (UC-WP-57): the same edge, the same queue while a
   * turn runs (R-WP-10). Never reached with a blank text: the backend answers 400, so the empty way ends in
   * `submit` before any request.
   */
  const send = useCallback(
    async (text: string) => {
      if (projectId === null) {
        return
      }
      // his own input is always shown, wherever he had read up to (UC-WP-80) — before the line is written, so
      // the follow is re-armed when the effect that carries the new line runs
      followNow()
      // shown before the server has it: a turn writes the user line into the memory only when it starts,
      // so a queued input would vanish from the screen until the next history read. The number of history
      // lines standing now and this browser's sender name are what confirm the line later — the name is
      // normalised exactly as the wire carries it, or the history would never confirm it (R-WP-37, D-H)
      writePending([
        ...pendingLines.current,
        { text, afterHistory: historyCount.current.lines, origin: senderName(user) },
      ])
      setError(null)
      // the send is what opens the turn, so the flag follows the intent and not the answer (D-13): 202 only
      // says "taken", and a stop that comes one moment later must already address this turn
      setRunningTurn(projectId)
      stoppedTurn.current.projectId = null
      try {
        // the name of this browser goes with it, and only when there is one (R-WP-25, UC-WP-48)
        await sendMessage(projectId, text, user)
      } catch (reason) {
        writePending(pendingLines.current.slice(0, -1))
        setRunningTurn(null)
        setError(errorMessage(reason))
      }
    },
    [followNow, historyCount, pendingLines, projectId, setError, user, writePending],
  )

  /**
   * Stop the running turn (UC-WP-50). A 409 is not shown: it says the turn was already over, which is
   * what the click wanted, and the roster is read again so the tiles say so too (D-17). Any other
   * refusal is a line in the chat like any other error.
   */
  const stop = useCallback(
    async (from: number | null, refreshRoster: () => void) => {
      if (projectId === null) {
        return
      }
      setError(null)
      try {
        await stopTurn(projectId)
        // remembered for the `done` that follows: his lines stay, the history is not re-read (D-16)
        stoppedTurn.current = { projectId, from }
      } catch (reason) {
        if (!isAlreadyOver(reason)) {
          setError(errorMessage(reason))
          return
        }
      }
      // the end he asked for has been taken: the wait ends now, without waiting for a `done` that will not
      // read anything over it — the lines on the screen stay (R-WP-31, UC-WP-50, D-16)
      setRunningTurn(null)
      refreshRoster()
    },
    [projectId, setError],
  )

  return {
    ownTurn,
    error,
    setError,
    send,
    stop,
    endTurn,
    takeStoppedTurn,
    stoppedFrom,
    forgetStoppedFrom,
  }
}
