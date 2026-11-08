import { createContext, useContext } from 'react'

import { prefersDark } from './prefers.dark'

/**
 * The colour mode this browser shows (R-WP-39, UC-WP-71): a **choice**, kept in the browser, that starts
 * from the operating system's answer. The choice outlives a reload; while nothing is chosen the operating
 * system is asked again — "nothing chosen" is a real state and not a missing value, exactly like the last
 * opened project of {@link ./last.project.ts}.
 *
 * One place owns the question, two readers answer from it: `applyMode` names the mode on the document, which
 * is what the two tables of `index.css` and `react-shiki` read, and `app/app.root.tsx` hands the same mode
 * to the design basis. No `prefers-color-scheme` duplicate of the table is needed, because the mode is
 * written before the first paint (`main.tsx`) — one screen, one mode, no flash.
 */
const STORAGE_KEY = 'aifactory.theme'

/** The two modes the app paints; the names are the values `index.css` hangs its tables on. */
export type ThemeMode = 'light' | 'dark'

/** The chosen mode, or `null` — never chosen is a real state, not a missing one. */
export function readStoredMode(): ThemeMode | null {
  const stored = localStorage.getItem(STORAGE_KEY)?.trim()
  return stored === 'dark' || stored === 'light' ? stored : null
}

/** Remember the mode this browser was switched to. Only a choice is ever written, never a start value. */
export function writeStoredMode(mode: ThemeMode): void {
  localStorage.setItem(STORAGE_KEY, mode)
}

/** The mode to start with: the choice where there is one, the answer of the operating system otherwise. */
export function startMode(): ThemeMode {
  return readStoredMode() ?? (prefersDark() ? 'dark' : 'light')
}

/** Name the mode on the document — the one thing both colour tables and the highlighter read. */
export function applyMode(mode: ThemeMode): void {
  document.documentElement.dataset.theme = mode
}

/** The mode of this browser and the one way to change it. */
export interface ThemeModeState {
  mode: ThemeMode
  /** Switches the mode and lets it outlive a reload; the start value never takes this path. */
  choose: (mode: ThemeMode) => void
}

/** Held by `app/app.root.tsx` and by nobody else — no component below the frame keeps a mode of its own. */
export const ThemeModeContext = createContext<ThemeModeState | null>(null)

/** The mode this browser shows. Only the frame hands one, so a hook outside it is a wiring mistake. */
export function useThemeMode(): ThemeModeState {
  const state = useContext(ThemeModeContext)
  if (state === null) {
    throw new Error('useThemeMode() needs the ThemeModeContext of app/app.root.tsx')
  }
  return state
}
