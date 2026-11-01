import type { AgentAction, AgentTile, AgentTileState } from './chat.agents.client'

interface Props {
  /** The current roster snapshot — from the read on mount or from an `agents` event, never merged. */
  tiles: AgentTile[]
  /** Asks for an action on one tile; the page owns the call and what a refusal means. */
  onAction: (name: string, action: AgentAction) => void
}

const STATE_LABEL: Record<AgentTileState, string> = {
  IDLE: 'bereit',
  WORKING: 'arbeitet',
  COMPACTING: 'kompaktiert',
  ERROR: 'Fehler',
}

/**
 * The head of the chat view (R-WP-7, UC-WP-11): one tile per agent of this project's team, with the
 * context number the core reports, the agent's own state, and Clear and Compact where the tile offers them.
 *
 * The tile's capability set is the only rule this component follows — there is no agent name in here, so
 * a Search sub-loop without affordances and a member with an insight need no branch (D-14). A number the
 * server does not send is shown as a dash and never as a computed guess.
 */
export function ChatTiles({ tiles, onAction }: Props) {
  if (tiles.length === 0) {
    return null
  }
  return (
    <ul className="chat-tiles" data-testid="chat-tiles" aria-label="Team">
      {tiles.map(tile => {
        const busy = tile.state !== 'IDLE'
        return (
          <li key={tile.name} className="chat-tile" data-testid={`tile-${tile.name}`}>
            <span className="chat-tile-name">{tile.name}</span>
            <span
              className="chat-tile-context"
              data-testid={`tile-context-${tile.name}`}
              title={
                tile.context === null
                  ? 'kein eigener Kontext'
                  : `${tile.contextPercent}% von Budget ${tile.budget}`
              }
            >
              {tile.context ?? '—'}
            </span>
            <span
              className={`chat-tile-state ${tile.state.toLowerCase()}`}
              data-state={tile.state}
              data-testid={`tile-state-${tile.name}`}
            >
              {STATE_LABEL[tile.state]}
            </span>
            {tile.capabilities.includes('CLEAR') && (
              <button
                type="button"
                data-testid={`tile-clear-${tile.name}`}
                disabled={busy}
                onClick={() => onAction(tile.name, 'clear')}
              >
                Clear
              </button>
            )}
            {tile.capabilities.includes('COMPACT') && (
              <button
                type="button"
                data-testid={`tile-compact-${tile.name}`}
                disabled={busy}
                onClick={() => onAction(tile.name, 'compact')}
              >
                Compact
              </button>
            )}
          </li>
        )
      })}
    </ul>
  )
}
