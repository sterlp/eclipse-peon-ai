import { Spin } from 'antd'
import { CloseCircleFilled, LoadingOutlined } from '@ant-design/icons'

import type { AgentTileState } from './chat.agents.client'

/**
 * The one way this screen says <em>work is happening</em> (R-WP-45): the basis' own spin with its own
 * loading glyph, so the turning mark is the library's and the colour is ours. The tile of an agent and the
 * open step of the log both draw THIS and no other rendering of the same answer (R-WP-46).
 *
 * The measure is a component token and never a rule of the sheet: `Spin.dotSize` of `shared/theme.ts` answers
 * for `medium` (16 px), the size classes only swap the CSS variable the indicator's `font-size` reads
 * (`antd/es/spin/style/index.js`), and the basis clones our glyph under its own indicator class
 * (`antd/es/spin/Indicator/index.js`) — so a CSS rule here would fight a token and lose. The log's trace asks
 * for `small`, which stays the basis' smaller mark.
 *
 * `tone` is the only variant: work answers in the accent, a compact in the asking colour of R-WP-45 — one
 * spin, so one state never stands in two looks. The colour goes on the indicator and not on the spin's own
 * node, which the basis paints with `colorPrimary` (`spin/index.js`, `classNames.section`).
 */
export function WorkingIndicator({
  size = 'medium',
  tone = 'accent',
}: {
  size?: 'small' | 'medium' | 'large'
  tone?: 'accent' | 'question'
}) {
  return (
    <Spin
      size={size}
      indicator={<LoadingOutlined spin />}
      classNames={tone === 'question' ? { indicator: 'working-indicator-question' } : undefined}
      data-testid="working-indicator"
    />
  )
}

/**
 * The agent's own state as a mark on its tile (R-WP-45) — the word beside it names the state, this shows it.
 * The value arrives from the roster and is never derived here. `WORKING` hands the drawing to
 * {@link WorkingIndicator}, so the tile and the open step of the log share one display (R-WP-46); the tile
 * asks for no size, so its mark is the token's `medium` — big enough to be seen beside the word
 * (Paul 2026-11-08), while the idle circle and the error glyph hold the same 1rem (R-WP-45).
 *
 * No `data-state` of its own: the host's span already carries it (`chat.tiles.tsx`) — one answer, one
 * handle. The mark is decoration beside the word, so a screen reader reads the word and nothing twice.
 */
export function AgentStatus({ state }: { state: AgentTileState }) {
  return (
    <span className="agent-status" data-testid="agent-status" aria-hidden={true}>
      {state === 'WORKING' && <WorkingIndicator />}
      {state === 'COMPACTING' && <WorkingIndicator tone="question" />}
      {state === 'IDLE' && <span className="agent-status-idle" />}
      {state === 'ERROR' && <CloseCircleFilled className="agent-status-error" />}
    </span>
  )
}
