interface Props {
  /** The project-relative path the core named for this change. */
  file: string
  /** The unified diff of that file, as the core computed it — headers and hunk marks included. */
  diff: string
}

/**
 * One file the turn changed, as a block of the chat (R-WP-36, UC-WP-68): the path above the diff, the
 * changed lines coloured, everything else left as the diff wrote it — the headers included, so what the
 * screen shows is a diff the user could hand to `git apply`.
 *
 * No highlighter here on purpose: the core's diff is already the answer to "what changed", and a grammar
 * per file type would be a second, optional truth about the same lines (unlike a fenced answer or the
 * file viewer, this block therefore takes no part in the `.code-view` contract).
 *
 * One `<span>` per line, and no `\n` inside it — the sheet makes each span a block. Writing the newline
 * as well is what doubled every line box once before (shiki separates its lines the same way), so the
 * break belongs to the box model here and not to the text.
 */
export function ChatDiff({ file, diff }: Props) {
  const lines = diff.split('\n')
  // a diff ends with a line break, which `split` turns into a trailing empty entry: an empty line box
  // would claim space the change did not take
  if (lines.length > 0 && lines[lines.length - 1] === '') {
    lines.pop()
  }
  return (
    // `chat-diff` is the block itself, so one ancestor names every rule of its typography; the surface
    // inside is the shared `.chat-code` of a fenced answer — same edge, same radius, no second one (R-WP-33)
    <li className="chat-line diff chat-diff" data-testid="chat-diff">
      <span className="chat-diff-file" data-testid="chat-diff-file">
        {file}
      </span>
      <div className="chat-code">
        <pre className="chat-diff-body">
          {lines.map((line, index) => (
            <span className={`chat-diff-line ${lineClassOf(line, index)}`} data-testid="chat-diff-line" key={`diff-${index}`}>
              {line}
            </span>
          ))}
        </pre>
      </div>
    </li>
  )
}

/**
 * Which kind of diff line this is — decided on the text alone, because the unified format states it there.
 * Only the first two lines can be the file headers: a content line of a real file may well begin with
 * `--- ` and would otherwise be dimmed away.
 */
function lineClassOf(line: string, index: number): string {
  if (index <= 1 && (line.startsWith('--- ') || line.startsWith('+++ '))) {
    return 'diff-meta'
  }
  if (line.startsWith('@@')) {
    return 'diff-meta'
  }
  if (line.startsWith('+')) {
    return 'diff-added'
  }
  if (line.startsWith('-')) {
    return 'diff-removed'
  }
  return 'diff-context'
}
