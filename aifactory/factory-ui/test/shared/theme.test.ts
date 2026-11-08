import { afterEach, expect, test, vi } from 'vitest'

import { prefersDark } from '../../src/shared/prefers.dark'
import { declarations, declares } from './sheets'
import {
  isDesignLibrary,
  manifestPackageNames,
  secondDesignLibraryOffenders,
} from './design.basis'

afterEach(() => {
  vi.restoreAllMocks()
})

/**
 * The sources as written, sheets included. A surface is either the basis' own or it is not — that question is
 * answered by what the files import, not by what the screen happens to look like, and jsdom shows no colour
 * anyway. The `.css` files belong to the scan because a second library arrives as a stylesheet.
 */
const SOURCES = import.meta.glob('../../src/**/*.{ts,tsx,css}', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>

const MANIFESTS = import.meta.glob('../../package.json', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>

/**
 * The head row of the frame (R-WP-43): the header lays its parts out in one line, the menu takes what is
 * left and shrinks instead of stretching the band, and no second way to the settings page stands beside the
 * menu's entry. jsdom lays out nothing, so this reads the sheet — the look itself is accepted in a browser.
 */
// R-WP-43
// UC-WP-76
test('theHeaderRowHoldsTheMenuAndNoSecondSettingsEntry', () => {
  const header = declarations('.app-header')
  expect(header).toMatch(/display:\s*flex/)
  expect(header).toMatch(/align-items:\s*center/)
  const top = declarations('.app-top')
  expect(top).toMatch(/flex:\s*1/)
  expect(top, 'the menu shrinks instead of stretching the header').toMatch(/min-width:\s*0/)
  // and no second way to the settings page is left beside the menu's entry (R-WP-43)
  expect(declares('.app-settings'), 'the settings button of the header is gone').toBe(false)
})

/**
 * UC-WP-71 GIVEN 2 (the start value is the operating system's preference): one question, asked of the
 * browser and of nothing else. The answer is overwritten for this test alone, because dark is decided by
 * `matches` and a test may not depend on how the machine running it is configured.
 */
// UC-WP-71
test('theOperatingSystemAnswerIsTheStartValue', () => {
  expect(prefersDark(), 'the environment answers "not dark" here').toBe(false)

  vi.spyOn(window, 'matchMedia').mockImplementation(
    (query: string) =>
      ({
        matches: true,
        media: query,
        onchange: null,
        addListener: () => undefined,
        removeListener: () => undefined,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
        dispatchEvent: () => false,
      }) as MediaQueryList,
  )
  expect(prefersDark(), 'a dark preference reaches the theme').toBe(true)
})

/**
 * UC-WP-71 GIVEN 1 (the app is one design basis): the claim is about what the sources import, because a
 * second library left in the manifest is invisible on the screen and visible in every bundle after it.
 */
// UC-WP-71
test('theBasisIsTheOnlyDesignLibraryOfTheApp', () => {
  const files = Object.entries(SOURCES)
  expect(files.length, 'the scan reads the sources instead of nothing').toBeGreaterThan(30)
  const sheets = files.filter(([path]) => path.endsWith('.css'))
  expect(
    sheets.length,
    'and reads the sheets too, since a second basis arrives as CSS',
  ).toBeGreaterThan(0)
  for (const [path, code] of sheets) {
    expect(code, `${path} arrives as text, not as the empty stub of a switched-off css loader`).not.toHaveLength(
      0,
    )
  }

  // positively: the surfaces are built with the basis, so the absence below would be a gap and not a rule
  const builtWithBasis = files.filter(([, code]) => /from '(antd|@ant-design\/icons)'/.test(code))
  expect(
    builtWithBasis.length,
    'buttons, cards, fields, choices and icons come from the basis',
  ).toBeGreaterThanOrEqual(6)
  // R-WP-39 / UC-WP-71 GIVEN 4 draws the line where the basis stops: the conversation keeps our classes.
  // So the ban is the whole chat folder and the exception is the surfaces **the docs** hand to the basis —
  // R-WP-39 names `Input`, R-WP-41 its auto-grow, R-WP-40 the timeline the trace is drawn with, R-WP-43 the
  // actions ("echte AntD-Elemente … auch an den Kacheln", ui.md), R-WP-45 the working spin ("AntD `Spin`
  // mit `LoadingOutlined` … keine eigene CSS-Rotation") and R-WP-35 the way back to the end ("ein schwebender
  // Knopf … AntD `FloatButton`, wie `BackTop`, nur umgekehrt", chat.md). A new file of the conversation
  // arrives inside the ban, which is exactly GIVEN 1 — the same mistake a second time makes the suite red, not
  // the file list. A voice of the conversation is in none of them and stays ours (below).
  const basisOwnSurface = [
    'chat.composer.tsx',
    'chat.scroll.bottom.tsx',
    'chat.status.tsx',
    'chat.tiles.tsx',
    'chat.timeline.tsx',
  ]
  const basisInsideTheChat = builtWithBasis
    .map(([path]) => path)
    .filter((path) => path.includes('/chat/') && !basisOwnSurface.some((name) => path.endsWith(`/${name}`)))
  expect(basisInsideTheChat, 'no line of the conversation is painted by the basis').toEqual([])
  expect(
    builtWithBasis.map(([path]) => path).filter((path) => path.endsWith('/chat.composer.tsx')),
    'the field beside the conversation is the basis own (R-WP-41)',
  ).toHaveLength(1)
  // the exception stops at the trace: the two files that draw a VOICE of the conversation stay ours, so the
  // ban keeps its meaning after R-WP-40 handed the trace to the basis
  const voices = files
    .filter(([path]) => /\/chat\.(log|markdown)\.tsx$/.test(path))
    .map(([path]) => path)
  expect(voices, 'the scan sees the two files that draw a voice').toHaveLength(2)
  expect(
    voices.filter(path => builtWithBasis.some(([built]) => built === path)),
    'a voice of the conversation is nobody but ours: neither the log nor its Markdown builds with the basis',
  ).toEqual([])

  // the other side of one basis: no second library, no bridge of copied variables, no icon font — in the
  // forms they really take, which is what `design.basis.ts` states and the test below proves
  expect(secondDesignLibraryOffenders(SOURCES), 'no surface is left on the old layer').toEqual([])

  const named = manifestPackageNames(Object.values(MANIFESTS)[0] ?? '{}')
  expect(named, 'the manifest names the basis and its icons').toEqual(
    expect.arrayContaining(['antd', '@ant-design/icons']),
  )
  expect(
    named.filter(isDesignLibrary),
    'and no second design library travels in it, in either section',
  ).toEqual([])
})

/**
 * UC-WP-71 GIVEN 1: a guard nobody can trigger is not a rule. Each form a second library arrives in is
 * offered as a synthetic source, so the scan above is proven to see it — the CSS import of the entry is the
 * form this app actually had to remove, and the one a `from '…'` regex misses.
 */
// UC-WP-71
test('theGuardSeesTheCssImportAsWellAsTheComponent', () => {
  const arrives = (path: string, code: string) => secondDesignLibraryOffenders({ [path]: code })

  expect(
    arrives('src/main.tsx', `import 'bootstrap/dist/css/bootstrap.min.css'\n`),
    'a stylesheet import without a `from` is the dangerous form',
  ).not.toEqual([])
  expect(
    arrives('src/app/app.sider.tsx', `import { Nav } from 'react-bootstrap'\n`),
    'the component library is seen',
  ).not.toEqual([])
  expect(
    arrives('src/app/app.shell.tsx', `import 'bootstrap-icons/font/bootstrap-icons.css'\n`),
    'the icon font sheet is seen',
  ).not.toEqual([])
  expect(
    arrives('src/styles.css', `@import 'bootstrap/dist/css/bootstrap.css';\n`),
    'a sheet pulling a sheet in is seen',
  ).not.toEqual([])
  expect(
    arrives('src/project/project.files.tsx', `<i className="bi bi-folder2-open" />\n`),
    'an icon-font class in markup is seen',
  ).not.toEqual([])
  expect(
    arrives('src/index.css', `:root {\n  --bs-border-color: #dee2e6;\n}\n`),
    'a bridge of copied variables is seen',
  ).not.toEqual([])

  // the guard is not satisfiable by emptying the scan: the basis itself, and our own sheet, are no findings
  expect(
    arrives(
      'src/app/app.shell.tsx',
      `import { Button } from 'antd'\nimport { FolderOutlined } from '@ant-design/icons'\n`,
    ),
    'the basis is not a finding',
  ).toEqual([])
  expect(arrives('src/index.css', `:root {\n  --border: #d0d7de;\n}\n`), 'our tokens are no finding').toEqual(
    [],
  )
})
