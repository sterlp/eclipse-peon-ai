import { beforeEach, expect, test } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'

import { CodeView } from '../../src/shared/code'
import { declarations, declares } from './sheets'

beforeEach(() => {
  cleanup()
})

// UC-WP-61
test('codeLinesAreNotDoubled', async () => {
  render(<CodeView code={'one\ntwo\nthree'} language="text" className="files-viewer-code" testId="code-view" />)
  const box = await screen.findByTestId('code-view')
  await waitFor(() => expect(box.querySelectorAll('.line')).toHaveLength(3))

  // Shiki sets a literal `\n` between its line spans (classic structure) — inside a `<pre>` that newline IS
  // the line break, so a box of its own on every line adds a second, empty line under each of them: the
  // doubled height Paul read in the viewer. jsdom lays out nothing, so the contract is read as written.
  const code = box.querySelector('pre code')
  const breaks = [...(code?.childNodes ?? [])].filter(node => node.textContent === '\n')
  expect(breaks, 'the newlines between the line spans are what break the line').toHaveLength(2)
  expect(declares('.code-view .line'), 'a line carries no box of its own').toBe(false)
  expect(declarations('.code-view pre'), 'one line height stands on the block').toContain('line-height')
})
