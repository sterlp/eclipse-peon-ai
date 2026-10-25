import { beforeEach, expect, test } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'

import { ChatView } from './ChatView'
import { project } from '../test/fixtures'

beforeEach(() => {
  cleanup()
})

// UC-WP-6
test('chatHasNoAgentSwitcherAndGoesToJon', () => {
  const { container } = render(<ChatView project={project()} />)

  expect(screen.getByText(/Projekt: demo/)).toBeInTheDocument()
  expect(screen.queryByRole('combobox')).toBeNull()
  expect(container.querySelector('.empty-state')?.textContent).toContain('Noch kein Agent')
})

test('chatAsksForAProjectWhenNoneIsOpen', () => {
  render(<ChatView project={null} />)

  expect(screen.getByText('Kein Projekt geöffnet')).toBeInTheDocument()
})
