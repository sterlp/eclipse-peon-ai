import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, screen } from '@testing-library/react'

import { renderApp } from '../shared/app.harness'
import { readUserName, writeUserName } from '../../src/shared/user.name'

/** The key of R-WP-17, pinned as the contract of the browser store: a renamed key would empty every browser. */
const STORAGE_KEY = 'aifactory.user.name'

beforeEach(() => {
  cleanup()
  localStorage.clear()
  stubEmptyList()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

/** The app reads the project list on mount; this file never needs an answer beyond "there is none". */
function stubEmptyList(): void {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response('[]', { status: 200, headers: { 'content-type': 'application/json' } })),
  )
}

// UC-WP-25
test('theNameIsKeptInTheBrowser', async () => {
  const first = renderApp()
  fireEvent.change(await screen.findByTestId('user-name'), { target: { value: 'Anna' } })

  expect(screen.getByTestId('user-name')).toHaveValue('Anna')
  expect(readUserName()).toBe('Anna')
  expect(localStorage.getItem(STORAGE_KEY)).toBe('Anna')

  // a reload of the same browser is a fresh mount reading its own store — no server, no login (R-WP-17)
  first.unmount()
  renderApp()

  expect(await screen.findByTestId('user-name')).toHaveValue('Anna')
})

// UC-WP-25
test('aFreshBrowserHasNoName', async () => {
  renderApp()

  // nothing was typed and nothing is invented: no name is a state the chat handles (R-WP-25)
  expect(await screen.findByTestId('user-name')).toHaveValue('')
  expect(readUserName()).toBeNull()
})

// UC-WP-25
test('aBlankNameIsNotStored', async () => {
  writeUserName('   ')
  expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
  expect(readUserName()).toBeNull()

  // and erasing a name forgets it: the browser is nameless again, not "Anna with a space"
  writeUserName('Anna')
  renderApp()
  fireEvent.change(await screen.findByTestId('user-name'), { target: { value: '   ' } })

  expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
  expect(readUserName()).toBeNull()
})
