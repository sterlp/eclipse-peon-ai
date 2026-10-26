import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react'

import AppShell from '../../src/app/app.shell'
import { project } from '../shared/fixtures'
import type { DirectoryResponse, FileResponse, FileEntry, FileRoot } from '../../src/project/files.client'

const FILES_PATH = '/api/projects/demo-1a2b3c4/files'

interface Factory {
  /** Every `files` request as the browser sent it — the assertion behind "checkout is the default". */
  filesCalls: string[]
  /** Anything that was not a plain read on that edge — it must stay empty (R-WP-14). */
  nonGetCalls: string[]
  /** Switching this makes the next read answer 400 with exactly this server text. */
  fail: string | null
  closeStream(): Promise<void>
}

let active: Factory | undefined

function entries(...names: string[]): FileEntry[] {
  return names.map(name => ({
    name,
    directory: !name.includes('.'),
    size: name.includes('.') ? 12 : 0,
  }))
}

function directory(root: FileRoot, path: string, names: string[], truncated = false): DirectoryResponse {
  return { kind: 'directory', root, path, entries: entries(...names), truncated }
}

function file(root: FileRoot, path: string, text: string): FileResponse {
  return { kind: 'file', root, path, text, size: text.length, binary: false, truncated: false }
}

/** The checkout the server would answer with — one entry per `root|path` the area can ask for. */
const TREE: Record<string, DirectoryResponse | FileResponse> = {
  'checkout|': directory('checkout', '', ['src', 'pom.xml']),
  'checkout|src': directory('checkout', 'src', ['main', 'test']),
  'checkout|src/main': directory('checkout', 'src/main', ['app.tsx']),
  'checkout|src/main/app.tsx': file('checkout', 'src/main/app.tsx', 'export const app = 1'),
  'tmp|': directory('tmp', '', ['inc4-tmp.txt'], true),
}

/**
 * `fetch` stand-in for the four edges the shell and the chat touch. `files` answers out of `TREE` by
 * `root|path`, so a test asserts on the path the area asked for instead of scripting the sequence.
 */
function stubFactory(): Factory {
  const filesCalls: string[] = []
  const nonGetCalls: string[] = []
  const state: { fail: string | null } = { fail: null }
  let controller: ReadableStreamDefaultController<Uint8Array> | undefined

  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input)
      const method = (init?.method ?? 'GET').toUpperCase()

      if (path.endsWith('/events')) {
        return new Response(
          new ReadableStream<Uint8Array>({
            start(opened) {
              controller = opened
            },
          }),
          { status: 200, headers: { 'content-type': 'text/event-stream' } },
        )
      }
      if (path.endsWith('/messages')) {
        return json(200, [])
      }
      if (path.startsWith(FILES_PATH)) {
        filesCalls.push(path)
        if (method !== 'GET') {
          nonGetCalls.push(`${method} ${path}`)
        }
        if (state.fail !== null) {
          return json(400, { status: 400, error: 'Bad Request', message: state.fail })
        }
        const query = new URLSearchParams(path.slice(path.indexOf('?') + 1))
        const answer = TREE[`${query.get('root')}|${query.get('path')}`]
        return answer === undefined
          ? json(404, { status: 404, error: 'Not Found', message: 'nothing at that path below the checkout root' })
          : json(200, answer)
      }
      return json(200, [project()])
    }),
  )

  const factory: Factory = {
    filesCalls,
    nonGetCalls,
    get fail() {
      return state.fail
    },
    set fail(message: string | null) {
      state.fail = message
    },
    closeStream: async () => {
      await act(async () => {
        controller?.close()
        await new Promise(resolve => setTimeout(resolve, 0))
      })
    },
  }
  active = factory
  return factory
}

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } })
}

/** Renders the shell, opens the project, and lands in its chat — the way Jon reaches the file area. */
async function openProjectChat(): Promise<void> {
  render(<AppShell />)
  fireEvent.click(await screen.findByRole('button', { name: 'demo' }))
  await screen.findByTestId('chat-log')
}

beforeEach(() => {
  cleanup()
  active = undefined
})

