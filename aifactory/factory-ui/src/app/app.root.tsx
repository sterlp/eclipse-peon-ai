import { useEffect, type ReactNode } from 'react'
import { ConfigProvider } from 'antd'

import { AppBrowser } from './app.routes'
import { factoryTheme } from '../shared/theme'
import { usePrefersDark } from '../shared/prefers.dark'

/**
 * The one design basis of the app (R-WP-39): the theme of the basis is derived from our tokens and wrapped
 * once around everything, so no page carries a colour of its own and no second sheet competes with it.
 *
 * The same answer also names the colour mode on the document, which is what makes the dark table of
 * `index.css` the one table: the sheet's `data-theme` rule paints the page, the theme reads that same rule
 * (N-D1). Without this line the browser would follow its media query while the basis followed the attribute
 * nobody sets — one screen, two dark modes.
 *
 * Its own file because the theme is a component, and `main.tsx` is an entry that mounts and exports
 * nothing — a component living there would be outside every fast-refresh boundary.
 *
 * `children` exists for the proof: this is the only place the theme is handed to the basis, and a wrapper
 * nobody renders cannot be shown to work. The app itself keeps the default.
 */
export function AppRoot({ children = <AppBrowser /> }: { children?: ReactNode }) {
  const dark = usePrefersDark()

  useEffect(() => {
    document.documentElement.dataset.theme = dark ? 'dark' : 'light'
  }, [dark])

  return <ConfigProvider theme={factoryTheme(dark)}>{children}</ConfigProvider>
}
