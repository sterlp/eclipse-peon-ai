import { content } from '../shared/content'
import { arrivalTime, MAX_TOOL_LINES, type DelegationItem, type ToolItem } from './chat.live'

interface Props {
  /** The steps of one tool run, in the order they arrived. */
  items: ToolItem[]
  /** The one entry the open turn works on, or none (R-WP-40): a step of this box, or a delegation drawn
   *  beside it — which is exactly how this box stops working. Identity and not index: the cap may have
   *  trimmed this run, and the step that works belongs to one turn — not to whichever turn works. */
  open: ToolItem | DelegationItem | null
}

/**
 * The tool activity of one place in the conversation (R-WP-28): consecutive steps share one box, in the
 * grey italic of `chat.html`, and the box keeps the last {@link MAX_TOOL_LINES} of them — what a turn
 * reports while it works is a trace beside the answer, not a list of its own.
 *
 * Each step is a timeline entry of its own (R-WP-40): the agent the core stamped on it above its report, and
 * the clock of its arrival as the title of a step that is not working. A name the wire does not carry is not
 * shown, and a step whose moment the browser never received carries no title — the factory invents no time
 * and parses none out of the text (UC-WP-72).
 */
export function ChatTools({ items, open }: Props) {
  const shown = items.length > MAX_TOOL_LINES ? items.slice(-MAX_TOOL_LINES) : items
  const first = items.length - shown.length
  return (
    <li className="chat-line tool" data-testid="chat-tool-line">
      <span className="chat-tools-label">{content.chat.toolActivity}</span>
      {shown.map((item, offset) => {
        const working = item === open
        return (
          <span
            className="chat-tool-line"
            key={`tool-${first + offset}`}
            title={working || item.at === null ? undefined : arrivalTime(item.at)}
          >
            {item.origin !== null && <span className="chat-step-name">{item.origin}</span>}
            {item.text}
            {working && <span className="chat-step-running" aria-hidden="true" />}
          </span>
        )
      })}
    </li>
  )
}

interface DelegationProps {
  /** The delegation as the wire reported it — member, arrival and what its own events said. */
  item: DelegationItem
  /** True only while THIS view holds the turn open that the entry belongs to: identity and the entry's own
   *  end decide it, never the place in the list (R-WP-40, UC-WP-72 GIVEN 3). */
  open: boolean
}

/**
 * A delegation of the turn as a link of the timeline of its own (R-WP-40): the member the order went to and,
 * while his turn is the open one, the word that he works. The order itself stays the wording of the tool
 * line above — the wire carries no task text, so this entry parses nothing out of anybody (UC-WP-72
 * GIVEN 3). Like a step it carries the clock of its arrival once it is over, and a duration stays the
 * statement of the turn alone.
 */
export function ChatDelegation({ item, open }: DelegationProps) {
  return (
    <li
      className="chat-line delegation"
      data-testid="chat-delegation"
      title={!open && item.at !== null ? arrivalTime(item.at) : undefined}
    >
      <span className="chat-delegation-name">{item.member}</span>
      {open && <span className="chat-delegation-working">{content.chat.working}</span>}
    </li>
  )
}
