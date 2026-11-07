import { isValidElement } from 'react'
import type { ReactNode } from 'react'
import ReactMarkdown from 'react-markdown'
import type { Components } from 'react-markdown'
import remarkBreaks from 'remark-breaks'
import remarkGfm from 'remark-gfm'

import { CodeView } from '../shared/code'

/**
 * A fence of an answer (R-WP-28): `pre` hands its single `code` child to the app's one highlighted view,
 * so a fence in a reply and a file in the viewer are the same colours and the same theme switch — one
 * component, one place to keep (see `shared/code.tsx`).
 *
 * A fence without a language, or one the model invented, is plain text: Shiki falls back on its own, so
 * the block highlights as nothing rather than breaking the answer around it.
 */
function CodeFence({ children }: { children?: ReactNode }) {
  const [child] = Array.isArray(children) ? children : [children]
  if (!isValidElement<{ className?: string; children?: ReactNode }>(child)) {
    return <pre>{children}</pre>
  }
  const language = /language-([\w-]+)/.exec(child.props.className ?? '')?.[1] ?? 'text'
  // a fence carries its code as one string, with the newline the closing fence left behind on it
  const code = typeof child.props.children === 'string' ? child.props.children.replace(/\n$/, '') : ''

  return <CodeView className="chat-code" testId="chat-code" language={language} code={code} />
}

/**
 * A wide table of an answer is read inside its own scroll box (R-WP-29): the wrapper is the part that
 * scrolls horizontally, so the log keeps its vertical scroll and the page its fixed width. The table keeps
 * its own layout — widening it with `display: block` would redraw every answer's table to keep it from
 * shrinking instead of scrolling. `ANTD-A6` in the deviation register: this is an extra level of the
 * answer's DOM that a test uses as its seam, and it comes from us, not from the design basis.
 */
function TableBlock({ children }: { children?: ReactNode }) {
  return (
    <div className="chat-markdown-table">
      <table>{children}</table>
    </div>
  )
}

/**
 * Module scope because those props then never change: react-markdown builds its processor on every
 * render (`createProcessor` in `react-markdown/lib/index.js`) — the identity-based memoisation sits in
 * `MarkdownHooks`, which this app does not use. A fresh array per render would cost nothing extra; the
 * constants stay because they are the same value on every render.
 */
const REMARK_PLUGINS = [remarkGfm, remarkBreaks]

const COMPONENTS: Components = { pre: CodeFence, table: TableBlock }

interface Props {
  /** The Markdown as it stands — during a turn this is incomplete on purpose. */
  text: string
}

/**
 * An answer as Markdown (R-WP-28): GFM, and **one line break in the text stays a line on the screen**
 * (`remark-breaks` — soft breaks that join into one paragraph are the measured defect of the old renderer,
 * and react-markdown v10 has no `breaks` option of its own).
 *
 * Readable while it streams: an open `**` shows as what was typed until its token closes it, an open fence
 * already reads as code, and nothing ends as raw HTML — the renderer never turns model text into markup,
 * so a half answer is neither broken nor a hole in the page. No `final` flag: the whole text is parsed
 * again per token, which is exactly what makes the last token render the finished answer.
 */
export function ChatMarkdown({ text }: Props) {
  return (
    <div className="chat-markdown" data-testid="chat-markdown">
      <ReactMarkdown remarkPlugins={REMARK_PLUGINS} components={COMPONENTS}>
        {text}
      </ReactMarkdown>
    </div>
  )
}
