import { useEffect, useState } from 'react'

/** The one question the app asks the operating system about colour (R-WP-39). */
const DARK_QUERY = '(prefers-color-scheme: dark)'

/** The answer now, or no for an environment that has no media query to answer. */
function prefersDark(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia(DARK_QUERY).matches
}

/**
 * Whether the operating system wants dark, kept current while the page runs. The app has no theme switch
 * of its own, so this is also the only reader of that preference: the CSS tokens of `index.css` read the
 * same media query, and one question must not be asked in two ways (R-WP-39).
 */
export function usePrefersDark(): boolean {
  const [dark, setDark] = useState<boolean>(prefersDark)

  useEffect(() => {
    if (typeof window.matchMedia !== 'function') {
      return undefined
    }
    const media = window.matchMedia(DARK_QUERY)
    const onChange = (event: MediaQueryListEvent): void => setDark(event.matches)
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [])

  return dark
}
