import '@testing-library/jest-dom/vitest'

/**
 * The two browser APIs the design basis asks for that this environment does not answer.
 *
 * `matchMedia` — measured, jsdom 26.1.0: `window.matchMedia` exists as a property and its value is
 * `undefined` (an API the window declares and does not implement), so both the question of
 * `shared/prefers.dark.ts` and a `vi.spyOn(window, 'matchMedia')` in a test are without an answer here.
 * The stand-in answers "not dark", which is also what the pinned environment of a build machine should say:
 * a colour claim must not depend on how the machine running the suite is configured. A test that wants the
 * dark answer overwrites the query for itself (`theThemeQuestionIsTheOperatingSystem`,
 * `appRootSetsTheDocumentTheme`) — it needs `addEventListener`, because the hook keeps the answer current.
 *
 * `ResizeObserver` — the basis measures its surfaces on mount (the responsive Menu, a `Select` popup), and
 * without it 15 of the 16 tests under `test/app` fail with `ReferenceError`, whatever the page does. It
 * never reports: jsdom lays out nothing, so a measured width would be a number no screen has.
 *
 * Both belong here and nowhere else — a definition per test file would be a second environment, and the
 * reports of the basis' components would then depend on which file ran first.
 */
if (typeof window.matchMedia !== 'function') {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string): MediaQueryList =>
      ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => undefined,
        removeListener: () => undefined,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
        dispatchEvent: () => false,
      }) as MediaQueryList,
  })
}

if (typeof globalThis.ResizeObserver !== 'function') {
  class NoResizeObserver implements ResizeObserver {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  }
  globalThis.ResizeObserver = NoResizeObserver
}
