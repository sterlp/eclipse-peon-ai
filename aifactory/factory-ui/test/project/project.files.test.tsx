import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { act, cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'

import { renderApp } from '../shared/app.harness'
import { project } from '../shared/fixtures'
import { declarations } from '../shared/sheets'
import { content } from '../../src/shared/content'
import type { DirectoryResponse, FileResponse, FileEntry, FileRoot } from '../../src/project/files.client'

const FILES_PATH = '/api/projects/demo-1a2b3c4/files'

interface Factory {
  /** Every `files` request as the browser sent it — the assertion behind "checkout is the default". */
  filesCalls: string[]
  /** Anything that was not a plain read on that edge — it must stay empty (R-WP-14). */
  nonGetCalls: string[]
  /**
   * Which read the server refuses: `'all'` for every one, or one `root|path` for a single level. A level
   * answers 400 with exactly this server text (UC-WP-42).
   */
  refuse(key: string | null): void
  closeStream(): Promise<void>
}

let active: Factory | undefined

function entries(...names: string[]): FileEntry[] {
  return names.map(name => ({
    name,
    directory: !name.includes('.'),
    size: name.includes('.') ? 12 : 0,
    symlink: false,
  }))
}

function directory(root: FileRoot, path: string, names: string[], truncated = false): DirectoryResponse {
  return { kind: 'directory', root, path, entries: entries(...names), truncated }
}

function file(root: FileRoot, path: string, text: string): FileResponse {
  return { kind: 'file', root, path, text, size: text.length, binary: false, truncated: false }
}

/**
 * A directory holding one link: the server marks it as a link and sends no metadata of its target —
 * `size: 0` is "not followed", not "empty".
 */
const LINKED: DirectoryResponse = {
  kind: 'directory',
  root: 'checkout',
  path: 'links',
  entries: [
    { name: 'inside-link.md', directory: false, size: 0, symlink: true },
    { name: 'real.md', directory: false, size: 12, symlink: false },
  ],
  truncated: false,
}

/** The checkout the server would answer with — one entry per `root|path` the tree can ask for. */
const TREE: Record<string, DirectoryResponse | FileResponse> = {
  'checkout|': directory('checkout', '', ['src', 'pom.xml', 'links']),
  'checkout|links': LINKED,
  'checkout|src': directory('checkout', 'src', ['main', 'test']),
  'checkout|src/main': directory('checkout', 'src/main', ['app.tsx']),
  'checkout|src/main/app.tsx': file('checkout', 'src/main/app.tsx', 'export const app = 1'),
  'tmp|': directory('tmp', '', ['inc4-tmp.txt'], true),
}

/**
 * `fetch` stand-in for the edges the shell and the chat touch. `files` answers out of `TREE` by
 * `root|path`, so a test asserts on the path the tree asked for instead of scripting the sequence.
 */
function stubFactory(): Factory {
  const filesCalls: string[] = []
  const nonGetCalls: string[] = []
  const state: { refused: string | null } = { refused: null }
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
      // the tile family has no part in this class — an empty roster keeps the chat head out of the way
      if (path.endsWith('/agents')) {
        return json(200, [])
      }
      if (path.startsWith(FILES_PATH)) {
        filesCalls.push(path)
        if (method !== 'GET') {
          nonGetCalls.push(`${method} ${path}`)
        }
        const query = new URLSearchParams(path.slice(path.indexOf('?') + 1))
        const asked = `${query.get('root')}|${query.get('path')}`
        if (state.refused === 'all' || state.refused === asked) {
          return json(400, {
            status: 400,
            error: 'Bad Request',
            message: 'path leaves the checkout root /home/me/.aifactory/projects/demo',
          })
        }
        const answer = TREE[asked]
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
    refuse: key => {
      state.refused = key
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
async function openProjectChat(): Promise<{ path(): string }> {
  const app = renderApp()
  fireEvent.click(await screen.findByRole('button', { name: 'demo' }))
  await screen.findByTestId('chat-log')
  return app
}

/** The way down a tree is walked: open the area, then one level per click, then the file. */
async function openFileInTree(name: string): Promise<void> {
  fireEvent.click(screen.getByTestId('files-open'))
  await screen.findByTestId('files-tree')
  fireEvent.click(screen.getByRole('button', { name: 'src/' }))
  await screen.findByRole('button', { name: 'main/' })
  fireEvent.click(screen.getByRole('button', { name: 'main/' }))
  fireEvent.click(await screen.findByRole('button', { name: name }))
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
  await screen.findByTestId('files-tree')

  // the default root is the checkout, asked for with an empty relative path
  expect(factory.filesCalls[0]).toBe(`${FILES_PATH}?root=checkout&path=`)
  expect(screen.getByRole('button', { name: 'src/' })).toBeInTheDocument()

  // one level per read: opening a directory asks for it, and its rows belong to it
  fireEvent.click(screen.getByRole('button', { name: 'src/' }))
  expect(await screen.findByRole('button', { name: 'main/' })).toBeInTheDocument()
  expect(factory.filesCalls[1]).toBe(`${FILES_PATH}?root=checkout&path=src`)
  const source = screen.getByRole('button', { name: 'src/' }).closest('li')
  expect(source?.contains(screen.getByRole('button', { name: 'main/' })), 'main/ stands in src/').toBe(true)

  // a file is another read, and the viewer names the path it read
  fireEvent.click(screen.getByRole('button', { name: 'main/' }))
  fireEvent.click(await screen.findByRole('button', { name: 'app.tsx (12 B)' }))
  expect(factory.filesCalls[2]).toBe(`${FILES_PATH}?root=checkout&path=src/main`)
  expect(factory.filesCalls[3]).toBe(`${FILES_PATH}?root=checkout&path=src/main/app.tsx`)
  // Shiki answers one step later than the click: the viewer is there, the text arrives with it
  const viewer = await screen.findByTestId('files-text')
  await waitFor(() => expect(viewer.textContent).toContain('export const app = 1'))
  expect(screen.getByTestId('files-viewer-path')).toHaveTextContent('checkout · /src/main/app.tsx')

  // the way up is the row itself: folding takes its children with it and asks for nothing again
  const asked = factory.filesCalls.length
  fireEvent.click(screen.getByRole('button', { name: 'src/' }))
  await waitFor(() => expect(screen.queryByRole('button', { name: 'main/' })).toBeNull())
  expect(factory.filesCalls, 'folding is not a read').toHaveLength(asked)
  expect(screen.getByRole('button', { name: 'src/' })).toHaveAttribute('aria-expanded', 'false')

  // reading is all this area can do: only GETs went out, and the panel offers nothing to write with
  const panel = screen.getByTestId('files-panel')
  const controls = within(panel)
    .queryAllByRole('button')
    .map(button => button.textContent ?? '')
  expect(
    controls.filter(label =>
      /\b(new|create|upload|write|edit|delete|remove)\b|neu|anlegen|upload|schreiben|bearbeiten|löschen|loeschen|entfernen/i.test(
        label,
      ),
    ),
  ).toEqual([])
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
  // the chosen card says so, and the other one does not
  expect(screen.getByTestId('files-root-tmp')).toHaveAttribute('aria-pressed', 'true')
  expect(screen.getByTestId('files-root-checkout')).toHaveAttribute('aria-pressed', 'false')
  // and the server's bound is a hint, not a silently short list
  expect(screen.getByTestId('files-panel').textContent).toContain(content.files.truncated)
  expect(screen.queryByRole('button', { name: 'pom.xml (12 B)' })).toBeNull()

  // back to the checkout is the level that was already read — no second read of the same path
  const asked = factory.filesCalls.length
  fireEvent.click(screen.getByTestId('files-root-checkout'))
  expect(await screen.findByRole('button', { name: 'src/' })).toBeInTheDocument()
  expect(factory.filesCalls).toHaveLength(asked)
})

// UC-WP-42
test('fileAreaShowsTheRejectionInsteadOfContent', async () => {
  const factory = stubFactory()
  await openProjectChat()
  factory.refuse('all')

  fireEvent.click(screen.getByTestId('files-open'))

  const alert = await screen.findByRole('alert')
  expect(alert).toHaveTextContent('path leaves the checkout root')
  // no content where the rejected read would have stood, and no dialog in its place
  expect(screen.queryByRole('button', { name: 'pom.xml (12 B)' })).toBeNull()
  expect(screen.queryByTestId('files-text')).toBeNull()
  // the area stays usable — the next read is one click away
  expect(screen.getByTestId('files-panel')).toBeInTheDocument()
  expect(screen.getByTestId('files-root-tmp')).toBeEnabled()
  expect(screen.getByTestId('files-open')).toBeEnabled()
})

// UC-WP-42
test('aLevelThatLeavesItsRootIsRefusedWhereItStands', async () => {
  const factory = stubFactory()
  await openProjectChat()
  fireEvent.click(screen.getByTestId('files-open'))
  await screen.findByRole('button', { name: 'pom.xml (12 B)' })

  // the escape is refused in the level it belongs to, where the children would have stood
  factory.refuse('checkout|src')
  fireEvent.click(screen.getByRole('button', { name: 'src/' }))
  const alert = await screen.findByRole('alert')
  expect(alert).toHaveTextContent('path leaves the checkout root')
  expect(screen.queryByRole('button', { name: 'main/' })).toBeNull()
  // and the rest of the tree is untouched by it: the sibling rows still read
  expect(screen.getByRole('button', { name: 'pom.xml (12 B)' })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'links/' })).toBeInTheDocument()

  // the line is a read that failed, not a verdict: folding and opening asks the level again
  const asked = factory.filesCalls.length
  fireEvent.click(screen.getByRole('button', { name: 'src/' }))
  factory.refuse('checkout|src/main')
  fireEvent.click(screen.getByRole('button', { name: 'src/' }))
  expect(await screen.findByRole('button', { name: 'main/' })).toBeInTheDocument()
  expect(screen.queryByRole('alert'), 'the level that answered needs no line').toBeNull()

  // the level below it can still be refused, and the refusal still says which root it left
  fireEvent.click(screen.getByRole('button', { name: 'main/' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('path leaves the checkout root')
  expect(factory.filesCalls.length).toBeGreaterThan(asked)
})

// UC-WP-42
test('aListedLinkSaysLinkAndNoByteCount', async () => {
  stubFactory()
  await openProjectChat()

  fireEvent.click(screen.getByTestId('files-open'))
  fireEvent.click(await screen.findByRole('button', { name: 'links/' }))

  const link = await screen.findByRole('button', { name: 'inside-link.md (link)' })
  expect(link.textContent).toContain('link')
  // "0 B" would read as an empty file — the listing of a link says only that it was not followed
  expect(link.textContent).not.toMatch(/0 B/)
  // its sibling keeps its size: the label is the link's, not a general removal of sizes
  expect(screen.getByRole('button', { name: 'real.md (12 B)' })).toBeInTheDocument()
})

// UC-WP-60
test('theFilesTabOpensTheSlideOut', async () => {
  stubFactory()
  await openProjectChat()

  const tab = screen.getByTestId('files-open')
  expect(tab).toHaveTextContent(content.files.open)
  expect(tab).toHaveAttribute('aria-expanded', 'false')
  expect(tab.getAttribute('aria-label')).toBe(content.files.openArea)
  // ANTD-A4 (R-WP-39): the icon set of the basis names the direction in its class (`anticon-left`,
  // `anticon-right`) — the same claim about the arrow, read where the new set states it
  expect(tab.querySelector('.anticon-left'), 'a closed tab points inwards').not.toBeNull()

  fireEvent.click(tab)
  await screen.findByTestId('files-panel')
  expect(tab).toHaveAttribute('aria-expanded', 'true')
  expect(tab.getAttribute('aria-label')).toBe(content.files.collapseArea)
  expect(tab.querySelector('.anticon-right'), 'an open tab points back out').not.toBeNull()

  // the two roots as cards, each one naming what it holds
  const checkout = screen.getByTestId('files-root-checkout')
  expect(checkout).toHaveTextContent(content.files.roots.checkout)
  expect(checkout).toHaveTextContent(content.files.roots.checkoutHint)
  expect(screen.getByTestId('files-root-tmp')).toHaveTextContent(content.files.roots.tmpHint)
  expect(within(screen.getByTestId('files-panel')).getByRole('group', { name: content.files.root }))
    .toBeInTheDocument()
  // the drawer of the basis is a named dialog, and its own close is the way out — no second visible word
  expect(screen.getByRole('dialog')).toHaveAccessibleName(content.files.title)
  expect(screen.getByTestId('files-close')).toBeInTheDocument()
})

// UC-WP-60
test('theTreeExpandsALevel', async () => {
  const factory = stubFactory()
  await openProjectChat()
  fireEvent.click(screen.getByTestId('files-open'))
  await screen.findByTestId('files-tree')

  // a tree of nothing but the root: no read below it before a row is opened
  expect(factory.filesCalls).toEqual([`${FILES_PATH}?root=checkout&path=`])
  expect(screen.queryByRole('button', { name: 'main/' })).toBeNull()

  fireEvent.click(screen.getByRole('button', { name: 'src/' }))
  expect(await screen.findByRole('button', { name: 'test/' })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'src/' })).toHaveAttribute('aria-expanded', 'true')
  // one read for the level that opened, and none for the level behind it
  expect(factory.filesCalls).toEqual([`${FILES_PATH}?root=checkout&path=`, `${FILES_PATH}?root=checkout&path=src`])

  // a second opening shows what was read; folding takes the level away again
  fireEvent.click(screen.getByRole('button', { name: 'src/' }))
  expect(screen.queryByRole('button', { name: 'test/' })).toBeNull()
  expect(factory.filesCalls).toHaveLength(2)
  fireEvent.click(screen.getByRole('button', { name: 'src/' }))
  expect(await screen.findByRole('button', { name: 'test/' })).toBeInTheDocument()
  expect(factory.filesCalls, 'an answered level is not asked twice').toHaveLength(2)
})

// UC-WP-60
test('thePanelDoesNotRepeatTheProjectName', async () => {
  stubFactory()
  await openProjectChat()
  fireEvent.click(screen.getByTestId('files-open'))

  const panel = await screen.findByTestId('files-panel')
  expect(screen.getByRole('heading', { name: project().name }), 'the name stands once, in the chat head')
    .toBeInTheDocument()
  expect(panel.textContent, 'the panel is about files, not about which project').not.toContain(project().name)
  // it says what it holds without the name: the cards name the roots
  expect(panel.textContent).toContain(content.files.roots.tmp)
})

// UC-WP-61
test('theViewerHighlightsTheFile', async () => {
  stubFactory()
  await openProjectChat()
  await openFileInTree('app.tsx (12 B)')

  const viewer = await screen.findByTestId('files-text')
  // react-shiki and not a plain pre: the file is read as the language its suffix names
  await waitFor(() => expect(viewer.querySelector('pre.shiki')).not.toBeNull())
  expect(viewer.textContent).toContain('export const app = 1')
  expect(viewer.querySelector('span[style]'), 'a token carries the colour of the theme').not.toBeNull()
  expect(viewer.querySelector('pre.shiki')?.getAttribute('style') ?? '').toContain('--shiki-dark')
})

// UC-WP-61
test('theViewerCanAlwaysBeClosed', async () => {
  stubFactory()
  await openProjectChat()
  await openFileInTree('app.tsx (12 B)')
  await screen.findByTestId('files-viewer')

  // the viewer's own way back leaves the tree exactly where it was
  fireEvent.click(screen.getByTestId('files-viewer-close'))
  await waitFor(() => expect(screen.queryByTestId('files-viewer')).toBeNull())
  expect(screen.getByTestId('files-tree')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'main/' })).toBeInTheDocument()

  // and the area closes from the close of the drawer, with the chat beside it untouched
  fireEvent.click(screen.getByTestId('files-close'))
  await waitFor(() => expect(screen.queryByTestId('files-panel')).toBeNull())
  expect(screen.getByRole('region', { name: content.chat.title })).toBeInTheDocument()
  expect(screen.getByTestId('files-open')).toHaveAttribute('aria-expanded', 'false')
})

