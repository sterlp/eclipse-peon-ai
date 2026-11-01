import { request } from '../shared/api.client'
import type { AgentTile } from './chat.agents.client'

/**
 * One line of the shared history. `origin` names who spoke — the agent that spoke for Jon's own line and
 * a team member's final answer, the person for a user's line (R-WP-25). A user's line without a name
 * carries none and the screen shows its role label; an agent's line without one stays unnamed, because
 * the server never guesses and the UI invents nothing (R-WP-4).
 */
export interface ChatMessage {
  role: 'user' | 'jon'
  text: string
  origin?: string | null
}

/**
 * A live event of the running turn (§3.3.3): `partial` is a <em>delta</em>, `tool` one tool or problem
 * line, `done` closes one <em>turn</em> — an input that queued up behind a running one is closed by that
 * turn's `done` too — and `error` is a turn failure; the composer stays usable either way. Finished text
 * of anybody, team member included, is never an event: it arrives with the history (`done` reads it).
 *
 * `agents` is the one event that is not part of a conversation: the whole tile roster, every time. The UI
 * <em>replaces</em> its list with it and accumulates nothing, so a snapshot can never become a second
 * history (R-WP-7) and a lost one costs nothing — the roster is read again on mount.
 */
export type ChatStreamEvent =
  | { kind: 'partial'; thinking: boolean; text: string }
  | { kind: 'tool'; text: string }
  | { kind: 'done'; millis: number }
  | { kind: 'error'; message: string }
  | { kind: 'agents'; tiles: AgentTile[] }

export interface ChatStreamHandlers {
  onEvent: (event: ChatStreamEvent) => void
  /**
   * The connection ended on its own. `reconnect` is false only when the server <em>answered</em> a
   * status: a project without a chat will not become reachable by asking again.
   */
  onEnd: (reconnect: boolean) => void
}

export async function listMessages(projectId: string): Promise<ChatMessage[]> {
  return request<ChatMessage[]>(`/api/projects/${encodeURIComponent(projectId)}/messages`)
}

/**
 * Fire and forget — the server answers 202 with no body whether or not Jon is busy, because a busy
 * project queues the input instead of refusing it (R-WP-10).
 */
export async function sendMessage(projectId: string, text: string): Promise<void> {
  return request<void>(`/api/projects/${encodeURIComponent(projectId)}/messages`, {
    method: 'POST',
    body: JSON.stringify({ text }),
  })
}

/**
 * End the turn that is running (UC-WP-50). It belongs to the conversation's own edge: it writes nothing,
 * starts nothing and names no addressee — the same single conversation, only shorter.
 *
 * 202 means the stop was <em>taken</em>; the end itself arrives as `done` on the live wire. 409 means no
 * turn was running — the caller treats that as "already over" and shows no message of its own, because a
 * refusal here denies the user nothing he wanted.
 */
export async function stopTurn(projectId: string): Promise<void> {
  return request<void>(`/api/projects/${encodeURIComponent(projectId)}/messages/stop`, {
    method: 'POST',
  })
}
