import '@testing-library/jest-dom/vitest'

/**
 * The two browser APIs jsdom does not ship and AntD asks for on mount (its resize observer and the media
 * query behind `theme.useToken`, the Sider breakpoint and the responsive Menu). They belong here and
 * nowhere else: a second definition per test file would be a second environment, and the reports of
 * AntD components would then depend on which file ran first.
 *
 * `matchMedia` answers "not dark" — the app has no theme switch, and the dark branch is proven on the
 * token mapping in `theme.test.ts`, not on a simulated OS. `ResizeObserver` never reports: jsdom lays
 * out nothing, so a measured width would be a number no screen has.
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
