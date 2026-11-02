import { content } from '../shared/content'
import { MAX_TOOL_LINES } from './chat.live'

interface Props {
  /** The tool lines of one run, in the order they arrived. */
  lines: string[]
}

/**
 * The tool activity of one place in the conversation (R-WP-28): consecutive lines share one box, in the
 * grey italic of `chat.html`, and the box keeps the last {@link MAX_TOOL_LINES} of them — what a turn
 * reports while it works is a trace beside the answer, not a list of its own.
 */
export function ChatTools({ lines }: Props) {
  const shown = lines.length > MAX_TOOL_LINES ? lines.slice(-MAX_TOOL_LINES) : lines
  return (
    <li className="chat-line tool" data-testid="chat-tool-line">
      <span className="chat-tools-label">{content.chat.toolActivity}</span>
      {shown.map((line, index) => (
        <span className="chat-tool-line" key={`tool-${index}`}>
          {line}
        </span>
      ))}
    </li>
  )
}
