/** The one question the app asks the operating system about colour (R-WP-39). */
const DARK_QUERY = '(prefers-color-scheme: dark)'

/**
 * The answer now, or no for an environment that has no media query to answer. Asked **once**, before the
 * first paint: from then on the colour mode is the browser's own choice (`shared/theme.mode.ts`), so nothing
 * follows the operating system while the page runs (UC-WP-71).
 */
export function prefersDark(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia(DARK_QUERY).matches
}
