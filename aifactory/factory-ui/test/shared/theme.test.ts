import { afterEach, expect, test, vi } from 'vitest'
import { renderHook } from '@testing-library/react'
import { theme } from 'antd'

import { factoryTheme } from '../../src/shared/theme'
import { usePrefersDark } from '../../src/shared/prefers.dark'
import { tokenTable, tokenValues } from './sheets'
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

// UC-WP-71
test('themeMapsTheColoursOfTheBaseSheetOntoTheBasis', () => {
  const tokens = factoryTheme(false).token ?? {}
  // the four colours the chat already speaks, so one screen does not paint a button in another blue
  expect(tokens.colorPrimary, 'the accent of the sheet is the accent of the basis').toBe(
    tokenValues('--accent')[0],
  )
  expect(tokens.colorSuccess, 'an agent answer keeps its green in the basis as well').toBe(
    tokenValues('--agent')[0],
  )
  expect(tokens.colorWarning, 'a question keeps its amber').toBe(tokenValues('--question')[0])
  expect(tokens.colorError, 'a problem keeps its red').toBe(tokenValues('--problem')[0])
  // the sheet states ONE border colour, so no component may answer with a lighter second one
  expect(tokens.colorBorder).toBe(tokenValues('--border')[0])
  expect(tokens.colorBorderSecondary, 'the secondary edge is the same edge').toBe(tokenValues('--border')[0])
  expect(tokens.fontFamilyCode, 'code reads as code in the basis too').toBe(tokenValues('--mono')[0])
  // and nothing is invented: every token the map names is a colour of the sheet, no library grey stands in
  expect(Object.values(tokens), 'a token nobody states is left out, not filled in').not.toContain(undefined)
})

/** The colour map of the basis: which token of the basis answers which question of the sheet. */
const MAPPED: Record<string, string> = {
  colorPrimary: '--accent',
  colorBgBase: '--bg',
  colorTextHeading: '--text-h',
  colorText: '--text',
  colorBorder: '--border',
  colorBorderSecondary: '--border',
  colorSuccess: '--agent',
  colorError: '--problem',
  colorWarning: '--question',
  fontFamily: '--sans',
  fontFamilyCode: '--mono',
}

/** The light table and the table the app puts on the document for a dark operating system. */
const LIGHT_TABLE = tokenTable(":root,\n[data-theme='light']")
const DARK_TABLE = tokenTable("[data-theme='dark']")

/** What the sheet answers for one token in dark mode: its own value, or the light one where dark says nothing. */
function darkAnswer(name: string): string {
  const value = DARK_TABLE[name] ?? LIGHT_TABLE[name]
  expect(value, `${name} is answered by one of the two tables`).toBeDefined()
  return value as string
}

// UC-WP-71
test('theDarkSheetIsOneTable', () => {
  // the dark values are stated twice in CSS because a media query and an attribute selector cannot share one
  // rule — so the two copies have to be the same table, field by field. Compared as a table, a failure names
  // the field that split; a sampled pair would leave the other nine unwatched (plan R3, the Mischbild).
  const fallback = tokenTable(":root:not([data-theme='light'])")

  expect(
    Object.keys(DARK_TABLE).length,
    'the dark table answers every role and surface',
  ).toBeGreaterThan(10)
  expect(
    fallback,
    'the copy for a browser without javascript states the same colours as the table the document carries',
  ).toEqual(DARK_TABLE)
})

// UC-WP-71
test('darkModeCarriesTheDarkSheetAndTheDarkAlgorithm', () => {
  expect(factoryTheme(false).algorithm, 'light follows the sheet').toBe(theme.defaultAlgorithm)
  expect(factoryTheme(true).algorithm, 'the basis switches its whole algorithm, not one colour').toBe(
    theme.darkAlgorithm,
  )

  const dark = factoryTheme(true).token ?? {}
  // every token the map names, not a sample of it: the dark mode of the sheet answers with its own field
  // where it has one, and with the light field where it restates nothing (a token left undefined would make
  // the basis answer in its own default font — a second look inside one page, forbidden by R-WP-39)
  expect(
    dark,
    'the dark theme follows the tables of the sheet, token by token, and leaves none to the library',
  ).toEqual(Object.fromEntries(Object.entries(MAPPED).map(([token, name]) => [token, darkAnswer(name)])))
  // and the dark table really is another table: read light, the same token is a different colour
  expect(dark.colorError).not.toBe(tokenValues('--problem')[0])
  expect(DARK_TABLE['--accent'], 'the accent of the dark sheet is not the accent of the light one').not.toBe(
    tokenValues('--accent')[0],
  )
  expect(
    LIGHT_TABLE['--sans'],
    'the typefaces are stated once, so the dark theme has to carry them over',
  ).toBe(darkAnswer('--sans'))
})

// UC-WP-71
test('theSurfacesTheBasisPaintsOnItsOwnFollowTheFrame', () => {
  const components = factoryTheme(false).components ?? {}
  const card = (components.Card ?? {}) as Record<string, string>
  const layout = (components.Layout ?? {}) as Record<string, string>

  // the card of the config page and of a file card is one language: its border names the block, and the
  // page stays the surface — the look says "kein Hintergrund", which is a token, not a CSS override
  expect(card.colorBgContainer, 'a card fills nothing').toBe('transparent')
  expect(layout.bodyBg, 'the frame behind the content is the sheet background').toBe(tokenValues('--bg')[0])
  expect(layout.headerBg, 'the header is the same surface, not a library band').toBe(tokenValues('--bg')[0])
  expect(layout.headerColor, 'and it speaks in the heading colour').toBe(tokenValues('--text-h')[0])
})

// UC-WP-71
test('theThemeQuestionIsTheOperatingSystem', () => {
  const light = renderHook(() => usePrefersDark())
  expect(light.result.current, 'the environment answers "not dark" here').toBe(false)

  // the answer of the environment is what the app asks; here it is overwritten for this test alone, because
  // dark is decided by `matches` and a test may not depend on how the machine running it is configured
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
  const dark = renderHook(() => usePrefersDark())
  expect(dark.result.current, 'a dark preference reaches the theme').toBe(true)
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
  // R-WP-39 / UC-WP-71 GIVEN 4 draws the line where the basis stops: inside the conversation every line
  // keeps our classes, outside it the basis is the skin of the app — and the field is outside (R-WP-39
  // names `Input`, R-WP-41 its auto-grow), so the ban is the conversation's files, not the chat folder.
  const conversation = ['chat.log.tsx', 'chat.tools.tsx', 'chat.diff.tsx', 'chat.markdown.tsx']
  const insideTheConversation = builtWithBasis
    .map(([path]) => path)
    .filter((path) => conversation.some((file) => path.endsWith(`/${file}`)))
  expect(insideTheConversation, 'no line of the conversation is painted by the basis').toEqual([])
  expect(
    builtWithBasis.map(([path]) => path).filter((path) => path.endsWith('/chat.composer.tsx')),
    'the field beside the conversation is the basis own (R-WP-41)',
  ).toHaveLength(1)

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
    arrives('src/app/app.nav.tsx', `import { Nav } from 'react-bootstrap'\n`),
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
