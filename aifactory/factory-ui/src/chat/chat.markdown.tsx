import MarkdownRender, { setCustomComponents } from 'markstream-react'
import { ShikiHighlighter } from 'react-shiki/web'

/**
 * The code block of an answer (R-WP-28): react-shiki, not the renderer's own highlighter, so the block
 * follows the theme layer of the app instead of a second colour scheme. Both themes travel in the markup
 * as `--shiki-dark`, and `styles.css` decides which of them shows — the same switch the tokens of
 * `index.css` use, so a code block never disagrees with the rest of the screen.
 *
 * An unknown language is plain text: react-shiki falls back on its own, so a fence for a language the
 * model invented highlights as nothing rather than breaking the answer around it.
 */
function ChatCodeBlock({ node }: { node: { language?: string; code?: string } }) {
  return (
    <div className="chat-code" data-testid="chat-code">
      <ShikiHighlighter
        language={node.language as never}
        theme={{ light: 'github-light', dark: 'github-dark' }}
        engine="javascript"
        addDefaultStyles={false}
        showLanguage={false}
      >
        {node.code ?? ''}
      </ShikiHighlighter>
    </div>
  )
}

/** The scope of our overrides: registered under this id, read back by the renderer that carries it. */
const SCOPE = 'aifactory-chat'

setCustomComponents(SCOPE, { code_block: ChatCodeBlock })

interface Props {
  /** The Markdown as it stands — during a turn this is incomplete on purpose. */
  text: string
  /** False while the answer is still arriving: the renderer then closes what the stream left open. */
  final?: boolean
}

/**
 * An answer as Markdown (R-WP-28), readable while it streams: an open `**`, a half list item or an open
 * fence is finished by the renderer and never shown as broken markup. Animations are off — a chat line
 * that fades in on every token would read as flicker, and the app's own look is the one that has to hold.
 */
export function ChatMarkdown({ text, final = true }: Props) {
  return (
    <div className="chat-markdown" data-testid="chat-markdown">
      <MarkdownRender
        content={text}
        final={final}
        fade={false}
        customId={SCOPE}
        smoothStreaming={false}
        batchRendering={false}
        deferNodesUntilVisible={false}
        viewportPriority={false}
      />
    </div>
  )
}
