import { request } from '../shared/api.client'

/**
 * What a tile offers (R-WP-7, R-WP-26). The server's set is the whole rule — the UI never branches on an
 * agent name, so a new agent or a new affordance is a server change and not a new `if` here.
 */
export type TileCapability = 'CONTEXT' | 'CLEAR' | 'COMPACT' | 'TRANSCRIPT'

export type AgentTileState = 'IDLE' | 'WORKING' | 'COMPACTING' | 'ERROR'

/**
 * One agent's tile: a snapshot of live state (UC-WP-11). Nothing here is a conversation — the insight into
 * an agent's own chat is Inc 5c, and only a tile offering TRANSCRIPT opens one.
 */
export interface AgentTile {
  name: string
  /** the core's own number, formatted by the core; `null` where the tile carries no CONTEXT */
  context: string | null
  tokens: number
  /** whether `tokens` is an estimate — the core says so, the screen does not guess */
  estimate: boolean
  contextPercent: number
  budget: number
  state: AgentTileState
  capabilities: TileCapability[]
}

/** The wire value of an action — `compact` and `clear` are the two the server knows. */
export type AgentAction = 'clear' | 'compact'

/** The roster of this project's team — re-read on mount and after every action (re-connect ⇒ lesen). */
export async function listAgents(projectId: string): Promise<AgentTile[]> {
  return request<AgentTile[]>(`/api/projects/${encodeURIComponent(projectId)}/agents`)
}

/**
 * Clear or Compact one tile. 202 means <em>taken</em>, not done — the action runs on the project's turn
 * executor. 409 says a turn was running: the action was refused and nothing changed.
 */
export async function agentAction(
  projectId: string,
  name: string,
  action: AgentAction,
): Promise<void> {
  return request<void>(`/api/projects/${encodeURIComponent(projectId)}/agents`, {
    method: 'POST',
    body: JSON.stringify({ name, action }),
  })
}
