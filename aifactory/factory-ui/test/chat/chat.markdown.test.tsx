import { beforeEach, expect, test } from 'vitest'
import { act, cleanup, render, waitFor } from '@testing-library/react'
import type { RenderResult } from '@testing-library/react'

import { ChatMarkdown } from '../../src/chat/chat.markdown'
import { declarations } from '../shared/sheets'

/**
 * One answer, one component: a streamed document is re-parsed in place, so the view that grows is the view
 * that ends on the screen — the assertions below name which of the two they belong to.
 */
async function grow(view: RenderResult, text: string): Promise<void> {
  await act(async () => {
    view.rerender(<ChatMarkdown text={text} />)
  })
}

function renderMarkdown(text: string): RenderResult {
  return render(<ChatMarkdown text={text} />)
}

beforeEach(() => {
  cleanup()
})

/**
 * The measured defect the sharpened rule closes (R-WP-28): two sentences the model wrote on two lines
 * must not run together into one paragraph. `remark-breaks` is what keeps them apart.
 */
// UC-WP-73
test('aSingleLineBreakStaysALineOnTheScreen', () => {
  const { container } = renderMarkdown('der Build läuft.\nich habe ihn zweimal gestartet')

  expect(container.querySelector('br'), 'the single break is a line break of its own').not.toBeNull()
  expect(container.textContent).toContain('der Build läuft.')
  expect(container.textContent).toContain('ich habe ihn zweimal gestartet')
  // the sheet half of the same rule: a chat line preserves its whitespace, so the `\n` beside the break
  // would draw a second, empty line — the markdown box is the one that opts out (R-WP-29 keeps it flat)
  expect(declarations('.chat-log .chat-markdown')).toContain('white-space: normal')
  // a real blank line stays what it was: a new paragraph, and no extra break inside the first one
  const twoParagraphs = renderMarkdown('erster Absatz\n\nzweiter Absatz')
  expect(twoParagraphs.container.querySelectorAll('p')).toHaveLength(2)
  expect(twoParagraphs.container.querySelector('br')).toBeNull()
})

// UC-WP-73
test('aTableAndStrikeThroughAreDrawnAndNotTyped', () => {
  const answer = [
    '| Datei | Zeilen |',
    '| --- | ---: |',
    '| pom.xml | 12 |',
    '',
    'das ~~alte~~ Feld',
    '',
    '- [x] gebaut',
    '- [ ] geraucht',
  ].join('\n')
  const { container } = renderMarkdown(answer)

  expect([...container.querySelectorAll('th')].map(head => head.textContent)).toEqual(['Datei', 'Zeilen'])
  expect(container.querySelector('tbody td')?.textContent).toContain('pom.xml')
  expect(container.querySelector('del')?.textContent).toBe('alte')
  // an task list is a list of boxes, not a line starting with brackets
  expect(container.querySelectorAll('input[type="checkbox"]')).toHaveLength(2)
  // the source of a table is not an answer to read
  expect(container.textContent).not.toContain('~~')
  expect(container.textContent).not.toContain('| ---')
})

/**
 * What an answer may never do while it arrives (UC-WP-54): become markup of the page, or take the rest of
 * the answer with it. HTML the model typed is not passed through — the renderer knows no raw nodes.
 */
// UC-WP-54
test('markupOfAnAnswerNeverBecomesMarkupOfThePage', () => {
  const { container } = renderMarkdown(['<img src="x" onerror="alert(1)">', '', 'und <b>fett</b> danach.'].join('\n'))

  expect(container.querySelector('img')).toBeNull()
  expect(container.querySelector('b')).toBeNull()
  expect(container.querySelector('script')).toBeNull()
  // shown as typed instead of dropped: what the model wrote stays readable, and the sentence around it
  // survives — a dropped tag cannot end the line it stands in
  expect(container.textContent).toContain('<b>fett</b> danach.')
  // a link that points into the page's own script engine is no link any more
  const link = renderMarkdown('[klick](javascript:alert(1))').container.querySelector('a')
  expect(link?.getAttribute('href') ?? '').toBe('')
})

// UC-WP-54, ANTD-A5
test('streamingMarkdownStaysReadable', async () => {
  // an answer cut mid-token: emphasis left open, a list that goes on, a fence that never closed
  const half = ['## Antwort', '', 'zwei **fette Worte', '', '- erstes', '', '```ts', 'const a = 1'].join('\n')
  const view = renderMarkdown(half)
  const { container } = view

  expect(container.querySelector('h2')?.textContent).toContain('Antwort')
  expect(container.querySelector('li')?.textContent).toContain('erstes')
  // the open fence already reads as code, not as a paragraph with three backticks in it
  expect(container.querySelector('[data-testid="chat-code"]')).not.toBeNull()
  expect(container.textContent).not.toContain('```')
  // ANTD-A5: what the stream has not closed yet shows as typed — R-WP-28 allows a raw syntax character
  // while the turn runs and requires it rendered once the last token stands (asserted below)
  expect(container.textContent).toContain('**fette Worte')

  // the same view keeps growing: nothing already shown is lost, and the code block stays a code block
  const whole = [
    '## Antwort',
    '',
    'zwei **fette Worte**',
    '',
    '- erstes',
    '- zweites',
    '',
    '```ts',
    'const a = 1',
    '```',
  ].join('\n')
  await grow(view, whole)

  expect(container.querySelector('h2')?.textContent).toContain('Antwort')
  expect(container.querySelectorAll('li')).toHaveLength(2)
  expect(container.querySelector('[data-testid="chat-code"]')).not.toBeNull()
  expect(container.textContent).not.toContain('```')
  // the closed syntax of the last token is rendered, and its characters are gone from the screen
  expect(container.querySelector('strong')?.textContent).toContain('fette Worte')
  expect(container.textContent).not.toContain('**')
})

// UC-WP-54
test('codeBlocksAreHighlighted', async () => {
  const { container } = renderMarkdown('Vorher.\n\n```ts\nconst value: number = 1\n```\n\nDanach mit `inline`.')

  // react-shiki and not a plain pre: the tokens of the theme are what the screen shows (R-WP-28)
  await waitFor(() => expect(container.querySelector('pre.shiki')).not.toBeNull())
  const code = container.querySelector('pre.shiki')
  expect(code?.textContent).toContain('const value: number = 1')
  expect(code?.querySelector('span[style]'), 'a token carries the colour of the theme').not.toBeNull()
  // both themes travel in the markup; styles.css decides which one shows, on the same data-theme the
  // colour tokens of index.css read
  expect(code?.getAttribute('style') ?? '').toContain('--shiki-dark')

  // one box for the fence alone: code inside a sentence is a word of that sentence (R-WP-28)
  expect(container.querySelectorAll('[data-testid="chat-code"]')).toHaveLength(1)
  const inline = [...container.querySelectorAll('code')].find(node => node.textContent === 'inline')
  expect(inline, 'inline code is drawn').toBeDefined()
  expect(inline?.closest('.code-view'), 'but not in the highlighted box').toBeNull()
})

test('aLanguageTheModelInventedIsPlainCode', async () => {
  const { container } = renderMarkdown('Vorher.\n\n```zuul\nhello world\n```\n')

  // nothing to highlight is no error: the block shows its text and the answer around it holds
  await waitFor(() => expect(container.querySelector('pre')).not.toBeNull())
  expect(container.querySelector('pre')?.textContent).toContain('hello world')
  expect(container.textContent).toContain('Vorher.')
})
