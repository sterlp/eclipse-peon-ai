import { ShikiHighlighter } from 'react-shiki/web'

interface Props {
  code: string
  /** A Shiki language id — one the bundle does not carry highlights as nothing, on purpose. */
  language: string
  /** The caller's own box: `chat-code` for a fenced answer, `files-viewer-code` for a file. */
  className: string
  testId: string
}

/**
 * Highlighted code, one component for the two places that show it (R-WP-28, R-WP-33). react-shiki over
 * `/web` keeps the main chunk small and the languages lazy, and `engine="javascript"` means no WASM — the
 * same call answers in the browser and in a jsdom test.
 *
 * Both themes travel in the markup as `--shiki-dark` and `styles.css` picks one on the switch the colour
 * tokens of `index.css` read, so a block never disagrees with the screen around it.
 */
export function CodeView({ code, language, className, testId }: Props) {
  return (
    <div className={`code-view ${className}`} data-testid={testId}>
      <ShikiHighlighter
        language={language as never}
        theme={{ light: 'github-light', dark: 'github-dark' }}
        engine="javascript"
        addDefaultStyles={false}
        showLanguage={false}
      >
        {code}
      </ShikiHighlighter>
    </div>
  )
}
