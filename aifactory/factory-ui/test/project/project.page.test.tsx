import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react'
import { useState } from 'react'

import { ProjectPage } from '../../src/project/project.page'
import { listProjects } from '../../src/project/project.client'
import { project } from '../shared/fixtures'
import { content } from '../../src/shared/content'
import type { ProjectDto } from '../../src/shared/server-api'

beforeEach(() => {
  cleanup()
})

afterEach(async () => {
  // a work-state stream that never ends would leave the reader of the unmounted view pending (as in
  // chat.page.test); awaited, because an unfinished act() would overlap the act() of the next test
  await active?.closeStream()
  active = undefined
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

type Script = { GET: unknown; POST: unknown; DELETE: unknown }
type Call = { method: string; path: string; body?: string }

interface Server {
  script: Script
  calls: Call[]
  /** One event of the work-state wire, written as the server writes it. */
  push(event: string, payload: unknown): Promise<void>
  closeStream(): Promise<void>
}

let active: Server | undefined

/**
 * Mutable `fetch` stand-in: `script` can be replaced between the steps of one test (the view
 * reloads the list after a POST/DELETE), every call is recorded. The work-state edge answers an open
 * stream the test writes into, because its events are the whole point of R-WP-24.
 */
function stubServer(initial: Partial<Script>): Server {
  let controller: ReadableStreamDefaultController<Uint8Array> | undefined
  const server: Server = {
    script: { GET: [], POST: undefined, DELETE: undefined, ...initial },
    calls: [],
    push: async (event, payload) => {
      await act(async () => {
        controller?.enqueue(
          new TextEncoder().encode(`event:${event}\ndata:${JSON.stringify(payload)}\n\n`),
        )
        await new Promise(resolve => setTimeout(resolve, 0))
      })
    },
    closeStream: async () => {
      await act(async () => {
        controller?.close()
        controller = undefined
        await new Promise(resolve => setTimeout(resolve, 0))
      })
    },
  }
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const method = (init?.method ?? 'GET').toUpperCase()
      server.calls.push({ method, path: String(input), body: init?.body as string | undefined })
      if (String(input).endsWith('/events')) {
        return new Response(
          new ReadableStream<Uint8Array>({
            start(opened) {
              controller = opened
            },
          }),
          { status: 200, headers: { 'content-type': 'text/event-stream' } },
        )
      }
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
  active = server
  return server
}

function response(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

/**
 * The frame in miniature: it owns the list (D1) exactly as `useProjectList` does, only without the tree —
 * so a test of this page reads nothing at mount and the row appears through `onReload` alone.
 */
function Host({ initial = [] }: { initial?: ProjectDto[] }) {
  const [projects, setProjects] = useState<ProjectDto[]>(initial)
  return (
    <ProjectPage
      projects={projects}
      onOpen={() => undefined}
      onReload={async () => setProjects(await listProjects())}
    />
  )
}

test('showsEmptyStateWhenRegistryIsEmpty', async () => {
  stubServer({ GET: [] })
  render(<Host />)

  expect(await screen.findByText(content.projects.empty)).toBeInTheDocument()
})

// UC-WP-2
test('listsProjectAfterSuccessfulAdd', async () => {
  const added = project()
  const server = stubServer({ GET: [] })
  render(<Host />)
  await screen.findByText(content.projects.empty)

  server.script = { GET: [added], POST: added, DELETE: undefined }
  fireEvent.change(screen.getByLabelText(content.projects.gitUrl), {
    target: { value: 'https://git.example.com/acme/demo.git' },
  })
  fireEvent.click(screen.getByRole('button', { name: content.projects.add }))

  expect(await screen.findByText('demo')).toBeInTheDocument()
  expect(screen.queryByText(content.projects.empty)).not.toBeInTheDocument()
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
  render(<Host />)
  await screen.findByText(content.projects.empty)

  fireEvent.change(screen.getByLabelText(content.projects.gitUrl), {
    target: { value: 'https://git.example.com/acme/gone.git' },
  })
  fireEvent.click(screen.getByRole('button', { name: content.projects.add }))

  expect(await screen.findByRole('alert')).toHaveTextContent('fatal: repository not found')
  expect(screen.getByText(content.projects.empty)).toBeInTheDocument()
  expect(screen.queryByText('gone')).not.toBeInTheDocument()
})

// UC-WP-3
test('sameUrlReusesExistingClone', async () => {
  const existing = project()
  const server = stubServer({ GET: [existing], POST: existing })
  render(<Host initial={[existing]} />)
  await screen.findByText('demo')

  fireEvent.change(screen.getByLabelText(content.projects.gitUrl), {
    target: { value: 'https://git.example.com/acme/demo/' },
  })
  fireEvent.click(screen.getByRole('button', { name: content.projects.add }))

  await waitFor(() => expect(server.calls.filter(call => call.method === 'POST')).toHaveLength(1))
  expect(screen.queryByRole('alert')).toBeNull() // reuse is not an error (R-WP-2)
  expect(screen.getAllByText('demo')).toHaveLength(1)
  expect(screen.getByLabelText(content.projects.gitUrl)).toHaveValue('')
})

// UC-WP-30
test('credentialUrlRejectedAndNothingStored', async () => {
  stubServer({
    GET: [],
    POST: new Error('git urls must not carry credentials — public https repositories only'),
  })
  render(<Host />)
  await screen.findByText(content.projects.empty)

  fireEvent.change(screen.getByLabelText(content.projects.gitUrl), {
    target: { value: 'https://user:token@git.example.com/acme/demo.git' },
  })
  fireEvent.click(screen.getByRole('button', { name: content.projects.add }))

  const alert = await screen.findByRole('alert')
  expect(alert).toHaveTextContent('credentials')
  expect(alert.textContent).not.toContain('user')
  expect(alert.textContent).not.toContain('token')
  expect(screen.getByText(content.projects.empty)).toBeInTheDocument()
})

// UC-WP-28
/**
 * ANTD-A2 (R-WP-39): the confirmation is the basis' `Popconfirm` at the row instead of `window.confirm` — the
 * question is on the screen, so it is read there, and "nothing deleted while it stands" is read off the
 * calls to the edge rather than off a spy of a browser dialog.
 */
test('asksForConfirmationBeforeDelete', async () => {
  const server = stubServer({ GET: [project()] })
  render(<Host initial={[project()]} />)
  await screen.findByText('demo')

  fireEvent.click(screen.getByRole('button', { name: content.projects.remove }))

  expect(await screen.findByText(content.projects.removeQuestion('demo'))).toBeInTheDocument()
  expect(server.calls.filter(call => call.method === 'DELETE')).toEqual([])
  expect(screen.getByText('demo')).toBeInTheDocument()
})

test('removesProjectAfterConfirmation', async () => {
  const server = stubServer({ GET: [project()] })
  render(<Host initial={[project()]} />)
  await screen.findByText('demo')

  server.script = { GET: [], POST: undefined, DELETE: undefined }
  fireEvent.click(screen.getByRole('button', { name: content.projects.remove }))
  fireEvent.click(await screen.findByRole('button', { name: content.common.yes }))

  expect(await screen.findByText(content.projects.empty)).toBeInTheDocument()
  expect(
    server.calls.some(call => call.method === 'DELETE' && call.path.includes('demo-1a2b3c4')),
  ).toBe(true)
})


// UC-WP-47
test('theListShowsWorkingAndQueued', async () => {
  const demo = project()
  const server = stubServer({ GET: [demo] })
  render(<Host initial={[demo]} />)
  const row = (await screen.findByText('demo')).closest('li') as HTMLElement

  // nothing told about a project means free — the same answer a restart gives (R-WP-24)
  expect(row).toHaveTextContent(content.projects.free)

  await server.push('projects', { projects: [{ projectId: demo.id, working: true, queued: 0 }] })
  expect(row).toHaveTextContent(content.projects.working)
  expect(row.textContent).not.toContain('queued')

  await server.push('project', { projectId: demo.id, working: true, queued: 1 })
  expect(row).toHaveTextContent(content.projects.working)
  expect(row).toHaveTextContent(content.projects.queued(1))

  // the page no longer reads the list at all — the frame owns that read (D1), so counting here is counting
  // something this view could only do to itself
  const reads = server.calls.filter(call => call.path === '/api/projects').length
  expect(reads, 'the rows came from the props, not from a read of this page').toBe(0)
  await server.push('project', { projectId: demo.id, working: false, queued: 0 })
  expect(row).toHaveTextContent(content.projects.free)
  expect(row.textContent).not.toContain(content.projects.working)
  // a work state is a picture of the runtime and never a reason to read the registry again (R-WP-24)
  expect(server.calls.filter(call => call.path === '/api/projects')).toHaveLength(reads)
})
