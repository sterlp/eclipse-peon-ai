import { Timeline, type TimelineItemProps } from 'antd'

import { content } from '../shared/content'
import {
  arrivalTime,
  MAX_TOOL_LINES,
  type DelegationItem,
  type ToolItem,
  type TraceItem,
} from './chat.live'

interface Props {
  /** The entries of one place of the trace, in the order they arrived. */
  items: TraceItem[]
  /** The one entry the open turn works on, or none (R-WP-40). Identity and not index: the cap may have
   *  trimmed this run, and the entry that works belongs to ONE turn — not to whichever turn works. */
  open: TraceItem | null
}

/**
 * The trace of ONE place in the conversation as one timeline (R-WP-40): the steps and the delegations of a
 * run are links of the same account, so they share the widget the wire's order drew instead of each kind
 * drawing a box of its own.
 *
 * The widget is the design basis' own; its dots and its gaps are tokens of `shared/theme.ts` and are never
 * painted over from a sheet. What the factory owns is the entry of every link and every handle standing on
 * it — the basis' classes are never a proof (UC-WP-72).
 *
 * Nothing here is invented (UC-WP-72 GIVEN 1 and 4): a name stands only where the wire stamped one, a clock
 * only where this browser received a moment, and the clock is an HTML `title` of our own entry — the basis'
 * `title` would add a second column and say more than the wire does.
 */
export function ChatTraceTimeline({ items, open }: Props) {
  return <Timeline items={timelineItems(items, open)} />
}

/**
 * The entries in arrival order. A run of steps keeps its tail (`MAX_TOOL_LINES`, R-WP-28) and a delegation
 * never joins that cut — there are few of them and each names a member, so dropping one would silently
 * drop a fact (UC-WP-72 GIVEN 5). Keyed by the place in the run, so the cut shifts no entry's identity.
 */
function timelineItems(items: TraceItem[], open: TraceItem | null): TimelineItemProps[] {
  const steps = items.filter((item): item is ToolItem => item.kind === 'tool')
  const hidden: Set<TraceItem> = new Set(steps.slice(0, Math.max(0, steps.length - MAX_TOOL_LINES)))
  const shown: TimelineItemProps[] = []
  items.forEach((item, index) => {
    if (hidden.has(item)) {
      return
    }
    const working = item === open
    shown.push({
      key: index,
      loading: working,
      content:
        item.kind === 'delegation' ? (
          <DelegationEntry item={item} working={working} />
        ) : (
          <StepEntry item={item} working={working} />
        ),
    })
  })
  return shown
}

/** One step of the run: the agent the core stamped above his report, and nothing the wire left unsaid. */
function StepEntry({ item, working }: { item: ToolItem; working: boolean }) {
  return (
    <span
      className="chat-trace-item"
      data-testid="chat-trace"
      data-trace="tool"
      data-working={working ? 'true' : undefined}
      title={working || item.at === null ? undefined : arrivalTime(item.at)}
    >
      {item.origin !== null && <span className="chat-step-name">{item.origin}</span>}
      {item.text}
    </span>
  )
}

/**
 * One delegation: the member the order went to and, while his turn is the open one, the word that he works.
 * The order itself stays the wording of the step above — the wire carries no task text, so this entry parses
 * nothing out of anybody (UC-WP-72 GIVEN 3). The basis' loading dot is the indicator; the word needs none.
 */
function DelegationEntry({ item, working }: { item: DelegationItem; working: boolean }) {
  return (
    <span
      className="chat-trace-item"
      data-testid="chat-delegation"
      data-trace="delegation"
      data-working={working ? 'true' : undefined}
      title={working || item.at === null ? undefined : arrivalTime(item.at)}
    >
      <span className="chat-delegation-name">{item.member}</span>
      {working && <span className="chat-delegation-working">{content.chat.working}</span>}
    </span>
  )
}
