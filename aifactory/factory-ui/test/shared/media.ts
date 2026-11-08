import { vi } from 'vitest'

/**
 * A `MediaQueryList` jsdom does not give us: the property is there, its value is not, and the basis reads
 * `matches` and registers a listener. (AGENTS-DEV: `matchMedia` is load-bearing under jsdom.)
 */
export function mediaAnswer(query: string, matches: boolean): MediaQueryList {
  return {
    matches,
    media: query,
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    dispatchEvent: () => false,
  } as MediaQueryList
}

/**
 * Answer every media question of the run from one reading of its query. Restored by `vi.restoreAllMocks()`
 * of the test's own `afterEach`.
 */
export function answerMediaQueries(answers: (query: string) => boolean): void {
  vi.spyOn(window, 'matchMedia').mockImplementation((query: string) =>
    mediaAnswer(String(query), answers(String(query))),
  )
}

/**
 * The narrow answer for a width question only — a blanket `true` would answer the colour question of the
 * dark mode too, and the theme would fold with the frame.
 */
export function belowTheBreakpoint(): void {
  answerMediaQueries(query => query.includes('max-width'))
}
