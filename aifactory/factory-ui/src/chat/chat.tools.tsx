import { content } from '../shared/content'
import { arrivalTime, MAX_TOOL_LINES, type ToolItem } from './chat.live'

interface Props {
  /** The steps of one tool run, in the order they arrived. */
  items: ToolItem[]
  /** The step the open turn reported last, or none (R-WP-40). Identity and not index: the cap may have
   *  trimmed this run, and the step that works belongs to one turn — not to whichever turn works. */
  open: ToolItem | null
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
