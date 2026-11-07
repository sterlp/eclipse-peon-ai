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
 * Every rule the app sheet states for a selector — **all** of them, the media blocks and the nested ones
 * included, as written. `declarations` answers for the FIRST block it finds, so a prohibition that has to
 * hold for every way one element is painted (`.chat-line`, then `.chat-line.user`, then whatever a media
 * query adds later) stays green while a second block breaks it; that hole is why this exists. Comments are
 * stripped first, so prose naming a selector cannot answer here, and a selector list is read selector by
 * selector. A matcher naming no rule fails — a guard that reads nothing has proved nothing.
 */
export function allDeclarations(matcher: RegExp): Array<{ selector: string; declarations: string }> {
  const source = withoutComments(SHEETS[APP_SHEET])
  const rules: Array<{ selector: string; declarations: string }> = []
  let from = 0
  for (let i = 0; i < source.length; i++) {
    if (source[i] === ';' || source[i] === '}') {
      from = i + 1
    } else if (source[i] === '{') {
      const body = source.slice(i + 1, closingBraceOf(source, i))
      for (const part of source.slice(from, i).split(',')) {
        const selector = part.trim()
        if (matcher.test(selector)) {
          rules.push({ selector, declarations: ownDeclarations(body) })
        }
      }
      from = i + 1
    }
  }
  expect(rules.length, `the sheet states a rule matching ${matcher}`).toBeGreaterThan(0)
  return rules
}

/** The sheet without its comments: a selector mentioned in prose is not a rule of it. */
function withoutComments(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, ' ')
}

/** The index of the brace closing the one at `open`. */
function closingBraceOf(source: string, open: number): number {
  let depth = 0
  for (let i = open; i < source.length; i++) {
    if (source[i] === '{') {
      depth++
    } else if (source[i] === '}' && --depth === 0) {
      return i
    }
  }
  return source.length
}

/** The declarations one rule states itself — the blocks written inside it removed. */
function ownDeclarations(body: string): string {
  let own = ''
  let from = 0
  for (let i = 0; i < body.length; i++) {
    if (body[i] !== '{') {
      continue
    }
    const started = Math.max(body.lastIndexOf(';', i), body.lastIndexOf('}', i)) + 1
    own += body.slice(from, started)
    from = closingBraceOf(body, i) + 1
    i = from - 1
  }
  return (own + body.slice(from)).trim()
}


/** Whether a sheet names a rule for this selector at all — the question behind "that block is gone". */
export function declares(selector: string): boolean {
  return SHEETS[APP_SHEET].includes(`${selector} {`)
}
