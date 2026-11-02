import { expect } from 'vitest'

/**
 * The sheets the screen is painted with, as written. jsdom lays out nothing and resolves no custom
 * property, so the colour contract of a rule like R-WP-29 cannot be read off a computed style — it is read
 * where it is written: the class a row carries has to be the class the sheet paints, and the token it
 * paints has to be the colour of `chat.html`. `test.css` in `vite.config.ts` keeps these two sheets
 * readable as text.
 */
const SHEETS = import.meta.glob('../../src/*.css', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>

/** The declarations a sheet gives one selector, up to a nested rule — a selector it does not name fails. */
export function declarations(selector: string): string {
  const source = SHEETS['../../src/styles.css']
  const at = source.indexOf(`${selector} {`)
  expect(at, `${selector} is styled at all`).toBeGreaterThanOrEqual(0)
  const body = source.slice(at + selector.length + 2)
  const end = body.search(/[{}]/)
  return body.slice(0, end === -1 ? body.length : end)
}

/** Whether a sheet names a rule for this selector at all — the question behind "that block is gone". */
export function declares(selector: string): boolean {
  return SHEETS['../../src/styles.css'].includes(`${selector} {`)
}

/** The values a token is given, in the order the theme states them: light first (R-WP-27). */
export function tokenValues(name: string): string[] {
  return [...SHEETS['../../src/index.css'].matchAll(new RegExp(`${name}:\\s*([^;]+);`, 'g'))].map(
    match => match[1].trim(),
  )
}
