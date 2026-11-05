import { readSseStream } from '../shared/sse.client'
import type { ProjectWork } from '../shared/dto'

/**
 * The two words of the work-state edge (R-WP-24): the whole picture when a viewer connects, one project
 * whenever one of them changes.
 */
export type ProjectStatusEvent =
  | { kind: 'snapshot'; work: ProjectWork[] }
  | { kind: 'delta'; work: ProjectWork }

export interface ProjectStatusHandlers {
  onEvent(event: ProjectStatusEvent): void
  /** The stream ended; `retry` means it ended by itself, so asking again is what brings the states back. */
  onEnd(retry: boolean): void
}

/**
 * The work states of every project over one connection. Same transport as the conversation — `fetch` on
 * the event stream, so the wire is what a test reads and reconnecting is the view's own rule — because
 * the row of the project list changes while the list stands there, without anyone reloading it (UC-WP-47).
 */
export async function openProjectStatusStream(
  handlers: ProjectStatusHandlers,
  signal: AbortSignal,
): Promise<void> {
  let response: Response
  try {
    response = await fetch('/api/projects/events', {
      headers: { accept: 'text/event-stream' },
      signal,
    })
  } catch {
    // no answer at all (a restarting server) — asking again is what brings the states back
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
    // an interrupted stream is not the view's problem — it reconnects and reads the list again
  }
  if (!signal.aborted) {
    handlers.onEnd(true)
  }
}

/** An event name or a payload this client does not know is the server's addition, not an error. */
function toEvent(name: string, payload: string): ProjectStatusEvent | null {
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
  if (name === 'projects') {
    // a snapshot replaces what the view holds and never adds to it — the states are a picture, and a
    // picture that accumulated would become a second store (R-WP-24)
    return Array.isArray(fields.projects)
      ? { kind: 'snapshot', work: fields.projects as ProjectWork[] }
      : null
  }
  if (name === 'project') {
    return typeof fields.projectId === 'string' ? { kind: 'delta', work: fields as unknown as ProjectWork } : null
  }
  return null
}
