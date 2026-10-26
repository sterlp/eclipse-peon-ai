import { request } from '../shared/api.client'

/** One line of the shared history — `jon` is the only author on the agent side, roles carry no name. */
export interface ChatMessage {
  role: 'user' | 'jon'
  text: string
}

/**
 * A live event of the running turn (§3.3.3): `partial` is a <em>delta</em>, `done` closes one
 * answered input and `error` is a turn failure — the composer stays usable either way.
 */
export type ChatStreamEvent =
  | { kind: 'partial'; thinking: boolean; text: string }
  | { kind: 'tool'; text: string }
  | { kind: 'done'; millis: number }
  | { kind: 'error'; message: string }

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
