import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'

import { SettingsView } from './SettingsView'
import type { Settings } from '../api/settings'

const KEY = 'sk-file-secret-0123456789'

beforeEach(() => {
  cleanup()
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

type Call = { method: string; path: string }

/**
 * `fetch` stand-in for a read-only view: one answer, every call recorded. Honest limit, same as the
 * Projects tests — the answer is stubbed, so this view cannot go red from a backend mutation; the wire
 * contract itself is proven by `SettingsControllerTest`.
 */
function stubServer(answer: unknown | Error): { calls: Call[] } {
  const calls: Call[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const method = (init?.method ?? 'GET').toUpperCase()
      calls.push({ method, path: String(input) })
      if (answer instanceof Error) {
        return response(400, { status: 400, error: 'Bad Request', message: answer.message })
      }
      return response(200, answer)
    }),
  )
  return { calls }
}

function response(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

/**
 * The masked answer of the factory. It carries an `apiKey` on purpose: if a server ever leaked one,
 * the view still must not print a character of it (R-WP-16 "kein Zeichen nach draußen").
 */
function settingsAnswer(overrides: Partial<Settings> = {}): Record<string, unknown> {
  return {
    provider: 'OPEN_AI',
    model: 'gpt-demo',
    url: 'https://api.example.com/v1',
    tokenWindow: 64000,
    timeoutSeconds: 45,
    sendThinking: false,
    keySet: true,
    keySource: 'FILE',
    apiKey: KEY,
    ...overrides,
  }
}

/** The value as it sits at the documented field — the field is the contract, not the markup. */
async function valueOf(label: string): Promise<string> {
  const field = await screen.findByText(label)
  return field.parentElement?.querySelector('dd')?.textContent ?? ''
}

// UC-WP-23
test('settingsShowsMaskedConfig', async () => {
  const server = stubServer(settingsAnswer())
  const { container } = render(<SettingsView />)

  expect(await valueOf('Provider')).toBe('OPEN_AI')
  expect(await valueOf('Modell')).toBe('gpt-demo')
  expect(await valueOf('URL')).toBe('https://api.example.com/v1')
  expect(await valueOf('Token-Fenster')).toBe('64000')
  expect(await valueOf('Timeout')).toBe('45 s')
  expect(await valueOf('Think senden')).toBe('nein')
  expect(screen.getByText('gesetzt')).toBeInTheDocument()
  expect(screen.getByText('Quelle: Datei')).toBeInTheDocument()

  expect(screen.queryByText(/sk-/)).toBeNull()
  expect(container.textContent).not.toContain('sk-')
  expect(container.textContent).not.toContain(KEY)
  // a read-only view: the config is read, never written
  expect(server.calls).toEqual([{ method: 'GET', path: '/api/settings' }])

  // the source is named as what it is (R-WP-16 "Woher ein Wert kommt, zeigt der View")
  cleanup()
  stubServer(settingsAnswer({ keySource: 'ENV', model: 'env-model' }))
  render(<SettingsView />)

  expect(await valueOf('Modell')).toBe('env-model')
  expect(screen.getByText('Quelle: Umgebungsvariable')).toBeInTheDocument()
})

// UC-WP-23
test('showsKeyStatusOfAnUnsetKey', async () => {
  stubServer(settingsAnswer({ keySet: false, keySource: 'NONE', model: null, url: null }))
  render(<SettingsView />)

  expect(await valueOf('Modell')).toBe('—')
  expect(screen.getByText('nicht gesetzt')).toBeInTheDocument()
  expect(screen.getByText('Quelle: keine')).toBeInTheDocument()
})

test('showsErrorMessageWhenSettingsCannotLoad', async () => {
  stubServer(new Error('could not read /tmp/home/config.properties'))
  render(<SettingsView />)

  expect(await screen.findByRole('alert')).toHaveTextContent('config.properties')
})
