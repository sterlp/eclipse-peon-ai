/**
 * One event of an event-stream: its `event:` name (empty when the server named none) and the `data:`
 * lines of that event, joined the way the wire joins them.
 */
export interface SseMessage {
  event: string
  data: string
}

/**
 * The wire reader of every event stream this app opens — a conversation and the work states speak the
 * same protocol, so its corner cases are parsed in one place: the event boundary is the empty line,
 * `event:` plus every `data:` line of one event are joined, `:comment` is keep-alive noise, and a chunk
 * may split an event at any byte.
 *
 * Resolves when the body ends and rejects when the read breaks — what either means is the caller's
 * business, not the protocol's.
 */
export async function readSseStream(
  body: ReadableStream<Uint8Array>,
  onMessage: (message: SseMessage) => void,
): Promise<void> {
  const wire: Wire = { event: '', data: [], buffer: '' }
  const reader = body.getReader()
  const decoder = new TextDecoder()
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
      readLine(line, wire, onMessage)
    }
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

function readLine(line: string, wire: Wire, onMessage: (message: SseMessage) => void): void {
  if (line === '') {
    dispatch(wire, onMessage)
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
  // `id:` and `retry:` stay unread: the wires of this app set no id and have no replay to resume from
}

function dispatch(wire: Wire, onMessage: (message: SseMessage) => void): void {
  const { event, data } = wire
  wire.event = ''
  wire.data = []
  if (data.length === 0) {
    return
  }
  onMessage({ event, data: data.join('\n') })
}

function trimOneSpace(value: string): string {
  return value.startsWith(' ') ? value.slice(1) : value
}
