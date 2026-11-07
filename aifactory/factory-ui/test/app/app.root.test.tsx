import { afterEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, type RenderResult } from '@testing-library/react'
import { Button, ConfigProvider, theme } from 'antd'

import { AppRoot } from '../../src/app/app.root'
import { AppThemeToggle } from '../../src/app/app.theme.toggle'
import { content } from '../../src/shared/content'
import { factoryTheme } from '../../src/shared/theme'

afterEach(() => {
  cleanup()
  delete document.documentElement.dataset.theme
  // a chosen mode left here would decide every following start of this file, whatever the query answers
  window.localStorage.clear()
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
 * UC-WP-71 GIVEN 3 (one colour mode, one table): the frame names the mode on the document — that attribute is
 * the only thing the dark table of `index.css` hangs on, so without it a dark basis would be handed light
 * tokens. The start value is the answer of the operating system, and what this browser once chose beats it:
 * the frame reads what the browser left behind (UC-WP-71's second point) and writes nothing before a hand has
 * touched the switch. That hand is part of the case: the click is the one place the frame writes, so a choice
 * that forgot the browser would be red here rather than at the browser.
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
  expect(
    window.localStorage.getItem('aifactory.theme'),
    'a start value is no choice, so nothing of it is kept — the next start asks the system again',
  ).toBeNull()
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
  dark.unmount()

  // the reload of a browser that switched once: the choice is read before anything is asked again, and it
  // wins over the operating system, which is still answering light here
  window.localStorage.setItem('aifactory.theme', 'dark')
  answerTheDarkQuery(false)
  const chosen = render(
    <AppRoot>
      <TokenProbe />
    </AppRoot>,
  )
  expect(document.documentElement.dataset.theme, 'a chosen dark outvotes the operating system').toBe('dark')
  expect(accentOf(chosen), 'and the basis is handed the chosen answer').toBe(accentUnder(true))
  chosen.unmount()
  window.localStorage.clear()

  // the hand on the switch itself: what the frame hands down is what the browser keeps, and the document
  // follows in the same turn — without this click `writeStoredMode` could fall out of the choice and stay
  // green, because nothing else in this file lets the frame write
  answerTheDarkQuery(false)
  render(
    <AppRoot>
      <AppThemeToggle />
    </AppRoot>,
  )
  expect(window.localStorage.getItem('aifactory.theme'), 'a screen nobody touched keeps nothing').toBeNull()
  fireEvent.click(screen.getByRole('switch', { name: content.theme.toggle('light') }))
  expect(window.localStorage.getItem('aifactory.theme'), 'the switch hands its choice to the browser').toBe(
    'dark',
  )
  expect(document.documentElement.dataset.theme, 'and the document names it at once').toBe('dark')
  window.localStorage.clear()
})
