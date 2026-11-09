import { vi } from 'vitest'
import { screen } from '@testing-library/react'

import type { Settings } from '../../src/config/config.client'

/** The key a leaked view would repeat — every masked read names it from here. */
export const KEY = 'sk-file-secret-0123456789'

/** The draft endpoint of a model list (R-WP-50) — a POST, because the connection has no place in a query. */
const MODELS_PATH = '/api/settings/models'

export type Script = {
  GET: unknown
  PUT: unknown
  PROVIDERS: unknown
  AGENTS: unknown
  MODELS: unknown
}
export type Call = { method: string; path: string; body?: string }

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
    extraBodySupported: true,
  },
  {
    name: 'OLLAMA',
    defaultUrl: 'https://ollama.example',
    thinkSupported: true,
    thinkForm: 'TOGGLE',
    thinkValues: [],
    extraBodySupported: false,
  },
  {
    name: 'GOOGLE_GEMINI',
    defaultUrl: null,
    thinkSupported: false,
    thinkForm: 'NONE',
    thinkValues: [],
    extraBodySupported: false,
  },
]

/**
 * Mutable `fetch` stand-in: one answer per endpoint, every call recorded with its body. Honest limit,
 * same as the Projects tests — the answers are stubbed, so this view cannot go red from a backend
 * mutation; the wire contract itself is proven by `SettingsControllerTest`.
 */
export function stubServer(initial: Partial<Script>): {
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
      // the models of the connection the form describes (R-WP-50) — asked for, never mounted
      MODELS: [],
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
          : path === '/api/settings/models'
            ? server.script.MODELS
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
export function settingsAnswer(overrides: Partial<Settings> = {}): Record<string, unknown> {
  return {
    provider: 'OPEN_AI',
    model: 'gpt-demo',
    think: null,
    url: 'https://api.example.com/v1',
    tokenWindow: 64000,
    timeoutSeconds: 45,
    sendThinking: false,
    maxTokens: null,
    queryParams: null,
    headerParams: null,
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
export async function valueOf(label: string): Promise<string> {
  const field = await screen.findByText(label)
  const row = field.parentElement?.querySelector('dd')
  // ANTD-A3 (AntD 6): a choice shows itself in the base's display, not in a `<select>`. What stands there is the
  // catalog label — the wire value is pinned in the body by `theChoiceSendsItsWireValue`.
  const choice = row?.querySelector('.ant-select-content')
  if (choice) {
    return choice.getAttribute('title') ?? choice.textContent ?? ''
  }
  const value = row?.querySelector('input, select')
  if (value) {
    return (value as HTMLInputElement).value
  }
  return row?.textContent ?? ''
}

/** What really sits in the form — text content cannot see the value of an input. */
export function controlValues(container: HTMLElement): string[] {
  return Array.from(container.querySelectorAll('input, select')).map(
    (el) => (el as HTMLInputElement).value,
  )
}

/**
 * The bodies the page saved, in the order it sent them. A choice shows its catalog label on the screen and
 * its wire value in the request — the value only exists here, so this is where it has to be pinned.
 */
export function sentBodies(server: { calls: Call[] }): Record<string, unknown>[] {
  return server
    .calls.filter((call) => call.method === 'PUT')
    .map((call) => JSON.parse(call.body ?? '{}') as Record<string, unknown>)
}

/** The connections the page asked a model list for, in the order it asked. */
export function modelQueries(server: { calls: Call[] }): Record<string, unknown>[] {
  return server.calls
    .filter((call) => call.path === MODELS_PATH)
    .map((call) => JSON.parse(call.body ?? '{}') as Record<string, unknown>)
}
