import { useCallback, useRef, useState } from 'react'

import { listAgents, type AgentTile } from './chat.agents.client'
import { errorMessage } from '../shared/api.client'

/**
 * The team's board: which stand of the roster is the fresher one (R-WP-48, UC-WP-84 GIVEN 3).
 *
 * The board has two producers and they race: reading `…/agents` is a round trip, while an `agents` snapshot is
 * pushed by the stream. So the answer of a read that went out while a turn still worked can arrive after the
 * snapshot that ended it, and would paint the wait back over a board that is already ready — the wait nobody
 * will end (R-WP-31). One counter settles it, claimed by both producers: a read takes its number
 * <em>before</em> the request, a snapshot takes the next one when it arrives, and an answer whose number is no
 * longer the highest loses. It is the read order the conversation already keeps beside the history
 * (`readSeq` and `appliedRead`), only for the tiles.
 *
 * A module of its own rather than five lines in the page: the question above is the board's own responsibility
 * — the page keeps the decision of <em>when</em> to read and what a failure becomes — and the page stands at
 * the size boundary of AGENTS.md:10-12, which the counter and its two guards would not fit under.
 */
export function useTileBoard(projectId: string | null, onFailure: (message: string) => void): TileBoard {
  const [tiles, setTiles] = useState<AgentTile[]>([])
  /** The freshest claim: only the producer still holding it may write the board. */
  const stamp = useRef(0)

  const refreshTiles = useCallback((): Promise<void> => {
    if (projectId === null) {
      return Promise.resolve()
    }
    const claim = ++stamp.current
    return listAgents(projectId)
      .then(loaded => {
        if (claim === stamp.current) {
          setTiles(loaded)
        }
      })
      .catch(reason => onFailure(errorMessage(reason)))
  }, [onFailure, projectId])

  /**
   * A snapshot of the stream stands <b>now</b>: it takes the next number, so every read that began before it
   * loses. Replaced whole, never merged (R-WP-7).
   */
  const applySnapshot = useCallback((loaded: AgentTile[]) => {
    ++stamp.current
    setTiles(loaded)
  }, [])

  return { tiles, refreshTiles, applySnapshot }
}

export interface TileBoard {
  /** The board as the freshest stand left it. */
  tiles: AgentTile[]
  /** Read the roster, claiming the freshest stand before the request goes out. */
  refreshTiles(): Promise<void>
  /** The stream's snapshot of the whole roster: the new freshest stand (R-WP-7). */
  applySnapshot(loaded: AgentTile[]): void
}
