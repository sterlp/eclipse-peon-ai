import { expect, test } from 'vitest'

import { content } from '../../src/shared/content'

/**
 * The source of the screen (R-WP-27, UC-WP-53): a component holds no copy of its own, so a German UI is
 * caught by two checks — no German word outside the catalog, and a catalog that speaks English itself. A
 * comment is not the screen, so it is stripped before the scan: prose of this project is German by
 * AGENTS.md, the shown text is English.
 *
 * The scan reads the sources as raw text through Vite (`?raw`), so it sees exactly the files the build
 * sees and needs no file-system types in the browser project.
 */
const SOURCES = import.meta.glob('../../src/**/*.{ts,tsx}', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>

/** The catalog is the one file allowed to hold copy — that is the whole point of it. */
const CATALOG = '../../src/shared/content.ts'

/** Words a screen would say in German; English identifiers never collide with them. */
const GERMAN_WORDS = [
  'kein',
  'keine',
  'lädt',
  'schließen',
  'schliessen',
  'entfernen',
  'unverändert',
  'gesetzt',
  'quelle',
  'dateien',
  'projekt',
  'einstellungen',
  'arbeitet',
  'kompaktiert',
  'bereit',
  'unbekannter',
  'fehler',
  'antwort',
  'werkzeug',
  'eingang',
  'binäre',
  'ordner',
  'zurück',
  'senden',
  'speichern',
  'modell',
  'gespräch',
  'öffnen',
  'schreiben',
  'bearbeiten',
  'löschen',
]

const GERMAN_WORD = new RegExp(`\\b(${GERMAN_WORDS.join('|')})\\b`, 'i')

/** A comment is prose, not a label — and prose of this project is German on purpose. */
function withoutComments(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/(^|[^:])\/\/[^\n]*/g, '$1')
}

function isGerman(source: string): boolean {
  return /[ÄÖÜäöüß]/.test(source) || GERMAN_WORD.test(source)
}

/** The path as a reader of the failure would name it. */
function shortName(path: string): string {
  return path.replace('../../src/', '')
}

// UC-WP-53
test('noGermanTextInComponents', () => {
  const files = Object.entries(SOURCES).filter(([path]) => path !== CATALOG)
  expect(files.length, 'the scan has to see the components, not an empty folder').toBeGreaterThan(10)

  const offenders = files
    .filter(([, source]) => isGerman(withoutComments(source)))
    .map(([path]) => shortName(path))

  expect(
    offenders,
    'every text of the screen comes from the catalog, and the catalog is English (R-WP-27)',
  ).toEqual([])
})

/** A leaf is one text of the screen; a function is a text that needs a value from the screen. */
function leafTexts(node: unknown, path: string): [string, string][] {
  if (typeof node === 'string') {
    return [[path, node]]
  }
  if (typeof node === 'function') {
    const text = (node as (...args: unknown[]) => string)(
      ...Array.from({ length: node.length }, () => 1),
    )
    return [[path, text]]
  }
  return Object.entries(node as Record<string, unknown>).flatMap(([key, value]) =>
    leafTexts(value, `${path}.${key}`),
  )
}

test('theCatalogSpeaksEnglishItself', () => {
  const texts = leafTexts(content, 'content')
  expect(texts.length).toBeGreaterThan(30)

  expect(texts.filter(([, text]) => text.trim() === '').map(([path]) => path)).toEqual([])
  expect(
    texts
      .filter(([, text]) => isGerman(text))
      .map(([path, text]) => `${path}: ${text}`),
  ).toEqual([])
})
