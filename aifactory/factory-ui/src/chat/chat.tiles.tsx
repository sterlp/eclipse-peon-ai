import type { AgentAction, AgentTile } from './chat.agents.client'
import { content } from '../shared/content'

interface Props {
  /** The current roster snapshot — from the read on mount or from an `agents` event, never merged. */
  tiles: AgentTile[]
  /** Asks for an action on one tile; the page owns the call and what a refusal means. */
  onAction: (name: string, action: AgentAction) => void
  /** Asks for the read-only insight into one tile's own conversation (UC-WP-49) — offered per tile. */
  onInspect: (name: string) => void
}

/**
 * The head of the chat view (R-WP-7, UC-WP-11): one tile per agent of this project's team, with the
 * context number the core reports, the agent's own state, and Clear and Compact where the tile offers them.
 *
 * The tile's capability set is the only rule this component follows — there is no agent name in here, so
 * a Search sub-loop without affordances and a member with an insight need no branch (D-14). A number the
 * server does not send is shown as a dash and never as a computed guess.
 *
 * A tile offering TRANSCRIPT also offers <em>Insight</em>: the read-only look into that agent's own
 * conversation (UC-WP-49). It is a control beside the others rather than a click on the whole tile,
 * because the tile already carries buttons and a button may not hold a button.
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
        return (
          <li key={tile.name} className="chat-tile" data-testid={`tile-${tile.name}`}>
            <span className="chat-tile-name">{tile.name}</span>
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
              {content.tiles.state[tile.state]}
            </span>
            {tile.capabilities.includes('TRANSCRIPT') && (
              <button
                type="button"
                className="chat-tile-open"
                data-testid={`tile-open-${tile.name}`}
                onClick={() => onInspect(tile.name)}
              >
                {content.tiles.insight}
              </button>
            )}
            {tile.capabilities.includes('CLEAR') && (
              <button
                type="button"
                data-testid={`tile-clear-${tile.name}`}
                disabled={busy}
                onClick={() => onAction(tile.name, 'clear')}
              >
                {content.tiles.clear}
              </button>
            )}
            {tile.capabilities.includes('COMPACT') && (
              <button
                type="button"
                data-testid={`tile-compact-${tile.name}`}
                disabled={busy}
                onClick={() => onAction(tile.name, 'compact')}
              >
                {content.tiles.compact}
              </button>
            )}
          </li>
        )
      })}
    </ul>
  )
}
