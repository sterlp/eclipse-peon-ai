import { useState } from 'react'

/**
 * The one overlay a chat view may put on the right edge: the read-only look into an agent's own conversation
 * (UC-WP-49). The edge carries exactly one overlay (R-WP-33, UC-WP-60 G3), so the file area of the address
 * and this look are the one or the other — the drawer steps back over its address, the look leaves without
 * a trace of its own and nothing of it returns when the drawer closes.
 *
 * `filesOpen` is the frame's reading of the address: this hook keeps no "open" of the file area, it only
 * obeys it. `giveUpEdge` is the frame's way back, asked for when a tile's door is clicked.
 *
 * The `edgeAsked` flag is the measured part, and the reason this is a module of its own rather than three
 * lines in the page: React applies the state of the view that asks <em>before</em> the new address reaches
 * it, so the pass carrying a freshly asked name still reads the edge as taken. Stating the ask together
 * with the name is what keeps the rule below from releasing what the reader has just asked for. Adjusting
 * during the render is what holds the rule without an effect (those may not write state here, AGENTS-DEV)
 * and without ever painting both overlays in one frame.
 */
export function useEdgeInsight(filesOpen: boolean, giveUpEdge: () => void): EdgeInsight {
  const [insight, setInsight] = useState<{ name: string; edgeAsked: boolean } | null>(null)

  if (insight !== null) {
    if (filesOpen) {
      // the address took the edge for the file area: a look that was standing there is released, and one
      // that was asked for in the same click waits for the address it is on its way to
      if (!insight.edgeAsked) {
        setInsight(null)
      }
    } else if (insight.edgeAsked) {
      // the edge arrived: the look is the edge's now, and the ask is spent — the next address that takes
      // the edge releases it like any other look
      setInsight({ name: insight.name, edgeAsked: false })
    }
  }

  /**
   * A tile's door into that agent's own conversation (UC-WP-49): the ask carries the edge as the click found
   * it, and the frame is asked to hand the edge over — the one moment a chat touches the address (R-WP-38).
   */
  function openInsight(name: string): void {
    setInsight({ name, edgeAsked: filesOpen })
    giveUpEdge()
  }

  /** The X of the look: the reader ends it, and no second control does (UC-WP-49). */
  function closeInsight(): void {
    setInsight(null)
  }

  // a look never stands on an edge the address holds, not even in the pass before that address arrives
  return { shown: filesOpen || insight === null ? null : insight.name, openInsight, closeInsight }
}

export interface EdgeInsight {
  /** The agent whose look stands on the right edge — `null` while the address holds that edge. */
  shown: string | null
  /** A tile's door: take the edge for this agent's look, asking the frame for it if it is taken. */
  openInsight(name: string): void
  /** The reader's own way out of the look. */
  closeInsight(): void
}