// UC-WP-60
test('escapeClosesTheFilesDrawer', async () => {
  // closing is always possible, and Esc is the basis' own gesture: it hangs on the window, so it reaches the
  // drawer from wherever the focus stands — here from the field of the chat, where a reader is typing
  stubFactory()
  const app = await openProjectChat()
  const field = screen.getByTestId('chat-input') as HTMLElement
  field.focus()
  fireEvent.click(screen.getByTestId('files-open'))
  await screen.findByTestId('files-panel')

  fireEvent.keyDown(window, { key: 'Escape' })

  await waitFor(() => {
    expect(screen.queryByTestId('files-panel')).toBeNull()
    expect(app.path(), 'closing is the address stepping back').not.toContain('/files')
  })
  expect(screen.getByTestId('files-open')).toHaveAttribute('aria-expanded', 'false')
})

// UC-WP-61
test('theChatStaysVisibleWhileTheViewerIsOpen', async () => {
  stubFactory()
  await openProjectChat()
  await openFileInTree('app.tsx (12 B)')
  await screen.findByTestId('files-viewer')

  const chat = screen.getByRole('region', { name: content.chat.title })
  const panel = screen.getByTestId('files-panel')
  // the conversation is still on the screen: its field is there and reachable
  expect(within(chat).getByRole('textbox')).toBeEnabled()
  // the overlay stands inside the chat's own row instead of in a portal of its own — that is what keeps the
  // header and the tree free and gives tab and panel one top edge (R-WP-33). What this cannot say is the
  // *where*: that the panel lies over the right edge while the chat stays in view beside it is a browser
  // measurement (jsdom lays out nothing) — here it is the shared row and the absence of a portal
  const row = panel.closest('.chat-workspace')
  expect(row, 'the drawer renders in the row of the chat').not.toBeNull()
  expect(row!.contains(chat), 'the chat is beside the panel, not behind it').toBe(true)
  // and nothing locks the page the chat stands on: the drawer is deliberately no modal
  expect(document.body.style.overflow, 'no scroll lock of a mask').not.toBe('hidden')
})

