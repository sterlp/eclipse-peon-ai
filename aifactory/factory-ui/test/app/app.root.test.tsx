import { afterEach, expect, test, vi } from 'vitest'
import { cleanup, render, type RenderResult } from '@testing-library/react'
import { Button, ConfigProvider, theme } from 'antd'

import { AppRoot } from '../../src/app/app.root'
import { factoryTheme } from '../../src/shared/theme'
import { tokenTable, tokenValues } from '../shared/sheets'

afterEach(() => {
  cleanup()
  delete document.documentElement.dataset.theme
  vi.restoreAllMocks()
})

/** A surface of the basis, asked which accent it was handed. */
function TokenProbe() {
  const { token } = theme.useToken()
  return (
    <Button data-testid="token-probe" data-color-primary={token.colorPrimary}>
      probe
    </Button>
  )
}

function accentOf(page: RenderResult): string | null {
  return (
    page.container.querySelector('[data-testid="token-probe"]')?.getAttribute('data-color-primary') ?? null
  )
}

/** The accent a surface is handed under one colour mode of our own theme — the reference to compare to. */
function accentUnder(dark: boolean): string | null {
  const page = render(
    <ConfigProvider theme={factoryTheme(dark)}>
      <TokenProbe />
    </ConfigProvider>,
  )
  const accent = accentOf(page)
  page.unmount()
  return accent
}

/** The answer of the operating system, for as long as one render needs it. */
function answerTheDarkQuery(dark: boolean): void {
  vi.spyOn(window, 'matchMedia').mockImplementation(
    (query: string) =>
      ({
        matches: dark,
        media: query,
        onchange: null,
        addListener: () => undefined,
        removeListener: () => undefined,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
        dispatchEvent: () => false,
      }) as MediaQueryList,
  )
}

/**
 * UC-WP-71 GIVEN 2 (the surfaces come from one basis): jsdom paints no colour, so what is proven here is the
 * wiring — a component of the library, inside the app's own root, reads our token out of the theme. That the
 * token equals our sheet is the other claim, in `theme.test.ts`.
 */
// UC-WP-71
test('appRootWearsOurTokenColours', () => {
  const page = render(
    <AppRoot>
      <TokenProbe />
    </AppRoot>,
  )

  const probe = page.container.querySelector('[data-testid="token-probe"]')
  expect(probe, 'the probe speaks the basis, so its token is the basis answer').toHaveClass('ant-btn')
  expect(probe?.getAttribute('data-color-primary'), 'and the basis was handed the accent of our sheet').toBe(
    tokenValues('--accent')[0],
  )
})

/**
 * UC-WP-71 GIVEN 3 (one colour mode, one table): the app asks the operating system once and names the answer
 * on the document, which is the selector the dark table of `index.css` hangs on. Without it the page would
 * follow the media query while the basis followed an attribute nobody sets — a dark screen with light edges.
 *
 * Measured, and the reason the comparison is against our own dark theme rather than against the literal of
 * the table: under `darkAlgorithm` the basis derives its accent from it (`#58a6ff` answers as `#4e90dc`),
 * while the light mode passes the literal through. That the dark theme is asked for is the claim here; that
 * it is the table of the sheet is `darkModeCarriesTheDarkSheetAndTheDarkAlgorithm`.
 */
// UC-WP-71
test('appRootSetsTheDocumentTheme', () => {
  answerTheDarkQuery(false)
  const light = render(
    <AppRoot>
      <TokenProbe />
    </AppRoot>,
  )
  expect(document.documentElement.dataset.theme, 'a light system names light on the document').toBe('light')
  expect(accentOf(light), 'and the basis is handed the light answer').toBe(accentUnder(false))
  light.unmount()

  answerTheDarkQuery(true)
  const dark = render(
    <AppRoot>
      <TokenProbe />
    </AppRoot>,
  )
  expect(document.documentElement.dataset.theme, 'a dark system names dark on the document').toBe('dark')
  expect(accentOf(dark), 'and the basis is handed the dark answer').toBe(accentUnder(true))
  expect(
    accentUnder(true),
    'the two answers differ, so the comparison above is not a tautology',
  ).not.toBe(accentUnder(false))
  expect(
    tokenTable("[data-theme='dark']")['--accent'],
    'and the dark answer is asked of the dark table, whose accent is not the light one',
  ).not.toBe(tokenValues('--accent')[0])
})