afterEach(async () => {
  // an endless stream would keep the reader of the unmounted view pending into the next test
  await active?.closeStream()
  active = undefined
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

// UC-WP-19
test('fileAreaOpensOnCheckoutAndReadsItsWayDown', async () => {
  const factory = stubFactory()
  await openProjectChat()

  // nothing of the checkout is on the screen until the area is opened, and nothing has been read
  expect(screen.queryByTestId('files-panel')).toBeNull()
  expect(screen.queryByRole('button', { name: 'pom.xml (12 B)' })).toBeNull()
  expect(factory.filesCalls).toEqual([])

  fireEvent.click(screen.getByTestId('files-open'))
  await screen.findByTestId('files-list')

  // the default root is the checkout, asked for with an empty relative path
  expect(factory.filesCalls[0]).toBe(`${FILES_PATH}?root=checkout&path=`)
  expect(screen.getByRole('button', { name: 'src/' })).toBeInTheDocument()

  // one level per read: going down asks again, and the answer replaces the list
  fireEvent.click(screen.getByRole('button', { name: 'src/' }))
  expect(await screen.findByText('main/')).toBeInTheDocument()
  expect(factory.filesCalls[1]).toBe(`${FILES_PATH}?root=checkout&path=src`)

  fireEvent.click(screen.getByRole('button', { name: 'main/' }))
  fireEvent.click(await screen.findByRole('button', { name: 'app.tsx (12 B)' }))
  expect(await screen.findByTestId('files-text')).toHaveTextContent('export const app = 1')
  expect(factory.filesCalls[2]).toBe(`${FILES_PATH}?root=checkout&path=src/main`)
  expect(factory.filesCalls[3]).toBe(`${FILES_PATH}?root=checkout&path=src/main/app.tsx`)

  // back is one level up again, not a new chat and not a fourth view
  fireEvent.click(screen.getByTestId('files-back'))
  expect(await screen.findByTestId('files-list')).toHaveTextContent('app.tsx (12 B)')
  expect(screen.getByTestId('files-path')).toHaveTextContent('/src/main')

  // reading is all this area can do: only GETs went out, and the panel offers nothing to write with
  const panel = screen.getByTestId('files-panel')
  const controls = within(panel)
    .queryAllByRole('button')
    .map(button => button.textContent ?? '')
  expect(controls.filter(label => /neu|anlegen|upload|schreiben|bearbeiten|löschen|loeschen|entfernen/i.test(label))).toEqual([])
  expect(within(panel).queryByRole('textbox')).toBeNull()
  expect(factory.nonGetCalls).toEqual([])
})

// UC-WP-19
test('fileAreaSwitchesToSystemTmp', async () => {
  const factory = stubFactory()
  await openProjectChat()
  fireEvent.click(screen.getByTestId('files-open'))
  await screen.findByRole('button', { name: 'pom.xml (12 B)' })

  fireEvent.click(screen.getByTestId('files-root-tmp'))
  expect(await screen.findByRole('button', { name: 'inc4-tmp.txt (12 B)' })).toBeInTheDocument()

  // the other root, from its own root down — a path of the checkout is not carried over
  expect(factory.filesCalls[1]).toBe(`${FILES_PATH}?root=tmp&path=`)
  expect(screen.getByTestId('files-path')).toHaveTextContent('tmp')
  // and the server's bound is a hint, not a silently short list
  expect(screen.getByTestId('files-truncated')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'pom.xml (12 B)' })).toBeNull()
})

// UC-WP-42
test('fileAreaShowsTheRejectionInsteadOfContent', async () => {
  const factory = stubFactory()
  await openProjectChat()
  factory.fail = 'path leaves the checkout root /home/me/.aifactory/projects/demo'

  fireEvent.click(screen.getByTestId('files-open'))

  const alert = await screen.findByRole('alert')
  expect(alert).toHaveTextContent('path leaves the checkout root')
  // no content where the rejected read would have stood, and no dialog in its place
  expect(screen.queryByTestId('files-list')).toBeNull()
  expect(screen.queryByTestId('files-text')).toBeNull()
  // the area stays usable — the next read is one click away
  expect(screen.getByTestId('files-panel')).toBeInTheDocument()
  expect(screen.getByTestId('files-root-tmp')).toBeEnabled()
  expect(screen.getByTestId('files-open')).toBeEnabled()
})
