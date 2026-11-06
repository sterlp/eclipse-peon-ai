import { afterEach, expect, test, vi } from 'vitest'
import { renderHook } from '@testing-library/react'
import { theme } from 'antd'

import { factoryTheme } from '../../src/shared/theme'
import { usePrefersDark } from '../../src/shared/prefers.dark'
import { tokenValues } from './sheets'

afterEach(() => {
  vi.restoreAllMocks()
})

/**
 * The sources as written. A surface is either the basis' own or it is not — that question is answered by
 * what the files import, not by what the screen happens to look like, and jsdom shows no colour anyway.
 */
const SOURCES = import.meta.glob('../../src/**/*.{ts,tsx}', {
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

// UC-WP-71
test('darkModeCarriesTheDarkSheetAndTheDarkAlgorithm', () => {
  expect(factoryTheme(false).algorithm, 'light follows the sheet').toBe(theme.defaultAlgorithm)
  expect(factoryTheme(true).algorithm, 'the basis switches its whole algorithm, not one colour').toBe(
    theme.darkAlgorithm,
  )

  const dark = factoryTheme(true).token ?? {}
  expect(dark.colorPrimary, 'the dark sheet names its own accent').toBe(tokenValues('--accent').at(-1))
  expect(dark.colorError, 'and its own red, not the light one').toBe(tokenValues('--problem').at(-1))
  expect(dark.colorError).not.toBe(tokenValues('--problem')[0])
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

  // the same shape the polyfill in `setup.ts` answers with — only `matches` is the dark answer
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

  // positively: the surfaces are built with the basis, so the absence below would be a gap and not a rule
  const builtWithBasis = files.filter(([, code]) => /from '(antd|@ant-design\/icons)'/.test(code))
  expect(
    builtWithBasis.length,
    'buttons, cards, fields, choices and icons come from the basis',
  ).toBeGreaterThanOrEqual(6)
  expect(builtWithBasis.map(([path]) => path).filter((path) => path.includes('/chat/')).length).toBe(0)

  // the other side of one basis: no second library, no bridge of copied variables, no icon font
  const offenders = files
    .filter(([, code]) => /from 'react-bootstrap|from 'bootstrap|bi bi-|--bs-/.test(code))
    .map(([path]) => path)
  expect(offenders, 'no surface is left on the old layer').toEqual([])

  const manifest = JSON.parse(Object.values(MANIFESTS)[0] ?? '{}') as {
    dependencies?: Record<string, string>
  }
  const named = Object.keys(manifest.dependencies ?? {})
  expect(named, 'the manifest names the basis and its icons').toEqual(
    expect.arrayContaining(['antd', '@ant-design/icons']),
  )
  expect(
    named.filter((name) => name.startsWith('bootstrap') || name === 'react-bootstrap'),
    'and no second design library travels in it',
  ).toEqual([])
})
