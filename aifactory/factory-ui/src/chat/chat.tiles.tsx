import type { KeyboardEvent } from 'react'
import { Button } from 'antd'

import type { AgentAction, AgentTile } from './chat.agents.client'
import { AgentStatus } from './chat.status'
import { content } from '../shared/content'

interface Props {
  /** The current roster snapshot — from the read on mount or from an `agents` event, never merged. */
  tiles: AgentTile[]
  /** Asks for an action on one tile; the page owns the call and what a refusal means. */
  onAction: (name: string, action: AgentAction) => void
  /** Asks for the read-only insight into one tile's own conversation (UC-WP-49) — the tile's own click. */
  onInspect: (name: string) => void
}

/**
 * The head of the chat view (R-WP-7, R-WP-32): one tile per agent of this project's team, in a row — the
 * name as title, the context number the core reports and the agent's own state beneath it, Clear and
 * Compact beneath that where the tile offers them.
 *
 * The tile's capability set is the only rule this component follows — there is no agent name in here, so
 * a Search sub-loop without affordances and a member with an insight need no branch (D-14). A number the
 * server does not send is shown as a dash and never as a computed guess.
 *
 * A tile offering TRANSCRIPT is itself the door to the read-only Blick (R-WP-32): the face carries
 * `role="button"`, Enter and Space, and the hover that promises something — a second control for the same
 * thing would be the separate link the rule rules out. The actions inside stop their own event, so a click
 * or a key held on Clear is Clear and never the door (UC-WP-59). A tile without TRANSCRIPT gets neither the
 * role nor the hover of one: a look that promises nothing would be a lie (R-WP-26 — Jon's conversation is
 * the chat, a sub-loop owns none).
 */
export function ChatTiles({ tiles, onAction, onInspect }: Props) {
  if (tiles.length === 0) {
    return null
  }
  return (
    <ul className="chat-tiles" data-testid="chat-tiles" aria-label={content.tiles.team}>
      {tiles.map(tile => {
        // only a state that holds the agent blocks an action — a failure is over, and the server takes a
        // Clear on an idle agent at once, so disabling on ERROR would strand the tile until the next turn
        const busy = tile.state === 'WORKING' || tile.state === 'COMPACTING'
        const opens = tile.capabilities.includes('TRANSCRIPT')
        return (
          <li key={tile.name} className="chat-tile" data-testid={`tile-${tile.name}`}>
            <div
              className={`chat-tile-face${opens ? ' chat-tile-open' : ''}`}
              data-testid={opens ? `tile-open-${tile.name}` : undefined}
              role={opens ? 'button' : undefined}
              tabIndex={opens ? 0 : undefined}
              aria-label={opens ? content.tiles.open(tile.name) : undefined}
              onClick={opens ? () => onInspect(tile.name) : undefined}
              onKeyDown={opens ? event => openFromKeyboard(event, tile.name, onInspect) : undefined}
            >
              <span className="chat-tile-name">{tile.name}</span>
              <span className="chat-tile-meta">
                <span
                  className="chat-tile-context"
                  data-testid={`tile-context-${tile.name}`}
                  title={
                    tile.context === null
                      ? content.tiles.noContext
                      : content.tiles.budget(tile.contextPercent, tile.budget)
                  }
                >
                  {tile.context ?? '—'}
                </span>
                <span
                  className={`chat-tile-state ${tile.state.toLowerCase()}`}
                  data-state={tile.state}
                  data-testid={`tile-state-${tile.name}`}
                >
                  <AgentStatus state={tile.state} />
                  {content.tiles.state[tile.state]}
                </span>
              </span>
              <span className="chat-tile-actions">
                {tile.capabilities.includes('CLEAR') && (
                  <Button
                    size="small"
                    data-testid={`tile-clear-${tile.name}`}
                    disabled={busy}
                    onClick={event => {
                      event.stopPropagation()
                      onAction(tile.name, 'clear')
                    }}
                  >
                    {content.tiles.clear}
                  </Button>
                )}
                {tile.capabilities.includes('COMPACT') && (
                  <Button
                    size="small"
                    data-testid={`tile-compact-${tile.name}`}
                    disabled={busy}
                    onClick={event => {
                      event.stopPropagation()
                      onAction(tile.name, 'compact')
                    }}
                  >
                    {content.tiles.compact}
                  </Button>
                )}
              </span>
            </div>
          </li>
        )
      })}
    </ul>
  )
}

/**
 * Enter and Space activate a button; the face answers for itself only, so a key held while an action has
 * the focus stays that action, and Space does not scroll the chat out from under the tile.
 */
function openFromKeyboard(
  event: KeyboardEvent<HTMLElement>,
  name: string,
  onInspect: (name: string) => void,
): void {
  if (event.target !== event.currentTarget || (event.key !== 'Enter' && event.key !== ' ')) {
    return
  }
  event.preventDefault()
  onInspect(name)
}
