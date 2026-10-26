import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'

import { ConfigPage } from '../../src/config/config.page'
import type { Settings } from '../../src/config/config.client'

const KEY = 'sk-file-secret-0123456789'

beforeEach(() => {
  cleanup()
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

type Script = { GET: unknown; PUT: unknown }
type Call = { method: string; path: string; body?: string }

/**
 * Mutable `fetch` stand-in: one answer per method, every call recorded with its body. Honest limit,
 * same as the Projects tests — the answers are stubbed, so this view cannot go red from a backend
 * mutation; the wire contract itself is proven by `SettingsControllerTest`.
 */
function stubServer(initial: Partial<Script>): { script: Script; calls: Call[] } {
  const server: { script: Script; calls: Call[] } = {
    script: { GET: undefined, PUT: undefined, ...initial },
    calls: [],
  }
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const method = (init?.method ?? 'GET').toUpperCase()
      server.calls.push({ method, path: String(input), body: init?.body as string | undefined })
      const answer = server.script[method as keyof Script]
      if (answer instanceof Error) {
        return response(400, { status: 400, error: 'Bad Request', message: answer.message })
      }
      if (answer === undefined) {
        return response(404, { status: 404, error: 'Not Found', message: 'nope' })
      }
      return response(200, answer)
    }),
  )
  return server
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

/**
 * The value as it sits at the documented field — the field is the contract, not the markup. An
 * editable row shows what its control holds, a status row shows its text.
 */
async function valueOf(label: string): Promise<string> {
  const field = await screen.findByText(label)
  const value = field.parentElement?.querySelector('dd')?.querySelector('input, select')
  if (value) {
    return (value as HTMLInputElement).value
  }
  return field.parentElement?.querySelector('dd')?.textContent ?? ''
}

/** What really sits in the form — text content cannot see the value of an input. */
function controlValues(container: HTMLElement): string[] {
  return Array.from(container.querySelectorAll('input, select')).map(el => (el as HTMLInputElement).value)
}

// UC-WP-23
test('settingsShowsMaskedConfig', async () => {
  const server = stubServer({ GET: settingsAnswer() })
  const { container } = render(<ConfigPage />)

  expect(await valueOf('Provider')).toBe('OPEN_AI')
  expect(await valueOf('Modell')).toBe('gpt-demo')
  expect(await valueOf('URL')).toBe('https://api.example.com/v1')
  expect(await valueOf('Token-Fenster')).toBe('64000')
  expect(await valueOf('Timeout')).toBe('45')
  expect(await valueOf('Think senden')).toBe('nein')
  expect(screen.getByText('gesetzt')).toBeInTheDocument()
  expect(screen.getByText('Quelle: Datei')).toBeInTheDocument()

  expect(screen.queryByText(/sk-/)).toBeNull()
  expect(container.textContent).not.toContain('sk-')
  expect(container.textContent).not.toContain(KEY)
  expect(controlValues(container).some(value => value.includes('sk-'))).toBe(false)
  // opening the settings reads the config, it never writes it
  expect(server.calls).toEqual([{ method: 'GET', path: '/api/settings' }])

  // the source is named as what it is (R-WP-16 "Woher ein Wert kommt, zeigt der View")
  cleanup()
  stubServer({ GET: settingsAnswer({ keySource: 'ENV', model: 'env-model' }) })
  render(<ConfigPage />)

  expect(await valueOf('Modell')).toBe('env-model')
  expect(screen.getByText('Quelle: Umgebungsvariable')).toBeInTheDocument()
})

// UC-WP-23
test('showsKeyStatusOfAnUnsetKey', async () => {
  stubServer({ GET: settingsAnswer({ keySet: false, keySource: 'NONE', model: null, url: null }) })
  const { container } = render(<ConfigPage />)

  // an unknown value is an empty field, never the word "null" — and empty means "unchanged"
  expect(await valueOf('Modell')).toBe('')
  expect(container.textContent).not.toContain('null')
  expect(screen.getByText('nicht gesetzt')).toBeInTheDocument()
  expect(screen.getByText('Quelle: keine')).toBeInTheDocument()
})

// UC-WP-24
test('settingsOverrideNeverWritesSharedStore', async () => {
  const server = stubServer({
    GET: settingsAnswer({ keySet: false, keySource: 'NONE', url: null }),
    PUT: settingsAnswer({ model: 'new-model', keySet: true, keySource: 'FILE' }),
  })
  const { container } = render(<ConfigPage />)
  await screen.findByDisplayValue('gpt-demo')

  fireEvent.change(screen.getByLabelText('Modell'), { target: { value: 'new-model' } })
  fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

  const put = server.calls.find(call => call.method === 'PUT')
  expect(put?.path).toBe('/api/settings')
  const sent = JSON.parse(put?.body ?? '{}') as Record<string, unknown>
  expect(sent.model).toBe('new-model')
  // R-WP-16 "leeres Feld = unverändert": an empty field is absent from the body, so its key is
  // never even named — an empty key field therefore cannot delete a stored key
  expect(Object.keys(sent)).not.toContain('url')
  expect(Object.keys(sent)).not.toContain('apiKey')

  // the answer of the server is the stored state: the key row says "gesetzt" without ever holding one
  expect(await screen.findByText('gesetzt')).toBeInTheDocument()
  expect(screen.getByText('Quelle: Datei')).toBeInTheDocument()
  expect(screen.getByLabelText('Key')).toHaveValue('')
  expect(controlValues(container).some(value => value.includes('sk-'))).toBe(false)

  // a rejected save names the field and keeps what was typed (nothing of the answer is a value)
  server.script.PUT = new Error('provider must name one of the supported providers')
  fireEvent.change(screen.getByLabelText('Modell'), { target: { value: 'kept-model' } })
  fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

  expect(await screen.findByRole('alert')).toHaveTextContent('provider')
  expect(screen.getByLabelText('Modell')).toHaveValue('kept-model')
})

test('showsErrorMessageWhenSettingsCannotLoad', async () => {
  stubServer({ GET: new Error('could not read /tmp/home/config.properties') })
  render(<ConfigPage />)

  expect(await screen.findByRole('alert')).toHaveTextContent('config.properties')
})
