import type { ReactNode } from 'react'

import { Timeline, type TimelineItemProps } from 'antd'
import { CloseCircleFilled } from '@ant-design/icons'

import { content } from '../shared/content'
import { WorkingIndicator } from './chat.status'
import { arrivalTime, MAX_TOOL_LINES, type TimelineEntry } from './chat.live'

/** One entry of one kind — what `entryOf` narrows a `TimelineEntry` to, anchor-free like all of them. */
type EntryOf<K extends TimelineEntry['kind']> = Extract<TimelineEntry, { kind: K }>

interface Props {
  /** The entries of one place of the trace, in the order they arrived. */
  items: TimelineEntry[]
  /** The one entry the open turn works on, or none (R-WP-40). Identity and not index: the cap may have
   *  trimmed this run, and the entry that works belongs to ONE turn — not to whichever turn works. */
  open: TimelineEntry | null
}

/**
 * The trace of ONE place in the conversation as one timeline (R-WP-40): the steps, the refusals of a tool,
 * the Think sections and the delegations of a run are links of the same account, so they share the widget the
 * wire's order drew instead of each kind drawing a box of its own. A refusal is a link like any other and
 * painted as what it is — red text, the word `Problem`, the basis' error icon on its head (R-WP-61, R-WP-46).
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
 * joins that cut — a Think section, a refusal and a delegation each say something no step says (a thought, a
 * refusal, a member), so dropping one would silently drop a fact (UC-WP-72 GIVEN 5). Keyed by the place in
 * the run, so the cut shifts no entry's identity.
 */
function timelineItems(items: TimelineEntry[], open: TimelineEntry | null): TimelineItemProps[] {
  const steps = items.filter((item): item is EntryOf<'tool'> => item.kind === 'tool')
  const hidden: Set<TimelineEntry> = new Set(steps.slice(0, Math.max(0, steps.length - MAX_TOOL_LINES)))
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
      // an entry that does not work carries no mark at all — a refusal excepted: its red icon belongs on the
      // head of the link, where the basis paints a mark, and not in the content (R-WP-46, R-WP-61)
      icon: working ? <WorkingIndicator size="small" /> : refusalIconOf(item),
      content: entryOf(item, working),
    })
  })
  return shown
}

/** The mark of an entry that does not work: none — a refusal excepted, whose red glyph is the basis' own. */
function refusalIconOf(item: TimelineEntry) {
  return item.kind === 'problem' ? <CloseCircleFilled className="chat-problem-icon" /> : undefined
}

/** The entry of one link, by its kind — a new kind of the trace is a branch here and no edit of the others. */
function entryOf(item: TimelineEntry, working: boolean) {
  if (item.kind === 'delegation') {
    return <DelegationEntry item={item} working={working} />
  }
  if (item.kind === 'think') {
    return <ThinkEntry item={item} working={working} />
  }
  if (item.kind === 'problem') {
    return <ProblemEntry item={item} working={working} />
  }
  return <StepEntry item={item} working={working} />
}

/** One step of the run: the agent the core stamped above his report, and nothing the wire left unsaid. */
function StepEntry({ item, working }: { item: EntryOf<'tool'>; working: boolean }) {
  return (
    <TraceEntry kind="tool" working={working} at={item.at}>
      {item.origin !== null && <span className="chat-step-name">{item.origin}</span>}
      {item.text}
    </TraceEntry>
  )
}

/**
 * The core's report that a tool refused (R-WP-61, UC-WP-98): the word that says what kind of entry this is,
 * the agent the wire stamped under it where the wire has one (the step's own idiom, so a refusal names its
 * agent exactly as a step does), and the report under both. Red is the sheet's, on our handles
 * `.chat-problem`, `.chat-problem-name` and `.chat-problem-icon` — and it is never inherited: a step's stamp
 * paints itself grey by design, so an entry that only leaned on inheritance would read grey. The icon does not
 * stand here: `refusalIconOf` hands it to the widget, because that is where the basis paints a mark. A refusal
 * never works, whatever the open turn does — the tool that refused is finished.
 */
function ProblemEntry({ item, working }: { item: EntryOf<'problem'>; working: boolean }) {
  return (
    <TraceEntry kind="problem" working={working} at={item.at}>
      <span className="chat-problem-name">{content.chat.problem}</span>
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
function ThinkEntry({ item, working }: { item: EntryOf<'think'>; working: boolean }) {
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
 * nothing out of anybody (UC-WP-72 GIVEN 3). What says <em>he works</em> is the point of the open entry, and
 * that point is our own `WorkingIndicator` — `timelineItems` hands it as `icon`, because the basis' loading
 * glyph would paint a second mark of the same answer (R-WP-46); the word beside the name carries no mark.
 */
function DelegationEntry({ item, working }: { item: EntryOf<'delegation'>; working: boolean }) {
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
 * other kinds to `chat-trace`, because one element carries one handle; `data-trace` tells the kinds apart
 * wherever a test needs to. A refusal stands in the trace's class **and** in `chat-problem`: the red of a
 * refusal is read off that handle and off the icon's own, never off an inheritance (R-WP-61).
 */
function TraceEntry({
  kind,
  working,
  at,
  children,
}: {
  kind: TimelineEntry['kind']
  working: boolean
  at: number | null
  children: ReactNode
}) {
  return (
    <span
      className={kind === 'problem' ? 'chat-trace-item chat-problem' : 'chat-trace-item'}
      data-testid={kind === 'delegation' ? 'chat-delegation' : 'chat-trace'}
      data-trace={kind}
      data-working={working ? 'true' : undefined}
      title={working || at === null ? undefined : arrivalTime(at)}
    >
      {children}
    </span>
  )
}