// UC-WP-60
test('theWideTabRidesTheEdgeOfTheWidePanel', async () => {
  stubFactory()
  await openProjectChat()
  await openFileInTree('app.tsx (12 B)')
  await screen.findByTestId('files-viewer')
  expect(screen.getByTestId('files-panel'), 'a read file is what makes the panel wide').toHaveAttribute(
    'data-wide',
    'true',
  )

  // INC-5B-D1: the width stays the area's own state — the drawer is asked for the wide measure once and the
  // sheet clamps it back while no file is open. The tab hangs outside the drawer, so it reads the wide state
  // through the row it shares with the panel: position, no longer descent (`+` was the old mechanism)
  expect(declarations('.chat-workspace .files-drawer .files-drawer-wrapper')).toContain(
    'max-width: var(--files-width)',
  )
  expect(declarations(".chat-workspace:has(.files-panel[data-wide='true']) .files-drawer-wrapper")).toContain(
    'max-width: var(--files-wide)',
  )
  expect(declarations(".chat-workspace:has(.files-panel[data-wide='true']) .files-tab")).toContain(
    'right: var(--files-wide)',
  )
  // and the area is no column of that row any more: a flex measure here would be the old slide returning
  expect(declarations('.files-panel'), 'the panel is an overlay, not a column').not.toMatch(/flex: 0|flex-basis/)
})

// UC-WP-60
test('closingThePanelForgetsThePositionInIt', async () => {
  const factory = stubFactory()
  await openProjectChat()
  await openFileInTree('app.tsx (12 B)')
  await screen.findByTestId('files-viewer')

  fireEvent.click(screen.getByTestId('files-open'))
  await waitFor(() => expect(screen.queryByTestId('files-panel')).toBeNull())
  expect(screen.getByTestId('files-open')).toHaveAttribute('aria-expanded', 'false')

  // the next open starts at the cards: the root is read again, nothing is pre-opened, no file is shown
  const asked = factory.filesCalls.length
  fireEvent.click(screen.getByTestId('files-open'))
  await screen.findByTestId('files-tree')
  expect(factory.filesCalls.slice(asked)).toEqual([`${FILES_PATH}?root=checkout&path=`])
  expect(screen.getByTestId('files-root-checkout')).toHaveAttribute('aria-pressed', 'true')
  expect(screen.queryByRole('button', { name: 'main/' })).toBeNull()
  expect(screen.queryByTestId('files-viewer')).toBeNull()
  expect(screen.getByRole('button', { name: 'src/' })).toHaveAttribute('aria-expanded', 'false')
})
