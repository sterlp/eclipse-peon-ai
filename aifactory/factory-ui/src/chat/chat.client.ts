import { request } from '../shared/api.client'
import type { AgentTile } from './chat.agents.client'

/**
 * One line of the shared history. `origin` names who spoke — the agent that spoke for Jon's own line and
 * a team member's final answer, the person for a user's line (R-WP-25). A user's line without a name
 * carries none and the screen shows its role label; an agent's line without one stays unnamed, because
 * the server never guesses and the UI invents nothing (R-WP-4).
 *
 * `error` is the role of a failed turn (R-WP-34): it carries no origin by construction and its text is the
 * server's reason — the UI knows no wording of its own for it.
 *
 * `tool` is the fourth role (R-WP-47): one step of Jon's own trace, named by `origin` — the tool he used,
 * or the member a delegation went to — with `text` as the excerpt of what came back. An empty `text` is a
 * call whose result the memory does not hold: the step shows its arrival (UC-WP-82 GIVEN 2). A step is
 * never a voice: the log draws it in the timeline of its place, beside the answer it led to. A status line
 * and a diff stay live-only and never arrive here (R-WP-11, R-WP-36).
 */
export interface ChatMessage {
  role: 'user' | 'jon' | 'error' | 'tool'
  text: string
  origin?: string | null
}

/** A step of the trace: a line of the log and never a voice. It is the one line a history read may hand back
 *  at any place, so an anchor never counts it (R-WP-47); the log draws it in the timeline of its place. */
export function isTraceLine(line: ChatMessage): boolean {
  return line.role === 'tool'
}

/**
 * A live event of the running turn (§3.3.3): `partial` is a <em>delta</em>, `tool` one tool line, `problem`
 * the core's own report that a tool refused (R-WP-61), `done` closes one <em>turn</em> — an input that queued
 * up behind a running one is closed by that turn's `done` too — and `error` is a turn failure; the composer
 * stays usable either way. Finished text of anybody, team member included, is never an event: it arrives
 * with the history (`done` reads it).
 *
 * `agents` is the one event that is not part of a conversation: the whole tile roster, every time. The UI
 * <em>replaces</em> its list with it and accumulates nothing, so a snapshot can never become a second
 * history (R-WP-7) and a lost one costs nothing — the roster is read again on mount.
 *
 * `diff` is one file the turn changed: `file` is the project-relative path, `diff` the unified diff the
 * core computed — never the file's content, and no author, because a change of this project belongs to
 * everybody watching it (R-WP-36). Live only, exactly like `tool`.
 */
export type ChatStreamEvent =
  | { kind: 'partial'; thinking: boolean; text: string }
  /** `origin` is the core's stamp of the agent that worked — absent, null or a name; the screen names a
   *  step only where the wire carries one (R-WP-37, R-WP-40). */
  | { kind: 'tool'; text: string; origin?: string | null }
  /** The core's own report that a tool refused (R-WP-61) — its own event and never a `tool` line, because the
   *  screen paints a refusal where a step cannot. `origin` follows the rule of a step: a name only where the
   *  core named one. Live only, like a step: the Bestand of the turn carries the same words as a step. */
  | { kind: 'problem'; text: string; origin?: string | null }
  | { kind: 'diff'; file: string; diff: string }
  /** One delegation of the turn: `member` is the team member the order went to and `working` says whether it
   *  began or ended — both fields, always. No task text: neither of the core's two listeners carries a
   *  prompt, so the order keeps the wording of the tool line above it (R-WP-40, UC-WP-72 GIVEN 3). */
  | { kind: 'delegation'; member: string; working: boolean }
  | { kind: 'done'; millis: number }
  | { kind: 'error'; message: string }
  | { kind: 'agents'; tiles: AgentTile[] }

export interface ChatStreamHandlers {
  onEvent: (event: ChatStreamEvent) => void
  /**
   * The stream's answer arrived: from here on the server delivers every event of this project to this view,
   * so a state read taken now cannot miss the end of something that already ended (R-WP-48). Optional —
   * only a viewer that shows state it has to re-read cares whether the connection stands.
   */
  onOpen?: () => void
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
 * What a name is — one definition for the wire and for the confirmation of a pending line (R-WP-25).
 * The server trims the same way before it writes the prefix, so a pending line stamped here carries the
 * very `origin` the history will hand back (R-WP-37). No name: no field on the wire, no prefix in the
 * memory, `origin` stays null (UC-WP-48).
 */
export function senderName(user?: string | null): string | null {
  const name = user?.trim()
  return name ? name : null
}

/**
 * Fire and forget — the server answers 202 with no body whether or not Jon is busy, because a busy
 * project queues the input instead of refusing it (R-WP-10).
 *
 * `user` is the name of this browser (R-WP-25): a label of the sender, never an addressee, and it is left
 * out of the body entirely when the browser holds none — then the request is exactly `{ text }` and the
 * server writes no prefix (UC-WP-48).
 */
export async function sendMessage(projectId: string, text: string, user?: string | null): Promise<void> {
  const name = senderName(user)
  return request<void>(`/api/projects/${encodeURIComponent(projectId)}/messages`, {
    method: 'POST',
    body: JSON.stringify({ text, ...(name ? { user: name } : {}) }),
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
