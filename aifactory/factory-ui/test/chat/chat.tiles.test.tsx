import { expect, test, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'

import { ChatTiles } from '../../src/chat/chat.tiles'
import type { AgentAction, AgentTile, AgentTileState } from '../../src/chat/chat.agents.client'

/**
 * The tile row as the screen shows it (R-WP-7). Drawn from a fixture roster, because what these tests
 * guard is the <em>reading</em> of the server's answer: the number is shown as the core formatted it, a
 * missing number becomes a dash, and the capability set alone decides which buttons exist — no agent name
 * is written anywhere in this file, and a rule that moved into the UI would show up here as a red test.
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
test('teamTilesShowContextAndStatus', () => {
  renderTeam()

  expect(screen.getAllByRole('listitem')).toHaveLength(TEAM.length)
  expect(screen.getByTestId('tile-Jon')).toHaveTextContent('Jon')
  expect(screen.getByTestId('tile-context-Jon')).toHaveTextContent('12k')
  expect(screen.getByTestId('tile-state-Jon')).toHaveTextContent('bereit')
  expect(screen.getByTestId('tile-state-Dev')).toHaveTextContent('arbeitet')
  expect(screen.getByTestId('tile-context-Plan')).toHaveTextContent('4k')
  // the dash is the server's `null`, never a computed 0 — a number nobody reported would be a lie
  expect(screen.getByTestId('tile-context-Review')).toHaveTextContent('—')
})

// UC-WP-11
test('searchTileShowsNoContextAndNoButtons', () => {
  renderTeam()

  const search = screen.getByTestId('tile-Search')
  expect(search).toHaveTextContent('Search')
  expect(search).toHaveTextContent('bereit')
  expect(screen.getByTestId('tile-context-Search')).toHaveTextContent('—')
  // a sub-loop owns no memory between calls: the row carries neither action (R-WP-7)
  expect(screen.queryByTestId('tile-clear-Search')).toBeNull()
  expect(screen.queryByTestId('tile-compact-Search')).toBeNull()
})

// UC-WP-13
test('compactShowsItsOwnState', () => {
  render(
    <ChatTiles
      tiles={[tile('Dev', 'COMPACTING', { capabilities: ['CONTEXT', 'CLEAR', 'COMPACT', 'TRANSCRIPT'] })]}
      onAction={vi.fn()}
      onInspect={vi.fn()}
    />,
  )

  expect(screen.getByTestId('tile-state-Dev')).toHaveTextContent('kompaktiert')
  // a tile only acts on an idle agent — a second action would write over the running one (UC-WP-12 guard)
  expect(screen.getByTestId('tile-clear-Dev')).toBeDisabled()
  expect(screen.getByTestId('tile-compact-Dev')).toBeDisabled()
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
