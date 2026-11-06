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

/** The sheet a selector is read from — the app's own layer and the base sheet with its tokens. */
const APP_SHEET = '../../src/styles.css'
const BASE_SHEET = '../../src/index.css'

/** The declarations one sheet gives one selector, up to a nested rule. */
function declarationsIn(sheet: string, selector: string): string {
  const source = SHEETS[sheet]
  const at = source.indexOf(`${selector} {`)
  expect(at, `${selector} is styled at all`).toBeGreaterThanOrEqual(0)
  const body = source.slice(at + selector.length + 2)
  const end = body.search(/[{}]/)
  return body.slice(0, end === -1 ? body.length : end)
}

/** The app's own sheet as written — for a contract a flat selector cannot reach, such as a nested rule. */
export function appSheet(): string {
  return SHEETS[APP_SHEET]
}

/** One rule block of one sheet, nested rules included, from the selector to its closing brace. */
function blockIn(sheet: string, selector: string): string {
  const source = SHEETS[sheet]
  const at = source.indexOf(`${selector} {`)
  expect(at, `${selector} is stated at all`).toBeGreaterThanOrEqual(0)
  let depth = 0
  for (let i = at + selector.length + 1; i < source.length; i++) {
    if (source[i] === '{') {
      depth++
    } else if (source[i] === '}' && --depth === 0) {
      return source.slice(at, i + 1)
    }
  }
  return source.slice(at)
}

/**
 * One rule block as written, nested rules included. `declarations` stops at the first nested rule, so a
 * measure that lives inside one — the padding of the chat's own field — is read here.
 */
export function block(selector: string): string {
  return blockIn(APP_SHEET, selector)
}

/** The declarations a sheet gives one selector, up to a nested rule — a selector it does not name fails. */
export function declarations(selector: string): string {
  return declarationsIn(APP_SHEET, selector)
}

/** The same for the base sheet, which is where the shell of the app — and its height — is stated. */
export function baseDeclarations(selector: string): string {
  return declarationsIn(BASE_SHEET, selector)
}

/**
 * Every custom property one rule of the base sheet states, as written: the colour table behind one mode.
 * Two rules that answer the same question — the dark table of `data-theme` and its copy inside the media
 * query — are compared field by field this way, so a divergence names the field instead of appearing as a
 * page painted half dark.
 *
 * The selector has to be the rule's own text (`:root,` plus its second selector, since they share one
 * block): a rule is recognised only where the selector is followed by nothing but whitespace and a brace, so
 * a prose mention of a selector in a comment cannot be mistaken for the rule itself.
 */
export function tokenTable(selector: string): Record<string, string> {
  const source = SHEETS[BASE_SHEET]
  let open = -1
  for (let at = source.indexOf(selector); at >= 0; at = source.indexOf(selector, at + 1)) {
    const rest = source.slice(at + selector.length)
    const gap = /^\s*/.exec(rest)?.[0] ?? ''
    if (rest[gap.length] === '{') {
      open = at + selector.length + gap.length
      break
    }
  }
  expect(open, `${selector} is a stated rule of the base sheet`).toBeGreaterThanOrEqual(0)

  let depth = 0
  let end = source.length
  for (let i = open; i < source.length; i++) {
    if (source[i] === '{') {
      depth++
    } else if (source[i] === '}' && --depth === 0) {
      end = i
      break
    }
  }

  const table: Record<string, string> = {}
  for (const match of source.slice(open, end).matchAll(/(--[\w-]+):\s*([^;]+);/g)) {
    table[match[1]] = match[2].trim()
  }
  expect(Object.keys(table).length, `${selector} states a token table`).toBeGreaterThan(0)
  return table
}

/** Whether a sheet names a rule for this selector at all — the question behind "that block is gone". */
export function declares(selector: string): boolean {
  return SHEETS[APP_SHEET].includes(`${selector} {`)
}

/** The values a token is given, in the order the theme states them: light first (R-WP-27). */
export function tokenValues(name: string): string[] {
  return [...SHEETS[BASE_SHEET].matchAll(new RegExp(`${name}:\\s*([^;]+);`, 'g'))].map(
    match => match[1].trim(),
  )
}
