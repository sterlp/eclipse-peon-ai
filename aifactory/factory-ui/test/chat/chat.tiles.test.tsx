import { expect, test, vi } from 'vitest'
import { fireEvent, render, screen, within } from '@testing-library/react'

import { ChatTiles } from '../../src/chat/chat.tiles'
import { content } from '../../src/shared/content'
import type { AgentAction, AgentTile, AgentTileState } from '../../src/chat/chat.agents.client'

/**
 * The tile row as the screen shows it (R-WP-7, R-WP-32). Drawn from a fixture roster, because what these
 * tests guard is the <em>reading</em> of the server's answer: the number is shown as the core formatted it,
 * a missing number becomes a dash, and the capability set alone decides which buttons exist and which tile
 * is a door — no agent name is written anywhere in this file, and a rule that moved into the UI would show
 * up here as a red test.
 */
function tile(
  name: string,
  state: AgentTileState,
  overrides: Partial<AgentTile> = {},
): AgentTile {
  return {
    name,
    context: '12k',
    tokens: 12_345,
    estimate: false,
    contextPercent: 12,
    budget: 100_000,
    state,
    capabilities: ['CONTEXT', 'CLEAR', 'COMPACT'],
    ...overrides,
  }
}

const TEAM: AgentTile[] = [
  tile('Jon', 'IDLE'),
  tile('Plan', 'IDLE', { capabilities: ['CONTEXT', 'CLEAR', 'COMPACT', 'TRANSCRIPT'], context: '4k' }),
  tile('Dev', 'WORKING', { capabilities: ['CONTEXT', 'CLEAR', 'COMPACT', 'TRANSCRIPT'] }),
  tile('Review', 'IDLE', { capabilities: ['CONTEXT', 'CLEAR', 'COMPACT', 'TRANSCRIPT'], context: null }),
  tile('Search', 'IDLE', { capabilities: [], context: null, tokens: 0, contextPercent: 0, budget: 0 }),
]

function renderTeam(onAction = vi.fn()) {
  render(<ChatTiles tiles={TEAM} onAction={onAction} onInspect={vi.fn()} />)
  return onAction
}

// UC-WP-11
// UC-WP-79
// UC-WP-81
test('teamTilesShowContextAndStatus', () => {
  renderTeam()

  expect(screen.getAllByRole('listitem')).toHaveLength(TEAM.length)
  expect(screen.getByTestId('tile-Jon')).toHaveTextContent('Jon')
  expect(screen.getByTestId('tile-context-Jon')).toHaveTextContent('12k')
  expect(screen.getByTestId('tile-state-Jon')).toHaveTextContent(content.tiles.state.IDLE)
  expect(screen.getByTestId('tile-state-Dev')).toHaveTextContent(content.tiles.state.WORKING)
  expect(screen.getByTestId('tile-context-Plan')).toHaveTextContent('4k')
  // the dash is the server's `null`, never a computed 0 — a number nobody reported would be a lie
  expect(screen.getByTestId('tile-context-Review')).toHaveTextContent('—')
  // the state mark beside the word is the widget of R-WP-45, and an agent that works shows the ONE working
  // display — the same the open step of the log shows, not a second rendering of the same answer
  expect(within(screen.getByTestId('tile-state-Jon')).getByTestId('agent-status')).toBeInTheDocument()
  expect(
    within(screen.getByTestId('tile-state-Dev')).getByTestId('working-indicator'),
    'a working agent is drawn by the working indicator itself',
  ).toBeInTheDocument()
  // and the spin is the mark of a state that works — an idle agent shows its own circle and nothing turning
  expect(
    within(screen.getByTestId('tile-state-Jon')).queryByTestId('working-indicator'),
    'idle is not drawn as work',
  ).toBeNull()
})

// UC-WP-11
test('searchTileShowsNoContextAndNoButtons', () => {
  renderTeam()

  const search = screen.getByTestId('tile-Search')
  expect(search).toHaveTextContent('Search')
  expect(search).toHaveTextContent(content.tiles.state.IDLE)
  expect(screen.getByTestId('tile-context-Search')).toHaveTextContent('—')
  // a sub-loop owns no memory between calls: the row carries neither action (R-WP-7)
  expect(screen.queryByTestId('tile-clear-Search')).toBeNull()
  expect(screen.queryByTestId('tile-compact-Search')).toBeNull()
})

