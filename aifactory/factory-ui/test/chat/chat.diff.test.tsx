import { afterEach, expect, test, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'

import { ChatLog } from '../../src/chat/chat.log'
import { appendLiveDiff, appendLiveTool, livePreview } from '../../src/chat/chat.live'
import { openChatStream } from '../../src/chat/chat.stream'
import { declarations } from '../shared/sheets'
import { separatorsOf } from '../shared/log.parts'
import type { ChatMessage, ChatStreamEvent } from '../../src/chat/chat.client'

const QUESTION: ChatMessage = { role: 'user', text: 'nimm die 3 zur 4', origin: 'Paul' }
const FILE = 'db/seed.sql'

/**
 * One unified diff as the wire carries it: headers, hunk mark, context, a removal, an addition. The removed
 * line is a comment of the file itself and so reads `--- …` — the case that makes the header rule of
 * `lineClassOf` a decision and not a guess.
 */
const DIFF = [
  `--- a/${FILE}`,
  `+++ b/${FILE}`,
  '@@ -1,4 +1,4 @@',
  ' insert into cfg values (1);',
  '--- eine alte Konstante',
  '+-- die neue Konstante',
  ' insert into cfg values (2);',
  ' ',
  // the break a diff ends with, which becomes the trailing empty entry `split` leaves behind
  '',
].join('\n')

/** The class of one rendered diff line without its shared handle. */
function kindOf(line: HTMLElement): string {
  return line.className.replace('chat-diff-line ', '')
}

afterEach(() => {
  vi.unstubAllGlobals()
})

/**
 * A change is a block of its own: it names the file it changed, colours the lines by what they are, and
 * keeps the headers, because what the screen shows should be the diff and not an interpretation of it.
 * jsdom paints no colour, so the contract is read off the sheet — see `test/shared/sheets.ts`.
 */
// UC-WP-68
test('aChangeIsABlockWithItsFileAndItsColouredLines', () => {
  render(
    <ChatLog
      messages={[QUESTION]}
      outgoing={[]}
      live={appendLiveDiff([], FILE, DIFF, 1)}
    />,
  )

  expect(screen.getByTestId('chat-diff-file')).toHaveTextContent(FILE)

  const lines = screen.getAllByTestId('chat-diff-line')
  expect(lines.map(kindOf)).toEqual([
    'diff-meta',
    'diff-meta',
    'diff-meta',
    'diff-context',
    // a removed comment of the file reads like a header and stays a removal (R-WP-36 shows what changed)
    'diff-removed',
    'diff-added',
    'diff-context',
    'diff-context',
  ])
  // the break belongs to the box model and not to the text: both together double every line of the block
  expect(lines.every(line => !line.textContent?.includes('\n')), 'a line breaks by its box').toBe(true)

  // the doc asks for `+` and `-` coloured, not for a palette: which token answers is the look's, so what is
  // pinned is that the two kinds read in two of our colours and never in one (R-WP-36)
  const added = declarations('.chat-log .chat-diff .diff-added')
  const removed = declarations('.chat-log .chat-diff .diff-removed')
  expect(added, 'an addition is coloured with a token of ours').toMatch(/color:\s*var\(--/)
  expect(removed, 'a removal is coloured with a token of ours').toMatch(/color:\s*var\(--/)
  expect(removed, 'a removal never reads like an addition').not.toBe(added)
  expect(declarations('.chat-log .chat-diff .chat-diff-line'), 'every line is a box of its own').toContain(
    'display: block',
  )
})

/** The other half of the same rule: the block is beside the trace, never inside it, and draws no voice line. */
// UC-WP-68
// UC-WP-55
test('aDiffStandsBesideTheTraceAndNotInsideIt', () => {
  const trace = appendLiveTool(
    appendLiveDiff(appendLiveTool([], 'write the file', 1), FILE, DIFF, 1),
    'read it back',
    1,
  )
  render(<ChatLog messages={[QUESTION]} outgoing={[]} live={trace} />)

  // the diff interrupts the trace run: one timeline before it, one after, because a timeline is one place
  // of the trace and the block beside it is not a link of it
  expect(screen.getAllByTestId(/^chat-(timeline|diff)$/).map(entry => entry.getAttribute('data-testid'))).toEqual(
    ['chat-timeline', 'chat-diff', 'chat-timeline'],
  )
  for (const widget of screen.getAllByTestId('chat-timeline')) {
    expect(within(widget).queryByTestId('chat-diff'), 'a block with its own file joins no timeline').toBeNull()
  }
  // and it is nobody's speech, so it neither begins a voice nor separates two (R-WP-29, UC-WP-55 GIVEN 2) —
  // read over the whole log, where `separatorsOf` alone decides which line counts as one
  expect(separatorsOf(screen.getByTestId('chat-log'))).toEqual([])
})

/** A `diff` event that carries only half its pair is the server's mistake, not a block to guess at. */
// UC-WP-68
test('aDiffEventWithoutItsFieldsIsDropped', async () => {
  const half = await readWire(`event:diff\ndata:{"file":"${FILE}"}\n\n`)
  expect(half, 'a block without its diff would promise a change nobody showed').toEqual([])
  render(<ChatLog messages={[QUESTION]} outgoing={[]} live={[]} />)
  expect(screen.queryByTestId('chat-diff')).toBeNull()

  expect(await readWire(`event:diff\ndata:{"file":"${FILE}","diff":"--- a/${FILE}"}\n\n`)).toEqual([
    { kind: 'diff', file: FILE, diff: `--- a/${FILE}` },
  ])
})

/** The waiting bar reports what the stream last said; for a block that is the file it names. */
test('theWaitingPreviewNamesTheFile', () => {
  expect(livePreview(appendLiveDiff([], FILE, DIFF, 1))).toBe(FILE)
  expect(livePreview(appendLiveDiff(appendLiveTool([], 'ls', 1), FILE, DIFF, 1))).toBe(FILE)
  // unchanged for everything else: the last non-empty line of what the turn reported
  expect(livePreview(appendLiveTool([], 'ls in the checkout', 1))).toBe('ls in the checkout')
})

/** One wire chunk through the real SSE reader and the real mapping — dropping is a property of that pair. */
async function readWire(chunk: string): Promise<ChatStreamEvent[]> {
  const seen: ChatStreamEvent[] = []
  const body = new ReadableStream<Uint8Array>({
    start(opened) {
      opened.enqueue(new TextEncoder().encode(chunk))
      opened.close()
    },
  })
  vi.stubGlobal(
    'fetch',
    vi.fn(
      async () =>
        new Response(body, { status: 200, headers: { 'content-type': 'text/event-stream' } }),
    ),
  )
  await openChatStream(
    'demo-1a2b3c4',
    { onEvent: event => seen.push(event), onEnd: () => {} },
    new AbortController().signal,
  )
  return seen
}
