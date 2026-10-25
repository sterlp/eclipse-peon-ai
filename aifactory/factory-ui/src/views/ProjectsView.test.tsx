import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'

import App from '../App'
import { ProjectsView } from './ProjectsView'
import { project } from '../test/fixtures'

beforeEach(() => {
  cleanup()
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

type Script = { GET: unknown; POST: unknown; DELETE: unknown }
type Call = { method: string; path: string; body?: string }

/**
 * Mutable `fetch` stand-in: `script` can be replaced between the steps of one test (the view
 * reloads the list after a POST/DELETE), every call is recorded.
 */
function stubServer(initial: Partial<Script>): { script: Script; calls: Call[] } {
  const server: { script: Script; calls: Call[] } = {
    script: { GET: [], POST: undefined, DELETE: undefined, ...initial },
    calls: [],
  }
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const method = (init?.method ?? 'GET').toUpperCase()
      server.calls.push({ method, path: String(input), body: init?.body as string | undefined })
      const answer = server.script[method as keyof Script]
      if (answer instanceof Error) {
        return response(400, { status: 400, error: 'Bad Request', message: answer.message })
      }
      if (answer === undefined) {
        return method === 'DELETE'
          ? new Response(null, { status: 204 })
          : response(404, { status: 404, error: 'Not Found', message: 'nope' })
      }
      return response(method === 'POST' ? 201 : 200, answer)
    }),
  )
  return server
}

function response(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

// UC-WP-1
test('showsExactlyThreeViews', async () => {
  stubServer({ GET: [] })
  render(<App />)

  const tabs = screen.getAllByRole('tab')
  expect(tabs.map(tab => tab.textContent)).toEqual(['Projekte', 'Chat', 'Einstellungen'])
  expect(tabs[0]).toHaveAttribute('aria-selected', 'true')
  await screen.findByText('Kein Projekt')
})

test('showsEmptyStateWhenRegistryIsEmpty', async () => {
  stubServer({ GET: [] })
  render(<ProjectsView onOpen={() => undefined} />)

  expect(await screen.findByText('Kein Projekt')).toBeInTheDocument()
})

// UC-WP-2
test('listsProjectAfterSuccessfulAdd', async () => {
  const added = project()
  const server = stubServer({ GET: [] })
  render(<ProjectsView onOpen={() => undefined} />)
  await screen.findByText('Kein Projekt')

  server.script = { GET: [added], POST: added, DELETE: undefined }
  fireEvent.change(screen.getByLabelText('Git-URL'), {
    target: { value: 'https://git.example.com/acme/demo.git' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }))

  expect(await screen.findByText('demo')).toBeInTheDocument()
  expect(screen.queryByText('Kein Projekt')).not.toBeInTheDocument()
  const post = server.calls.filter(call => call.method === 'POST')
  expect(post).toHaveLength(1)
  expect(post[0].body).toContain('https://git.example.com/acme/demo.git')
})

// UC-WP-4
test('showsGitErrorAtInputAndKeepsList', async () => {
  stubServer({
    GET: [],
    POST: new Error('fatal: repository not found'),
  })
  render(<ProjectsView onOpen={() => undefined} />)
  await screen.findByText('Kein Projekt')

  fireEvent.change(screen.getByLabelText('Git-URL'), {
    target: { value: 'https://git.example.com/acme/gone.git' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }))

  expect(await screen.findByRole('alert')).toHaveTextContent('fatal: repository not found')
  expect(screen.getByText('Kein Projekt')).toBeInTheDocument()
  expect(screen.queryByText('gone')).not.toBeInTheDocument()
})

// UC-WP-3
test('sameUrlReusesExistingClone', async () => {
  const existing = project()
  const server = stubServer({ GET: [existing], POST: existing })
  render(<ProjectsView onOpen={() => undefined} />)
  await screen.findByText('demo')

  fireEvent.change(screen.getByLabelText('Git-URL'), {
    target: { value: 'https://git.example.com/acme/demo/' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }))

  await waitFor(() => expect(server.calls.filter(call => call.method === 'POST')).toHaveLength(1))
  expect(screen.queryByRole('alert')).toBeNull() // reuse is not an error (R-WP-2)
  expect(screen.getAllByText('demo')).toHaveLength(1)
  expect(screen.getByLabelText('Git-URL')).toHaveValue('')
})

// UC-WP-30
test('credentialUrlRejectedAndNothingStored', async () => {
  stubServer({
    GET: [],
    POST: new Error('git urls must not carry credentials — public https repositories only'),
  })
  render(<ProjectsView onOpen={() => undefined} />)
  await screen.findByText('Kein Projekt')

  fireEvent.change(screen.getByLabelText('Git-URL'), {
    target: { value: 'https://user:token@git.example.com/acme/demo.git' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }))

  const alert = await screen.findByRole('alert')
  expect(alert).toHaveTextContent('credentials')
  expect(alert.textContent).not.toContain('user')
  expect(alert.textContent).not.toContain('token')
  expect(screen.getByText('Kein Projekt')).toBeInTheDocument()
})

// UC-WP-28
test('asksForConfirmationBeforeDelete', async () => {
  stubServer({ GET: [project()] })
  const confirmed = vi.spyOn(window, 'confirm').mockImplementation(() => false)
  render(<ProjectsView onOpen={() => undefined} />)
  await screen.findByText('demo')

  fireEvent.click(screen.getByRole('button', { name: 'Entfernen' }))

  expect(confirmed).toHaveBeenCalledTimes(1)
  expect(screen.getByText('demo')).toBeInTheDocument()
})

test('removesProjectAfterConfirmation', async () => {
  const server = stubServer({ GET: [project()] })
  vi.spyOn(window, 'confirm').mockImplementation(() => true)
  render(<ProjectsView onOpen={() => undefined} />)
  await screen.findByText('demo')

  server.script = { GET: [], POST: undefined, DELETE: undefined }
  fireEvent.click(screen.getByRole('button', { name: 'Entfernen' }))

  expect(await screen.findByText('Kein Projekt')).toBeInTheDocument()
  expect(
    server.calls.some(call => call.method === 'DELETE' && call.path.includes('demo-1a2b3c4')),
  ).toBe(true)
})
