import '@testing-library/jest-dom/vitest'

/**
 * The two browser APIs the design basis asks for that this environment does not answer.
 *
 * `matchMedia` — measured, jsdom 26.1.0: `window.matchMedia` exists as a property and its value is
 * `undefined` (an API the window declares and does not implement), so both the question of
 * `shared/prefers.dark.ts` and a `vi.spyOn(window, 'matchMedia')` in a test are without an answer here.
 * The stand-in answers "not dark", which is also what the pinned environment of a build machine should say:
 * a colour claim must not depend on how the machine running the suite is configured. A test that wants the
 * dark answer overwrites the query for itself (`theOperatingSystemAnswerIsTheStartValue`,
 * `appRootSetsTheDocumentTheme`) — the shape keeps `addEventListener`, because the responsive parts of the
 * basis register a listener on every query they read.
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

/**
 * AntD's styles stay out of the document — measured, not assumed.
 *
 * The basis writes every component style as `style[data-css-hash]` into `document.head`. jsdom pays for
 * every accessibility query with a cascade over every rule of every sheet in the document: with the Drawer
 * mounted the document holds 983 rules instead of 891, and the first `getAllByRole` of a file goes from
 * 49 ms to 494 ms — paid again after each DOM change, so one file of this suite ran 7 s at HEAD and 48 s
 * here. No test of this app reads a computed style (jsdom answers `canvastext` for colour and nothing for
 * layout); the mechanics tests read our own sheet as text. So the styles are moved to a detached element
 * the moment they arrive: the components render and behave unchanged, the cascade has nothing to do.
 */
const detachedStyles = document.createElement('div')
const moveBasisStyle = (node: Node): void => {
  if (node instanceof HTMLStyleElement && node.hasAttribute('data-css-hash')) detachedStyles.append(node)
}
document.head.querySelectorAll('style[data-css-hash]').forEach(moveBasisStyle)
new MutationObserver(records => records.forEach(record => record.addedNodes.forEach(moveBasisStyle))).observe(
  document.head,
  { childList: true },
)
