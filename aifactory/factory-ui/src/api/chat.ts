import { request } from './client'

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

/**
 * Reads the SSE wire with `fetch` + `ReadableStream` instead of `EventSource`: the parsing is what
 * R-WP-5 promises, so it has to be observable in a test, and reconnecting stays the view's single
 * rule instead of a browser feature nobody can see. Parsed per spec — event boundary is the empty
 * line, `event:` plus every `data:` line of one event are joined, `:comment` is keep-alive noise,
 * and a chunk may split an event at any byte.
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

  const wire: Wire = { event: '', data: [], buffer: '' }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      wire.buffer += decoder.decode(value, { stream: true })
      for (;;) {
        const line = takeLine(wire)
        if (line === null) {
          break
        }
        readLine(line, wire, handlers)
      }
    }
  } catch {
    // an interrupted stream is not the view's problem — it reconnects and reads the history
  }
  if (!signal.aborted) {
    handlers.onEnd(true)
  }
}

interface Wire {
  event: string
  data: string[]
  buffer: string
}

/**
 * Cuts one complete line off the buffer. A lone `\r` at the very end stays: it may be the first half
 * of a CRLF whose second half has not arrived yet — guessing wrong would split an event.
 */
function takeLine(wire: Wire): string | null {
  const buffer = wire.buffer
  for (let index = 0; index < buffer.length; index++) {
    const char = buffer[index]
    if (char === '\n') {
      wire.buffer = buffer.slice(index + 1)
      return buffer.slice(0, index)
    }
    if (char === '\r') {
      if (index + 1 === buffer.length) {
        return null
      }
      const length = buffer[index + 1] === '\n' ? 2 : 1
      wire.buffer = buffer.slice(index + length)
      return buffer.slice(0, index)
    }
  }
  return null
}

function readLine(line: string, wire: Wire, handlers: ChatStreamHandlers): void {
  if (line === '') {
    dispatch(wire, handlers)
    return
  }
  if (line.startsWith(':')) {
    return
  }
  const colon = line.indexOf(':')
  const field = colon < 0 ? line : line.slice(0, colon)
  const value = colon < 0 ? '' : trimOneSpace(line.slice(colon + 1))
  if (field === 'event') {
    wire.event = value
  } else if (field === 'data') {
    wire.data.push(value)
  }
  // `id:` and `retry:` stay unread: the wire sets no id and there is no replay to resume from
}

function dispatch(wire: Wire, handlers: ChatStreamHandlers): void {
  const { event, data } = wire
  wire.event = ''
  wire.data = []
  if (data.length === 0) {
    return
  }
  const mapped = toEvent(event, data.join('\n'))
  if (mapped !== null) {
    handlers.onEvent(mapped)
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
      return { kind: 'error', message: String(fields.message ?? 'unbekannter Fehler') }
    default:
      return null
  }
}

function trimOneSpace(value: string): string {
  return value.startsWith(' ') ? value.slice(1) : value
}