// UC-WP-13
// UC-WP-79
test('compactShowsItsOwnState', () => {
  render(
    <ChatTiles
      tiles={[tile('Dev', 'COMPACTING', { capabilities: ['CONTEXT', 'CLEAR', 'COMPACT', 'TRANSCRIPT'] })]}
      onAction={vi.fn()}
      onInspect={vi.fn()}
    />,
  )

  expect(screen.getByTestId('tile-state-Dev')).toHaveTextContent(content.tiles.state.COMPACTING)
  // a compact is work in the asking colour (R-WP-45): the mark is there, and it is not the accent's one —
  // the basis puts `classNames.indicator` on the cloned glyph, so the tone sits **under** the test handle
  expect(within(screen.getByTestId('tile-state-Dev')).getByTestId('agent-status')).toBeInTheDocument()
  const mark = within(screen.getByTestId('tile-state-Dev')).getByTestId('working-indicator')
  expect(mark.querySelector('.working-indicator-question'), 'a compact turns in the asking colour').not.toBeNull()
  // an agent that works or compacts offers nothing — a second action would write over the running one
  expect(screen.getByTestId('tile-clear-Dev')).toBeDisabled()
  expect(screen.getByTestId('tile-compact-Dev')).toBeDisabled()
})

// UC-WP-12
// UC-WP-79
test('clearStaysOfferedAfterAFailure', () => {
  render(
    <ChatTiles
      tiles={[
        tile('Jon', 'ERROR'),
        tile('Dev', 'WORKING', { capabilities: ['CONTEXT', 'CLEAR', 'COMPACT', 'TRANSCRIPT'] }),
      ]}
      onAction={vi.fn()}
      onInspect={vi.fn()}
    />,
  )

  expect(screen.getByTestId('tile-state-Jon')).toHaveTextContent(content.tiles.state.ERROR)
  // a failure of the agent is marked as one (R-WP-45) while the tile still offers its way out — the filled
  // sign of it, and read as decoration: the wrapper is `aria-hidden`, so no role query reaches inside it
  expect(within(screen.getByTestId('tile-state-Jon')).getByTestId('agent-status')).toBeInTheDocument()
  expect(
    within(screen.getByTestId('tile-state-Jon')).getByTestId('agent-status').querySelector('.agent-status-error'),
    'a failure carries the filled problem mark',
  ).not.toBeNull()
  // a failure is over: UC-WP-12 names no state condition and the server takes a Clear on an idle agent,
  // so a tile that stayed shut here would strand the only way out of the failed conversation
  expect(screen.getByTestId('tile-clear-Jon')).toBeEnabled()
  expect(screen.getByTestId('tile-compact-Jon')).toBeEnabled()
  // the state that really holds an agent still blocks it — that guard is the server's 409, seen as a button
  expect(screen.getByTestId('tile-clear-Dev')).toBeDisabled()
})

// UC-WP-12
test('actionNamesTheTileItWasClickedOn', () => {
  const seen: [string, AgentAction][] = []
  render(
    <ChatTiles
      tiles={TEAM}
      onAction={(name, action) => seen.push([name, action])}
      onInspect={vi.fn()}
    />,
  )

  fireEvent.click(screen.getByTestId('tile-clear-Plan'))
  fireEvent.click(screen.getByTestId('tile-compact-Jon'))

  expect(seen).toEqual([
    ['Plan', 'clear'],
    ['Jon', 'compact'],
  ])
})

// UC-WP-59
test('clickingATileOpensTheInsight', () => {
  const inspections = vi.fn()
  render(
    <ChatTiles
      tiles={[tile('Dev', 'IDLE', { capabilities: ['CONTEXT', 'CLEAR', 'COMPACT', 'TRANSCRIPT'] })]}
      onAction={vi.fn()}
      onInspect={inspections}
    />,
  )

  // what a tile is made of, in the order the eye reads it: the name as its title, number and status under
  // that, the actions last (R-WP-32, UC-WP-59 GIVEN 1) — a list of these is the row above the chat
  const dev = screen.getByTestId('tile-Dev')
  expect(Array.from(dev.querySelectorAll('.chat-tile-face > *')).map(part => part.className)).toEqual([
    'chat-tile-name',
    'chat-tile-meta',
    'chat-tile-actions',
  ])
  expect(dev.querySelector('.chat-tile-meta [data-testid="tile-context-Dev"]')).not.toBeNull()
  expect(dev.querySelector('.chat-tile-meta [data-testid="tile-state-Dev"]')).not.toBeNull()
  expect(dev.querySelector('.chat-tile-actions [data-testid="tile-clear-Dev"]')).not.toBeNull()

  // the tile is the door — no separate link has to be hit first (R-WP-32)
  const face = screen.getByTestId('tile-open-Dev')
  expect(face).toHaveAttribute('role', 'button')
  expect(face).toHaveAttribute('tabindex', '0')
  expect(screen.getByRole('button', { name: content.tiles.open('Dev') })).toBe(face)

  fireEvent.click(face)
  expect(inspections).toHaveBeenCalledTimes(1)
  expect(inspections).toHaveBeenCalledWith('Dev')

  // the keyboard opens the same door: a rule that only worked for a mouse would exclude half the users
  fireEvent.keyDown(face, { key: 'Enter' })
  fireEvent.keyDown(face, { key: ' ' })
  expect(inspections).toHaveBeenCalledTimes(3)
  // a key that means something else stays the browser's — Space must not scroll away under the tile
  fireEvent.keyDown(face, { key: 'Tab' })
  expect(inspections).toHaveBeenCalledTimes(3)

  // the hover is the visible promise of the click, and it is only drawn for a tile that opens something
  expect(declarations('.chat-tile-open'), 'a clickable tile reads as clickable').toContain('cursor: pointer')
  expect(declares('.chat-tile-open:hover'), 'the tile answers the pointer before it is clicked').toBe(true)
  expect(declares('.chat-tile-face:hover'), 'a tile with no door promises nothing').toBe(false)
})

