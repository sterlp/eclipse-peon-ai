import { beforeEach, expect, test } from 'vitest'
import { act, cleanup, render, waitFor } from '@testing-library/react'
import type { RenderResult } from '@testing-library/react'

import { ChatMarkdown } from '../../src/chat/chat.markdown'

/**
 * Lets the renderer settle: a streamed document is parsed off the render pass, so the first paint is not
 * the last one. The wait sits inside `act`, because that settling is exactly what the user watches.
 */
async function settle(): Promise<void> {
  await act(async () => {
    await new Promise(resolve => setTimeout(resolve, 0))
  })
}

/** One answer, one component: a streamed document is grown in place, never rendered into a second one. */
async function grow(view: RenderResult, text: string, final: boolean): Promise<void> {
  await act(async () => {
    view.rerender(<ChatMarkdown text={text} final={final} />)
    await settle()
  })
}

async function renderMarkdown(text: string, final: boolean): Promise<RenderResult> {
  const view = render(<ChatMarkdown text={text} final={final} />)
  await settle()
  return view
}

beforeEach(() => {
  cleanup()
})

// UC-WP-54
test('streamingMarkdownStaysReadable', async () => {
  // an answer cut mid-token: emphasis left open, a list that goes on, a fence that never closed
  const half = ['## Antwort', '', 'zwei **fette Worte', '', '- erstes', '', '```ts', 'const a = 1'].join('\n')
  const view = await renderMarkdown(half, false)
  const { container } = view

  expect(container.querySelector('h2')?.textContent).toContain('Antwort')
  expect(container.querySelector('li')?.textContent).toContain('erstes')
  // what the stream left open is closed by the renderer and never shown as markup of its own
  expect(container.querySelector('strong')?.textContent).toContain('fette Worte')
  expect(container.textContent).not.toContain('**')
  // the open fence already reads as code, not as a paragraph with three backticks in it
  expect(container.querySelector('[data-testid="chat-code"]')).not.toBeNull()
  expect(container.textContent).not.toContain('```')

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
  await grow(view, whole, false)

  expect(container.querySelector('h2')?.textContent).toContain('Antwort')
  expect(container.querySelectorAll('li')).toHaveLength(2)
  expect(container.querySelector('[data-testid="chat-code"]')).not.toBeNull()
  expect(container.textContent).not.toContain('```')

  await grow(view, whole, true)
  expect(container.querySelector('strong')?.textContent).toContain('fette Worte')
})

// UC-WP-54
test('codeBlocksAreHighlighted', async () => {
  const { container } = await renderMarkdown('Vorher.\n\n```ts\nconst value: number = 1\n```\n', true)

  // react-shiki and not a plain pre: the tokens of the theme are what the screen shows (R-WP-28)
  await waitFor(() => expect(container.querySelector('pre.shiki')).not.toBeNull())
  const code = container.querySelector('pre.shiki')
  expect(code?.textContent).toContain('const value: number = 1')
  expect(code?.querySelector('span[style]'), 'a token carries the colour of the theme').not.toBeNull()
  // both themes travel in the markup; styles.css decides which one shows, on the same data-theme the
  // colour tokens of index.css read
  expect(code?.getAttribute('style') ?? '').toContain('--shiki-dark')
})

test('aLanguageTheModelInventedIsPlainCode', async () => {
  const { container } = await renderMarkdown('Vorher.\n\n```zuul\nhello world\n```\n', true)

  // nothing to highlight is no error: the block shows its text and the answer around it holds
  await waitFor(() => expect(container.querySelector('pre')).not.toBeNull())
  expect(container.querySelector('pre')?.textContent).toContain('hello world')
  expect(container.textContent).toContain('Vorher.')
})
