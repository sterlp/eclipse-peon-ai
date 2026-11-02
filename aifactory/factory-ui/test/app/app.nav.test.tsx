import { beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'

import { AppNav } from '../../src/app/app.nav'

beforeEach(() => {
  cleanup()
})

/** The frame of the app is exactly the three views of R-WP-1 — the test is the counter-probe to a fourth. */
test('theTopNavHasThreeEntries', () => {
  render(<AppNav active="chat" onSelect={() => undefined} />)

  const nav = screen.getByRole('navigation', { name: 'Ansichten' })
  const entries = within(nav).getAllByRole('button')
  expect(entries.map(entry => entry.textContent)).toEqual(['Projekte', 'Chat', 'Einstellungen'])
  // the entry that shows is marked, and only that one — a menu marks its current, not a focus ring
  expect(entries[1]).toHaveAttribute('aria-current', 'page')
  expect(entries.filter(entry => entry.hasAttribute('aria-current'))).toHaveLength(1)
})

test('clickingANavEntrySelectsThatView', () => {
  const onSelect = vi.fn()
  render(<AppNav active="projects" onSelect={onSelect} />)

  const nav = screen.getByRole('navigation', { name: 'Ansichten' })
  fireEvent.click(within(nav).getByRole('button', { name: 'Chat' }))

  expect(onSelect).toHaveBeenCalledWith('chat')
})
