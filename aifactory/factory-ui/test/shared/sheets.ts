import { expect } from 'vitest'

/**
 * The sheets the screen is painted with, as written. jsdom lays out nothing and resolves no custom
 * property, so the contract of a rule like R-WP-29 cannot be read off a computed style — it is read where it
 * is written: the class a row carries has to be the class the sheet paints, and the sheet has to paint with
 * **our** tokens rather than a colour of its own. Which colour a token answers with is the look's and never
 * a test's, so no helper here reads a value out of the table. `test.css` in `vite.config.ts` keeps these two
 * sheets readable as text.
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

/** The base sheet as written — for a table a keyed selector read cannot reach (`:root, [data-theme='light']`). */
export function baseSheet(): string {
  return SHEETS[BASE_SHEET]
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
 * Whether the app's sheet spells a rule for this selector — the question behind "that block is gone". Read as
 * written: a substring hit on `selector` + **one** space + `{`. A rule with two spaces, a break before the
 * brace or a grouped selector (`a, b {`) is therefore not found, and `index.css` is never searched — so a
 * `false` can stay green while the rule exists in another spelling. Anyone who wants that read otherwise
 * changes this helper and reports its callers: `chat.page.test.tsx` (UC-WP-65), `code.test.tsx`,
 * `theme.test.ts`.
 */
export function declares(selector: string): boolean {
  return SHEETS[APP_SHEET].includes(`${selector} {`)
}
