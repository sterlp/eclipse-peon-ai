import { useEffect, useState, type ReactNode } from 'react'
import { ConfigProvider } from 'antd'

import { AppBrowser } from './app.routes'
import { factoryTheme } from '../shared/theme'
import {
  applyMode,
  startMode,
  ThemeModeContext,
  writeStoredMode,
  type ThemeMode,
  type ThemeModeState,
} from '../shared/theme.mode'

/**
 * The one design basis of the app (R-WP-39): the theme of the basis is derived from our tokens and wrapped
 * once around everything, so no page carries a colour of its own and no second sheet competes with it.
 *
 * The frame is also the one holder of the colour mode (UC-WP-71): it starts from the browser's choice or the
 * operating system's answer, names it on the document — which is what the two tables of `index.css` and
 * `react-shiki` read — and hands the same mode to the basis. Nothing below repeats the question, so one
 * screen never shows two dark modes.
 *
 * Its own file because the theme is a component, and `main.tsx` is an entry that mounts and exports
 * nothing — a component living there would be outside every fast-refresh boundary.
 *
 * `children` exists for the proof: this is the only place the theme is handed to the basis, and a wrapper
 * nobody renders cannot be shown to work. The app itself keeps the default.
 */
export function AppRoot({ children = <AppBrowser /> }: { children?: ReactNode }) {
  const [mode, setMode] = useState<ThemeMode>(startMode)

  // the document is named from the mode and nowhere else; `main.tsx` already did it once before the first
  // paint, so this only ever repeats that answer or carries a new one
  useEffect(() => {
    applyMode(mode)
  }, [mode])

  const theme: ThemeModeState = {
    mode,
    choose: (next: ThemeMode): void => {
      // only what the user chose reaches the browser: a start value stays a start value (UC-WP-71)
      writeStoredMode(next)
      setMode(next)
    },
  }

  return (
    <ThemeModeContext.Provider value={theme}>
      <ConfigProvider theme={factoryTheme(mode === 'dark')}>{children}</ConfigProvider>
    </ThemeModeContext.Provider>
  )
}