// UC-WP-59
test('clearDoesNotOpenTheInsight', () => {
  const inspections = vi.fn()
  const actions = vi.fn()
  render(
    <ChatTiles
      tiles={[tile('Dev', 'IDLE', { capabilities: ['CONTEXT', 'CLEAR', 'COMPACT', 'TRANSCRIPT'] })]}
      onAction={actions}
      onInspect={inspections}
    />,
  )

  fireEvent.click(screen.getByTestId('tile-clear-Dev'))
  fireEvent.click(screen.getByTestId('tile-compact-Dev'))

  expect(actions.mock.calls).toEqual([
    ['Dev', 'clear'],
    ['Dev', 'compact'],
  ])
  // the action is the one thing the click must never become: Clear is not the Blick
  expect(inspections).not.toHaveBeenCalled()

  // the same for a key held on a focused action, which would otherwise reach the tile's own handler
  fireEvent.keyDown(screen.getByTestId('tile-clear-Dev'), { key: 'Enter' })
  expect(inspections).not.toHaveBeenCalled()
})

// UC-WP-59
test('tilesWithoutTranscriptAreNotClickable', () => {
  const inspections = vi.fn()
  render(
    <ChatTiles
      tiles={[tile('Jon', 'IDLE'), tile('Search', 'IDLE', { capabilities: [], context: null })]}
      onAction={vi.fn()}
      onInspect={inspections}
    />,
  )

  // neither a door nor the look of one — the capability set is the whole rule (D-14, R-WP-26)
  for (const name of ['Jon', 'Search']) {
    const tileElement = screen.getByTestId(`tile-${name}`)
    const faces = tileElement.querySelectorAll('.chat-tile-face')
    expect(faces, `${name} has a tile to read`).toHaveLength(1)
    expect(faces[0], `${name} opens nothing`).not.toHaveClass('chat-tile-open')
    expect(faces[0], `${name} opens nothing`).not.toHaveAttribute('role')
    expect(faces[0], `${name} opens nothing`).not.toHaveAttribute('tabindex')
    expect(tileElement.querySelector('[role="button"]'), `${name} is no door`).toBeNull()

    fireEvent.click(tileElement)
    fireEvent.keyDown(tileElement, { key: 'Enter' })
  }
  expect(inspections).not.toHaveBeenCalled()
})

/**
 * The sheet the tiles are painted with (R-WP-32). jsdom lays out nothing and resolves no custom property,
 * so a row and a hover are read where they are written — same rule as `chat.page.test.tsx`.
 */
const SHEETS = import.meta.glob('../../src/*.css', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>

function styles(): string {
  return SHEETS['../../src/styles.css']
}

/** The declarations a sheet gives one selector, up to a nested rule — a selector it does not name fails. */
function declarations(selector: string): string {
  const source = styles()
  const at = source.indexOf(`${selector} {`)
  expect(at, `${selector} is styled at all`).toBeGreaterThanOrEqual(0)
  const body = source.slice(at + selector.length + 2)
  const end = body.search(/[{}]/)
  return body.slice(0, end === -1 ? body.length : end)
}

/** Whether a sheet names a rule for this selector at all — the question behind "that look is absent". */
function declares(selector: string): boolean {
  return styles().includes(`${selector} {`)
}
