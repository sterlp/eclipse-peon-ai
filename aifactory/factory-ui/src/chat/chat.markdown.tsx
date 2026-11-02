import MarkdownRender, { setCustomComponents } from 'markstream-react'

import { CodeView } from '../shared/code'

/**
 * The code block of an answer (R-WP-28): the app's one highlighted view, so a fence in a reply and a
 * file in the viewer are the same colours and the same theme switch — one component, one place to keep
 * (see `shared/code.tsx`).
 *
 * An unknown language is plain text: Shiki falls back on its own, so a fence for a language the model
 * invented highlights as nothing rather than breaking the answer around it.
 */
function ChatCodeBlock({ node }: { node: { language?: string; code?: string } }) {
  return (
    <CodeView className="chat-code" testId="chat-code" language={node.language ?? 'text'} code={node.code ?? ''} />
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
