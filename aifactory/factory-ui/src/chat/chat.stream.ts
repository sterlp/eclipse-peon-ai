import { readSseStream } from '../shared/sse.client'
import type { ChatStreamEvent, ChatStreamHandlers } from './chat.client'
import type { AgentTile } from './chat.agents.client'
import { content } from '../shared/content'

/**
 * Reads the SSE wire with `fetch` + `ReadableStream` instead of `EventSource`: the parsing is what
 * R-WP-5 promises, so it has to be observable in a test, and reconnecting stays the view's single
 * rule instead of a browser feature nobody can see. The protocol itself is `shared/sse.client.ts`.
 *
 * `agentName` chooses the wire, not the reading: with a name this is the live channel of that one
 * member (R-WP-53), without one it is the project's own conversation. Both speak the same events.
 */
export async function openChatStream(
  projectId: string,
  handlers: ChatStreamHandlers,
  signal: AbortSignal,
  agentName?: string,
): Promise<void> {
  const project = `/api/projects/${encodeURIComponent(projectId)}`
  const path =
    agentName === undefined
      ? `${project}/events`
      : `${project}/agents/${encodeURIComponent(agentName)}/events`
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

  if (!signal.aborted) {
    // the answer <em>is</em> the subscription: the server registered this emitter before it answered, so
    // whatever the view reads now, no event of this connection can have passed it unread (R-WP-48)
    handlers.onOpen?.()
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
      return { kind: 'tool', text: String(fields.text ?? ''), origin: stampOf(fields) }
    case 'problem':
      // a refusal of a tool is its own event and keeps the stamp rule of a step — one rule, one wording
      // (R-WP-37, R-WP-61)
      return { kind: 'problem', text: String(fields.text ?? ''), origin: stampOf(fields) }
    case 'done':
      return { kind: 'done', millis: Number(fields.millis ?? 0) }
    case 'error':
      return { kind: 'error', message: String(fields.message ?? content.chat.unknownError) }
    case 'diff': {
      // the pair or nothing: a block without its file would be a statement without a subject, and a block
      // without its diff an empty promise of a change (R-WP-36)
      if (typeof fields.file !== 'string' || typeof fields.diff !== 'string') {
        return null
      }
      return { kind: 'diff', file: fields.file, diff: fields.diff }
    }
    case 'delegation': {
      // the pair or nothing, like `diff`: an entry naming no member could never be closed again, and one
      // without its flag would not say whether it opens or ends (R-WP-40, UC-WP-72 GIVEN 3)
      if (typeof fields.member !== 'string' || fields.member.trim() === '') {
        return null
      }
      if (typeof fields.working !== 'boolean') {
        return null
      }
      return { kind: 'delegation', member: fields.member, working: fields.working }
    }
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

/**
 * The core's stamp of the agent a line belongs to, read off a payload: absent, of another type or empty is no
 * author, and the screen then names nobody (R-WP-37). One rule for both lines a tool reports — its step and
 * its refusal — so the two can never drift apart.
 */
function stampOf(fields: Record<string, unknown>): string | null {
  return typeof fields.origin === 'string' && fields.origin !== '' ? fields.origin : null
}
