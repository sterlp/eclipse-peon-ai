import type { ReactNode } from 'react'

import { Timeline, type TimelineItemProps } from 'antd'

import { content } from '../shared/content'
import { WorkingIndicator } from './chat.status'
import {
  arrivalTime,
  MAX_TOOL_LINES,
  type DelegationItem,
  type ThinkItem,
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
 * The trace of ONE place in the conversation as one timeline (R-WP-40): the steps, the Think sections and
 * the delegations of a run are links of the same account, so they share the widget the wire's order drew
 * instead of each kind drawing a box of its own.
 *
 * The widget is the design basis' own; its dots and its gaps are tokens of `shared/theme.ts` and are never
 * painted over from a sheet. What the factory owns is the entry of every link and every handle standing on
 * it — the basis' classes are never a proof (UC-WP-72).
 *
 * Nothing here is invented (UC-WP-72 GIVEN 1 and 4): a name stands only where the wire stamped one, a clock
 * only where this browser received a moment, and the clock is an HTML `title` of our own entry — the basis'
 * `title` would add a second column and say more than the wire does. The one word a Think entry names itself
 * with is ours and says what kind of entry it is, never who thought it: the wire stamps no author on one.
 */
export function ChatTraceTimeline({ items, open }: Props) {
  return <Timeline items={timelineItems(items, open)} />
}

/**
 * The entries in arrival order. A run of steps keeps its tail (`MAX_TOOL_LINES`, R-WP-28) and no other kind
 * joins that cut — a Think section and a delegation each say something no step says (a thought, a member),
 * so dropping one would silently drop a fact (UC-WP-72 GIVEN 5). Keyed by the place in the run, so the cut
 * shifts no entry's identity.
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
      // both and not one: without `loading` the basis answers `status: finish` and without `icon` its own
      // loading glyph, which carries no handle of ours — the working dot is our widget (R-WP-40, UC-WP-72).
      // `icon` and not the deprecated `dot` (`useItems.js`: `mergedIcon = icon ?? dot`)
      loading: working,
      icon: working ? <WorkingIndicator size="small" /> : undefined,
      content: entryOf(item, working),
    })
  })
  return shown
}

/** The entry of one link, by its kind — a new kind of the trace is a branch here and no edit of the others. */
function entryOf(item: TraceItem, working: boolean) {
  if (item.kind === 'delegation') {
    return <DelegationEntry item={item} working={working} />
  }
  if (item.kind === 'think') {
    return <ThinkEntry item={item} working={working} />
  }
  return <StepEntry item={item} working={working} />
}

/** One step of the run: the agent the core stamped above his report, and nothing the wire left unsaid. */
function StepEntry({ item, working }: { item: ToolItem; working: boolean }) {
  return (
    <TraceEntry kind="tool" working={working} at={item.at}>
      {item.origin !== null && <span className="chat-step-name">{item.origin}</span>}
      {item.text}
    </TraceEntry>
  )
}

/**
 * One Think section, an entry like a step (R-WP-40): the word that says what kind of entry this is, and the
 * wire's wording under it. The word is never an agent — the wire stamps no author on a thought, so lending
 * one would be the invention UC-WP-72 GIVEN 1 forbids. The wording shows as typed and not as Markdown: an
 * answer is speech, a thought is a link of the account (R-WP-28).
 */
function ThinkEntry({ item, working }: { item: ThinkItem; working: boolean }) {
  return (
    <TraceEntry kind="think" working={working} at={item.at}>
      <span className="chat-step-name">{content.chat.thinking}</span>
      {item.text}
    </TraceEntry>
  )
}

/**
 * One delegation: the member the order went to and, while his turn is the open one, the word that he works.
 * The order itself stays the wording of the step above — the wire carries no task text, so this entry parses
 * nothing out of anybody (UC-WP-72 GIVEN 3). The basis' loading dot is the indicator; the word needs none.
 */
function DelegationEntry({ item, working }: { item: DelegationItem; working: boolean }) {
  return (
    <TraceEntry kind="delegation" working={working} at={item.at}>
      <span className="chat-delegation-name">{item.member}</span>
      {working && <span className="chat-delegation-working">{content.chat.working}</span>}
    </TraceEntry>
  )
}

/**
 * The shell every link of the trace stands in, and the one place its handles are decided (UC-WP-72): our own
 * `data-*` and never a class of the basis; the working flag on the one entry the open turn works on; and the
 * clock of arrival only once the entry is finished **and** the wire really brought a moment — a working entry
 * has no clock yet, an untimestamped one never gets one. The delegation answers to `chat-delegation` and the
 * two other kinds to `chat-trace`, because one element carries one handle; `data-trace` tells the kinds
 * apart wherever a test needs to.
 */
function TraceEntry({
  kind,
  working,
  at,
  children,
}: {
  kind: TraceItem['kind']
  working: boolean
  at: number | null
  children: ReactNode
}) {
  return (
    <span
      className="chat-trace-item"
      data-testid={kind === 'delegation' ? 'chat-delegation' : 'chat-trace'}
      data-trace={kind}
      data-working={working ? 'true' : undefined}
      title={working || at === null ? undefined : arrivalTime(at)}
    >
      {children}
    </span>
  )
}
