import type { AgentAction, AgentTile, AgentTileState } from './chat.agents.client'

/**
 * What the surface knows about a tile action it asked for, and the one moment that action is over for the
 * conversation it changed (R-WP-63, UC-WP-100).
 *
 * The server answers the click with `202` — <em>taken</em>, not done: Clear and Compact run on the project's
 * turn executor, so a history read right there reads the conversation as it stood before the action and shows
 * exactly the stale stand the rule was written for. What ends the action is the tile's own report: the core
 * state area of the action (`COMPACTING` for Compact, `WORKING` for Clear, from the claimed action on) and the
 * first snapshot that leaves it again. No event of its own, no field of its own — the roster already carries
 * both edges (R-WP-60).
 */
export interface ActionWatch {
  /** the tile the click stood on, as the roster names it */
  name: string
  /** the state the action shows while it runs */
  region: AgentTileState
  /**
   * How many live lines stood when the action was accepted: everything behind it is the action's own trace
   * (the core writes a Compact into no memory) and survives the rebuild, while everything before it is what
   * the read replaces.
   */
  mark: number
  /** the area has been seen — from here on, leaving it is the falling edge and not a snapshot too early */
  rising: boolean
}

/**
 * The seat of the conversation this page shows: Clear and Compact, and <b>no</b> insight into a conversation
 * of its own — the backend's seat table (`AgentTiles.java:35-41`, capabilities per seat). A member owns all
 * four capabilities and his conversation is his own: his action changes nothing here (R-WP-26, Grenze 2 of
 * R-WP-63). The capability set is the only handle the surface has — it names no agent (D-14) and reads no
 * order of the roster, which is a backend detail and no contract.
 */
function isOwnConversation(tile: AgentTile): boolean {
  return tile.capabilities.includes('COMPACT') && !tile.capabilities.includes('TRANSCRIPT')
}

/**
 * The watch for an action the user asked for, or `null` when its tile is not this page's conversation. Called
 * <em>before</em> the request: the server publishes the tile's start snapshot on the request thread, so that
 * snapshot can reach the browser before the answer does (R-WP-60).
 */
export function acceptedAction(
  tile: AgentTile | undefined,
  action: AgentAction,
  mark: number,
): ActionWatch | null {
  if (tile === undefined || !isOwnConversation(tile)) {
    return null
  }
  return { name: tile.name, region: action === 'compact' ? 'COMPACTING' : 'WORKING', mark, rising: false }
}

/**
 * One roster snapshot through the watch: the tile in the action's area arms it, the first snapshot outside
 * that area after the arming is the end of the action and asks for the rebuild — with the mark the acceptance
 * left, never the mark of the moment. Then the watch is spent: one rebuild per accepted action.
 *
 * Anything else answers with the watch as it stands. A snapshot that names the tile not at all says nothing
 * about the end of its action, and a snapshot <em>outside</em> the area before it was ever seen is a stand
 * from before the claim (or a turn the action was refused for) — treating it as the end would read the old
 * conversation back over the screen, which is the defect this rule replaced (D-4).
 */
export function afterSnapshot(
  watch: ActionWatch | null,
  tiles: AgentTile[],
  onRebuild: (mark: number) => void,
): ActionWatch | null {
  if (watch === null) {
    return null
  }
  const tile = tiles.find(entry => entry.name === watch.name)
  if (tile === undefined || tile.state === watch.region) {
    return tile === undefined || watch.rising ? watch : { ...watch, rising: true }
  }
  if (!watch.rising) {
    return watch
  }
  onRebuild(watch.mark)
  return null
}
