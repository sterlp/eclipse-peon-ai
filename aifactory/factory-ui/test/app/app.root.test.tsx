import { afterEach, expect, test, vi } from 'vitest'
import { cleanup, render, type RenderResult } from '@testing-library/react'
import { Button, ConfigProvider, theme } from 'antd'

import { AppRoot } from '../../src/app/app.root'
import { factoryTheme } from '../../src/shared/theme'

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
 * UC-WP-71 GIVEN 3 (one colour mode, one table): the app asks the operating system once and names the answer
 * on the document — that attribute is the only thing the dark table of `index.css` hangs on, so without it a
 * dark basis would be handed light tokens.
 *
 * Compared against our own dark theme rather than against a colour of the sheet: under `darkAlgorithm` the
 * basis derives the accent it hands a surface from the token, so the value written in the table is not the
 * value measured here. What this proves is the choice of mode, never the palette.
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
})
