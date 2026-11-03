import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'

import { ConfigPage } from '../../src/config/config.page'
import { content } from '../../src/shared/content'
import type { Settings } from '../../src/config/config.client'

const KEY = 'sk-file-secret-0123456789'

beforeEach(() => {
  cleanup()
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

type Script = { GET: unknown; PUT: unknown; PROVIDERS: unknown; AGENTS: unknown }
type Call = { method: string; path: string; body?: string }

/**
 * The provider list as the core answers it (UC-WP-62): one provider with a URL default, one without a
 * think input. The values are stubs on purpose — which URL and which think values the core really
 * carries is pinned by `SettingsControllerTest`, the page only has to follow what the server says
 * (UC-WP-67: the same answer also builds the agent think field).
 */
const PROVIDER_ANSWER = [
  {
    name: 'OPEN_AI',
    defaultUrl: null,
    thinkSupported: true,
    thinkForm: 'VALUES',
    thinkValues: ['minimal', 'low', 'medium', 'high'],
  },
  {
    name: 'OLLAMA',
    defaultUrl: 'https://ollama.example',
    thinkSupported: true,
    thinkForm: 'TOGGLE',
    thinkValues: [],
  },
  { name: 'GOOGLE_GEMINI', defaultUrl: null, thinkSupported: false, thinkForm: 'NONE', thinkValues: [] },
]

/**
 * Mutable `fetch` stand-in: one answer per endpoint, every call recorded with its body. Honest limit,
 * same as the Projects tests — the answers are stubbed, so this view cannot go red from a backend
 * mutation; the wire contract itself is proven by `SettingsControllerTest`.
 */
function stubServer(initial: Partial<Script>): {
  script: Script
  calls: Call[]
} {
  const server: { script: Script; calls: Call[] } = {
    script: {
      GET: undefined,
      PUT: undefined,
      PROVIDERS: PROVIDER_ANSWER,
      // the page mounts the agent section too (R-WP-23); its own tests are in config.agents.test.tsx
      AGENTS: [],
      ...initial,
    },
    calls: [],
  }
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const method = (init?.method ?? 'GET').toUpperCase()
      const path = String(input)
      server.calls.push({
        method,
        path,
        body: init?.body as string | undefined,
      })
      const answer = path.endsWith('/providers')
        ? server.script.PROVIDERS
        : path === '/api/settings/agents'
          ? server.script.AGENTS
          : server.script[method as keyof Script]
      if (answer instanceof Error) {
        return response(400, {
          status: 400,
          error: 'Bad Request',
          message: answer.message,
        })
      }
      if (answer === undefined) {
        return response(404, {
          status: 404,
          error: 'Not Found',
          message: 'nope',
        })
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
  return Array.from(container.querySelectorAll('input, select')).map(
    (el) => (el as HTMLInputElement).value,
  )
}

// UC-WP-23
test('settingsShowsMaskedConfig', async () => {
  const server = stubServer({ GET: settingsAnswer() })
  const { container } = render(<ConfigPage />)

  expect(await valueOf(content.config.provider)).toBe('OPEN_AI')
  expect(await valueOf(content.config.model)).toBe('gpt-demo')
  expect(await valueOf(content.config.url)).toBe('https://api.example.com/v1')
  expect(await valueOf(content.config.tokenWindow)).toBe('64000')
  expect(await valueOf(content.config.timeout)).toBe('45')
  expect(await valueOf(content.config.sendThinking)).toBe('no')
  expect(screen.getByText(content.config.keySet)).toBeInTheDocument()
  expect(
    screen.getByText(`${content.config.keySource}: ${content.config.source.FILE}`),
  ).toBeInTheDocument()

  expect(screen.queryByText(/sk-/)).toBeNull()
  expect(container.textContent).not.toContain('sk-')
  expect(container.textContent).not.toContain(KEY)
  expect(controlValues(container).some((value) => value.includes('sk-'))).toBe(false)
  // opening the settings reads the config, the provider defaults and the agent slots, and writes none of
  // them — the third read is Inc 7b's: a slot's state lives in its own file, not in the config
  expect(server.calls.map((call) => call.method)).toEqual(['GET', 'GET', 'GET'])
  expect(server.calls.map((call) => call.path).sort()).toEqual([
    '/api/settings',
    '/api/settings/agents',
    '/api/settings/providers',
  ])

  // the source is named as what it is (R-WP-16 "Woher ein Wert kommt, zeigt der View")
  cleanup()
  stubServer({ GET: settingsAnswer({ keySource: 'ENV', model: 'env-model' }) })
  render(<ConfigPage />)

  expect(await valueOf(content.config.model)).toBe('env-model')
  expect(
    screen.getByText(`${content.config.keySource}: ${content.config.source.ENV}`),
  ).toBeInTheDocument()
})

// UC-WP-23
test('showsKeyStatusOfAnUnsetKey', async () => {
  stubServer({
    GET: settingsAnswer({
      keySet: false,
      keySource: 'NONE',
      model: null,
      url: null,
    }),
  })
  const { container } = render(<ConfigPage />)

  // an unknown value is an empty field, never the word "null" — and empty means "unchanged"
  expect(await valueOf(content.config.model)).toBe('')
  expect(container.textContent).not.toContain('null')
  expect(screen.getByText(content.config.keyMissing)).toBeInTheDocument()
  expect(
    screen.getByText(`${content.config.keySource}: ${content.config.source.NONE}`),
  ).toBeInTheDocument()
})

// UC-WP-24
test('settingsOverrideNeverWritesSharedStore', async () => {
  const server = stubServer({
    GET: settingsAnswer({ keySet: false, keySource: 'NONE', url: null }),
    PUT: settingsAnswer({
      model: 'new-model',
      keySet: true,
      keySource: 'FILE',
    }),
  })
  const { container } = render(<ConfigPage />)
  await screen.findByDisplayValue('gpt-demo')

  fireEvent.change(screen.getByLabelText(content.config.model), {
    target: { value: 'new-model' },
  })
  fireEvent.click(screen.getByRole('button', { name: content.config.save }))

  const put = server.calls.find((call) => call.method === 'PUT')
  expect(put?.path).toBe('/api/settings')
  const sent = JSON.parse(put?.body ?? '{}') as Record<string, unknown>
  expect(sent.model).toBe('new-model')
  // R-WP-16 since Inc 7a: every named field travels, an emptied one as an empty string — that is how a
  // cleared field is deleted from the file (UC-WP-46). The url was empty here, so it says "delete", which
  // the file answers by keeping nothing.
  expect(sent).toHaveProperty('url', '')
  // only the key is never named when empty: its field is write-only, so empty means unchanged and an empty
  // body cannot delete a stored key
  expect(Object.keys(sent)).not.toContain('apiKey')

  // the answer of the server is the stored state: the key row says "gesetzt" without ever holding one
  expect(await screen.findByText(content.config.keySet)).toBeInTheDocument()
  expect(
    screen.getByText(`${content.config.keySource}: ${content.config.source.FILE}`),
  ).toBeInTheDocument()
  expect(screen.getByLabelText(content.config.key)).toHaveValue('')
  expect(controlValues(container).some((value) => value.includes('sk-'))).toBe(false)

  // a rejected save names the field and keeps what was typed (nothing of the answer is a value)
  server.script.PUT = new Error('provider must name one of the supported providers')
  fireEvent.change(screen.getByLabelText(content.config.model), {
    target: { value: 'kept-model' },
  })
  fireEvent.click(screen.getByRole('button', { name: content.config.save }))

  expect(await screen.findByRole('alert')).toHaveTextContent('provider')
  expect(screen.getByLabelText(content.config.model)).toHaveValue('kept-model')
})

// UC-WP-62
test('changingTheProviderUpdatesTheUrl', async () => {
  const server = stubServer({
    GET: settingsAnswer({ url: 'https://old.example' }),
  })
  render(<ConfigPage />)
  expect(await valueOf(content.config.url)).toBe('https://old.example')

  fireEvent.change(screen.getByLabelText(content.config.provider), {
    target: { value: 'OLLAMA' },
  })

  // the default the server named for that provider, at once and without a save in between (R-WP-16)
  expect(screen.getByLabelText(content.config.url)).toHaveValue('https://ollama.example')
  expect(server.calls.filter((call) => call.method === 'PUT')).toEqual([])

  // a provider without a default leaves the field empty — the page invents nothing
  fireEvent.change(screen.getByLabelText(content.config.provider), {
    target: { value: 'GOOGLE_GEMINI' },
  })
  expect(screen.getByLabelText(content.config.url)).toHaveValue('')
})

// UC-WP-62
test('theModelFieldIsClearedOnProviderChange', async () => {
  stubServer({ GET: settingsAnswer({ model: 'gpt-demo' }) })
  render(<ConfigPage />)
  expect(await valueOf(content.config.model)).toBe('gpt-demo')

  fireEvent.change(screen.getByLabelText(content.config.provider), {
    target: { value: 'OLLAMA' },
  })

  // empty, not the old provider's model and not a guessed one: the core knows no model default (Q2)
  expect(screen.getByLabelText(content.config.model)).toHaveValue('')
})

// UC-WP-62
test('unsupportedThinkIsShownAndDisabled', async () => {
  stubServer({ GET: settingsAnswer() })
  render(<ConfigPage />)
  await screen.findByText(content.config.provider)

  // the stored provider can carry think, so the field works and nothing is claimed about it
  expect(screen.getByLabelText(content.config.sendThinking)).toBeEnabled()
  expect(screen.queryByText(content.config.thinkUnsupported)).toBeNull()

  fireEvent.change(screen.getByLabelText(content.config.provider), {
    target: { value: 'GOOGLE_GEMINI' },
  })

  // shown and disabled — not hidden: the row still says what the form is talking about (Q3)
  expect(screen.getByLabelText(content.config.sendThinking)).toBeDisabled()
  expect(screen.getByText(content.config.thinkUnsupported)).toBeInTheDocument()
  // disabled is not cleared: the stored value stands until a save says otherwise (UC-WP-46)
  expect(screen.getByLabelText(content.config.sendThinking)).toHaveValue('no')
})

/**
 * UC-WP-67 over the page: the agent rows take the form of the provider this form names — the slots have no
 * provider of their own, so the section reads the answer the page already has instead of asking twice.
 */
// UC-WP-67
test('agentThinkFollowsTheProviderOfTheForm', async () => {
  const server = stubServer({
    GET: settingsAnswer(),
    AGENTS: [
      {
        id: 'dev',
        name: 'Dev',
        overridden: true,
        model: null,
        url: null,
        think: 'medium',
        temperature: null,
        keySet: false,
      },
    ],
  })
  render(<ConfigPage />)

  // OPEN_AI answers a fixed list, so the agent field is that list and not a text box
  const think = (await screen.findByLabelText(content.agents.thinkDefault)) as HTMLSelectElement
  expect(think.tagName).toBe('SELECT')
  expect(Array.from(think.options).map((option) => option.value)).toEqual([
    '',
    'minimal',
    'low',
    'medium',
    'high',
  ])
  expect(think.value).toBe('medium')

  // the provider chosen on the page decides, at once and without a save in between (as R-WP-16 does for the URL)
  fireEvent.change(screen.getByLabelText(content.config.provider), {
    target: { value: 'GOOGLE_GEMINI' },
  })
  expect(screen.queryByLabelText(content.agents.thinkDefault)).toBeNull()
  expect(screen.getByTestId('agent-dev-think-unsupported')).toBeInTheDocument()

  // one provider answer serves both areas — a second read would be a second opinion on one question
  expect(server.calls.filter((call) => call.path === '/api/settings/providers')).toHaveLength(1)
})


// UC-WP-63
test('removeKeyDeletesItAndRefreshesTheStatus', async () => {
  const server = stubServer({
    GET: settingsAnswer(),
    PUT: settingsAnswer({ keySet: false, keySource: 'NONE' }),
  })
  render(<ConfigPage />)
  await screen.findByText(content.config.keySet)
  expect(
    screen.getByText(`${content.config.keySource}: ${content.config.source.FILE}`),
  ).toBeInTheDocument()
  // nothing is sent by the click alone — removal is a save like any other, so it can still be undone
  fireEvent.click(screen.getByRole('button', { name: content.config.removeKey }))

  expect(screen.getByText(content.config.removeKeyQueued)).toBeInTheDocument()
  expect(server.calls.filter((call) => call.method === 'PUT')).toEqual([])

  fireEvent.click(screen.getByRole('button', { name: content.config.save }))

  const put = server.calls.find((call) => call.method === 'PUT')
  const sent = JSON.parse(put?.body ?? '{}') as Record<string, unknown>
  expect(sent).toHaveProperty('removeApiKey', true)
  // the key field itself stays out of the body: the removal is said by its own flag, never by a blank
  expect(Object.keys(sent)).not.toContain('apiKey')

  // the status is the fresh answer of the server, not what the click implied (UC-WP-63 THEN "nicht gesetzt")
  expect(await screen.findByText(content.config.keyMissing)).toBeInTheDocument()
  expect(
    screen.getByText(`${content.config.keySource}: ${content.config.source.NONE}`),
  ).toBeInTheDocument()
  // and the affordance is gone with the key: nothing left to remove, nothing queued
  expect(screen.queryByText(content.config.removeKeyQueued)).toBeNull()
  expect(screen.queryByRole('button', { name: content.config.removeKey })).toBeNull()

  // the flag is spent: a later save of something else must not delete a key again, or a key set in the
  // meantime would vanish with an unrelated change (R-WP-16 "nur was dort geändert wird, geändert")
  fireEvent.change(screen.getByLabelText(content.config.model), { target: { value: 'gpt-next' } })
  fireEvent.click(screen.getByRole('button', { name: content.config.save }))

  await waitFor(() => expect(server.calls.filter((call) => call.method === 'PUT')).toHaveLength(2))
  const second = JSON.parse(
    server.calls.filter((call) => call.method === 'PUT')[1].body ?? '{}',
  ) as Record<string, unknown>
  expect(second).toHaveProperty('model', 'gpt-next')
  expect(second).not.toHaveProperty('removeApiKey')
})

// UC-WP-63
test('aTypedKeyCancelsTheQueuedRemoval', async () => {
  const server = stubServer({ GET: settingsAnswer() })
  render(<ConfigPage />)
  await screen.findByText(content.config.keySet)

  fireEvent.click(screen.getByRole('button', { name: content.config.removeKey }))
  expect(screen.getByText(content.config.removeKeyQueued)).toBeInTheDocument()
  // two intents cannot travel together: the newer one is the only one the body may carry, or the server
  // would write the new key and delete it again in the same save
  fireEvent.change(screen.getByLabelText(content.config.key), { target: { value: 'sk-other' } })
  expect(screen.queryByText(content.config.removeKeyQueued)).toBeNull()

  fireEvent.click(screen.getByRole('button', { name: content.config.save }))

  await waitFor(() => expect(server.calls.filter((call) => call.method === 'PUT')).toHaveLength(1))
  const sent = JSON.parse(
    server.calls.find((call) => call.method === 'PUT')?.body ?? '{}',
  ) as Record<string, unknown>
  expect(sent).toHaveProperty('apiKey', 'sk-other')
  expect(sent).not.toHaveProperty('removeApiKey')
})

// UC-WP-63
test('anEnvKeyIsReportedButNotRemovable', async () => {
  stubServer({ GET: settingsAnswer({ keySource: 'ENV' }) })
  render(<ConfigPage />)

  expect(await screen.findByText(content.config.keySet)).toBeInTheDocument()
  expect(
    screen.getByText(`${content.config.keySource}: ${content.config.source.ENV}`),
  ).toBeInTheDocument()
  // the page may report the start value, never delete it (R-WP-16)
  expect(screen.queryByRole('button', { name: content.config.removeKey })).toBeNull()
})

// UC-WP-46
test('theUnchangedHintIsGoneFromVisibleFields', async () => {
  const server = stubServer({ GET: settingsAnswer(), PUT: settingsAnswer() })
  render(<ConfigPage />)
  await screen.findByText(content.config.provider)

  // "empty = unchanged" is the key's sentence only; for a visible field empty means the opposite
  for (const label of [
    content.config.model,
    content.config.url,
    content.config.tokenWindow,
    content.config.timeout,
  ]) {
    expect(screen.getByLabelText(label)).not.toHaveAttribute(
      'placeholder',
      content.config.unchanged,
    )
  }
  expect(screen.getByLabelText(content.config.key)).toHaveAttribute(
    'placeholder',
    content.config.unchanged,
  )
  // and the sentence is nowhere painted as a hint either
  expect(document.body.textContent).not.toContain(content.config.unchanged)

  // the third state is reachable: an emptied field is sent empty, so the file can delete the value
  fireEvent.change(screen.getByLabelText(content.config.model), {
    target: { value: '' },
  })
  fireEvent.click(screen.getByRole('button', { name: content.config.save }))

  const put = server.calls.find((call) => call.method === 'PUT')
  const sent = JSON.parse(put?.body ?? '{}') as Record<string, unknown>
  expect(sent).toHaveProperty('model', '')
  expect(Object.keys(sent)).not.toContain('apiKey')
})

test('showsErrorMessageWhenSettingsCannotLoad', async () => {
  stubServer({ GET: new Error('could not read /tmp/home/config.properties') })
  render(<ConfigPage />)

  expect(await screen.findByRole('alert')).toHaveTextContent('config.properties')
})
