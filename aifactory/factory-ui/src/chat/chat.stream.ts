import { readSseStream } from '../shared/sse.client'
import type { ChatStreamEvent, ChatStreamHandlers } from './chat.client'
import type { AgentTile } from './chat.agents.client'
import { content } from '../shared/content'

/**
 * Reads the SSE wire with `fetch` + `ReadableStream` instead of `EventSource`: the parsing is what
 * R-WP-5 promises, so it has to be observable in a test, and reconnecting stays the view's single
 * rule instead of a browser feature nobody can see. The protocol itself is `shared/sse.client.ts`.
 */
export async function openChatStream(
  projectId: string,
  handlers: ChatStreamHandlers,
  signal: AbortSignal,
): Promise<void> {
  const path = `/api/projects/${encodeURIComponent(projectId)}/events`
  let response: Response
  try {
    response = await fetch(path, { headers: { accept: 'text/event-stream' }, signal })
  } catch {
    // no answer at all (a restarting server) — asking again is what brings the chat back
    if (!signal.aborted) {
      handlers.onEnd(true)
    }
    return
  }
  if (!response.ok || response.body === null) {
    if (!signal.aborted) {
      handlers.onEnd(false)
    }
    return
  }

  try {
    await readSseStream(response.body, message => {
      const mapped = toEvent(message.event, message.data)
      if (mapped !== null) {
        handlers.onEvent(mapped)
      }
    })
  } catch {
    // an interrupted stream is not the view's problem — it reconnects and reads the history
  }
  if (!signal.aborted) {
    handlers.onEnd(true)
  }
}

/** An event name or a payload this client does not know is the server's addition, not an error. */
function toEvent(name: string, payload: string): ChatStreamEvent | null {
  let body: unknown
  try {
    body = JSON.parse(payload)
  } catch {
    return null
  }
  if (body === null || typeof body !== 'object') {
    return null
  }
  const fields = body as Record<string, unknown>
  switch (name) {
    case 'partial':
      return { kind: 'partial', thinking: fields.kind === 'think', text: String(fields.text ?? '') }
    case 'tool':
      return { kind: 'tool', text: String(fields.text ?? '') }
    case 'done':
      return { kind: 'done', millis: Number(fields.millis ?? 0) }
    case 'error':
      return { kind: 'error', message: String(fields.message ?? content.chat.unknownError) }
    case 'agents': {
      // the whole roster, and the view replaces its list with it — a snapshot never accumulates, so it
      // cannot grow into a second history (R-WP-7); a payload without a list is not my error to guess
      if (!Array.isArray(fields.tiles)) {
        return null
      }
      return { kind: 'agents', tiles: fields.tiles as AgentTile[] }
    }
    default:
      return null
  }
}
