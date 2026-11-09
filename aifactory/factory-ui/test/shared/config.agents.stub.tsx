import { expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'

import { Agents } from '../../src/config/config.agents'
import { content } from '../../src/shared/content'
import type { AgentSettings } from '../../src/config/config.agents.client'
import { listModels, type ThinkInput } from '../../src/config/config.client'

/** The key a leaked slot view would repeat — every key test names it from here. */
export const KEY = 'sk-agent-secret-0123456789'

/** A slot as the file answers it — nothing overridden unless a test says otherwise. */
export function agent(overrides: Partial<AgentSettings> = {}): AgentSettings {
  return {
    id: 'dev',
    name: 'Dev',
    overridden: false,
    model: null,
    url: null,
    think: null,
    temperature: null,
    extraBody: null,
    tools: null,
    keySet: false,
    ...overrides,
  }
}

export type Call = { method: string; path: string; body?: string }

/**
 * Mutable `fetch` stand-in: the slots for the read, one saved slot for the write, every call recorded with
 * its body. Honest limit, as in `config.page.test.tsx` — the answers are stubbed, so this view cannot go
 * red from a backend mutation; the file itself is proven by `AgentConfigFileTest`.
 */
export function stubServer(initial: {
  slots: AgentSettings[]
  saved?: AgentSettings
  /** set to make every PUT answer as the server refuses it — the 400 path of R-WP-16 */
  rejectWith?: string
  /** the answer of the model list of a drafted connection (R-WP-50) — asked, never mounted */
  models?: string[]
  /** the agent's whole tool set (R-WP-52) — an asked allowlist answers its intersection, none the whole set */
  tools?: string[]
}): {
  calls: Call[]
  body: (method: string) => Record<string, unknown>
} {
  const calls: Call[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const method = (init?.method ?? 'GET').toUpperCase()
      const path = String(input)
      calls.push({ method, path, body: init?.body as string | undefined })
      if (path.endsWith('/api/settings/models')) {
        return response(200, initial.models ?? [])
      }
      if (path.includes('/tools')) {
        // the preview reads the typed entry from the query; without one the whole set answers (UC-WP-88)
        const asked = path.split('?allowlist=')[1]
        const all = initial.tools ?? []
        const names =
          asked === undefined
            ? all
            : decodeURIComponent(asked)
                .split(',')
                .map((name) => name.trim())
                .filter((name) => all.includes(name))
        return response(200, names)
      }
      if (method === 'PUT') {
        if (initial.rejectWith) {
          return response(400, { status: 400, error: 'Bad Request', message: initial.rejectWith })
        }
        return response(200, initial.saved ?? initial.slots[0])
      }
      return response(200, initial.slots)
    }),
  )
  return {
    calls,
    // the LAST write — a test that saves twice reads the second intent, not the first
    body: (method) => {
      const match = calls.filter((call) => call.method === method).at(-1)
      expect(match, 'a ' + method + ' call was made').toBeDefined()
      return JSON.parse(match?.body ?? '{}') as Record<string, unknown>
    },
  }
}

function response(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

/** One slot's row — every assertion stays inside it, because the page shows the slots side by side. */
export function row(id = 'dev'): HTMLElement {
  return screen.getByTestId('agent-slot-' + id)
}

/**
 * The inheritable fields of a slot — visible only while the slot overrides (R-WP-23), the extra body only
 * with a provider that carries it, which the default `renderAgents` offers.
 * The Dev slot carries the base think value of every other agent (R-THINK-10), so its field is named for
 * that — the labels below are the Dev row's, which is the row both tests of the main file read.
 */
export const FIELDS = [
  content.agents.model,
  content.agents.url,
  content.agents.thinkDefault,
  content.agents.temperature,
  content.agents.extraBody,
  content.agents.tools,
]

/**
 * The section with the think form of the chosen base provider (UC-WP-67) and the model loader of the page
 * (R-WP-50): a slot has no provider of its own, so the base the default form holds is what the page hands
 * down. The think default is the core's honest `UNKNOWN` — a text field, which is what every test here that
 * is not about the form always saw. The extra body's capacity is the same provider's answer (R-WP-52) and
 * defaults to offered.
 */
export const BASE = { provider: 'OPEN_AI', url: 'https://api.example.com/v1' }

export function renderAgents(
  think: ThinkInput = { form: 'UNKNOWN', values: [] },
  { extraBodySupported = true }: { extraBodySupported?: boolean } = {},
) {
  return render(
    <Agents
      think={think}
      connection={BASE.provider + ' ' + BASE.url}
      extraBodySupported={extraBodySupported}
      reloadToken={0}
      loadModels={(slotId, slotUrl, force) =>
        listModels({ ...BASE, agent: slotId, agentUrl: slotUrl, refresh: force })
      }
    />,
  )
}

/** The connections a section asked model lists for, in the order it asked (R-WP-50). */
export function modelQueries(server: { calls: Call[] }): Record<string, unknown>[] {
  return server.calls
    .filter((call) => call.path === '/api/settings/models')
    .map((call) => JSON.parse(call.body ?? '{}') as Record<string, unknown>)
}

/** The preview asks the section made, in the order it asked (R-WP-52). */
export function previewPaths(server: { calls: Call[] }): string[] {
  return server.calls.filter((call) => call.path.includes('/tools')).map((call) => call.path)
}
