import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'

import { Agents } from '../../src/config/config.agents'
import { content } from '../../src/shared/content'
import type { AgentSettings } from '../../src/config/config.agents.client'

const KEY = 'sk-agent-secret-0123456789'

beforeEach(() => {
  cleanup()
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

/** A slot as the file answers it — nothing overridden unless a test says otherwise. */
function agent(overrides: Partial<AgentSettings> = {}): AgentSettings {
  return {
    id: 'dev',
    name: 'Dev',
    overridden: false,
    model: null,
    url: null,
    think: null,
    temperature: null,
    keySet: false,
    ...overrides,
  }
}

type Call = { method: string; path: string; body?: string }

/**
 * Mutable `fetch` stand-in: the slots for the read, one saved slot for the write, every call recorded with
 * its body. Honest limit, as in `config.page.test.tsx` — the answers are stubbed, so this view cannot go
 * red from a backend mutation; the file itself is proven by `AgentConfigFileTest`.
 */
function stubServer(initial: {
  slots: AgentSettings[]
  saved?: AgentSettings
  /** set to make every PUT answer as the server refuses it — the 400 path of R-WP-16 */
  rejectWith?: string
}): {
  calls: Call[]
  body: (method: string) => Record<string, unknown>
} {
  const calls: Call[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const method = (init?.method ?? 'GET').toUpperCase()
      calls.push({ method, path: String(input), body: init?.body as string | undefined })
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
function row(id = 'dev'): HTMLElement {
  return screen.getByTestId('agent-slot-' + id)
}

/** The four inheritable fields of a slot — visible only while the slot overrides (R-WP-23). */
const FIELDS = [content.agents.model, content.agents.url, content.agents.think, content.agents.temperature]

// UC-WP-44
test('anAgentWithoutConfigShowsNoFields', async () => {
  stubServer({ slots: [agent()] })

  render(<Agents />)

  const slot = await waitFor(() => row())
  expect(within(slot).getByText('Dev')).toBeTruthy()
  expect((within(slot).getByRole('checkbox') as HTMLInputElement).checked).toBe(false)
  // "keine Dev-Felder sind sichtbar": the empty fields are not there to be mistaken for values
  for (const label of FIELDS) {
    expect(within(slot).queryByLabelText(label)).toBeNull()
  }
})

// UC-WP-44
test('checkingTheBoxShowsTheFieldsThatInheritByBeingEmpty', async () => {
  stubServer({ slots: [agent()] })

  render(<Agents />)

  const slot = await waitFor(() => row())
  fireEvent.click(within(slot).getByRole('checkbox'))

  for (const label of FIELDS) {
    const field = within(slot).getByLabelText(label) as HTMLInputElement
    expect(field.value).toBe('')
  }
  // the hint is the sentence an empty field says (UC-WP-44 "ein leeres Feld erbt")
  expect(within(slot).getByText(content.agents.inherits)).toBeTruthy()
})

/** UC-WP-44 GIVEN 2: the save names the slot and carries its fields — a slot per request (D-12). */
// UC-WP-44
test('savingAnOverrideWritesOnlyThatSlot', async () => {
  const server = stubServer({
    slots: [agent({ overridden: true, model: 'gpt-5' }), agent({ id: 'po', name: 'Jon' })],
    saved: agent({ overridden: true, model: 'gpt-5' }),
  })

  render(<Agents />)

  const slot = await waitFor(() => row())
  expect((within(slot).getByLabelText(content.agents.model) as HTMLInputElement).value).toBe('gpt-5')

  fireEvent.click(within(slot).getByRole('button', { name: content.agents.save }))

  await waitFor(() => expect(server.calls.some((call) => call.method === 'PUT')).toBe(true))
  const written = server.body('PUT')
  expect(server.calls.filter((call) => call.method === 'PUT').at(-1)?.path).toBe('/api/settings/agents/dev')
  expect(written).toMatchObject({ overridden: true, model: 'gpt-5' })
  // the neighbour slot is untouched, and an empty key field says nothing at all (R-WP-16)
  expect(JSON.stringify(written)).not.toContain('apiKey')
  expect(server.calls.filter((call) => call.path === '/api/settings/agents/po')).toHaveLength(0)
})

/** UC-WP-45: the unchecked box is a clear — the save says so, and the file's answer empties the row. */
// UC-WP-45
test('uncheckingTheBoxSendsTheClearAndTheSlotReadsAsDefaultAgain', async () => {
  const stored = agent({ overridden: false })
  const server = stubServer({
    slots: [agent({ overridden: true, model: 'gpt-5', think: 'medium' })],
    saved: stored,
  })

  render(<Agents />)

  const slot = await waitFor(() => row())
  fireEvent.click(within(slot).getByRole('checkbox'))
  fireEvent.click(within(slot).getByRole('button', { name: content.agents.save }))

  await waitFor(() => expect(server.calls.some((call) => call.method === 'PUT')).toBe(true))
  expect(server.body('PUT')).toMatchObject({ overridden: false })

  // the row is rebuilt from the answer, not from what was typed: no fields, empty checkbox (next open)
  await waitFor(() =>
    expect((within(row()).getByRole('checkbox') as HTMLInputElement).checked).toBe(false),
  )
  expect(within(row()).queryByLabelText(content.agents.model)).toBeNull()
})

/** UC-WP-46 GIVEN 2: one cleared field is that field's deletion, the neighbour keeps its value. */
// UC-WP-46
test('clearingTheModelFieldSendsTheDeletionAndKeepsThink', async () => {
  const server = stubServer({
    slots: [agent({ id: 'compact', name: 'Compact', overridden: true, model: 'gpt-5', think: 'medium' })],
    saved: agent({ id: 'compact', name: 'Compact', overridden: true, think: 'medium' }),
  })

  render(<Agents />)

  const slot = await waitFor(() => row('compact'))
  fireEvent.change(within(slot).getByLabelText(content.agents.model), { target: { value: '' } })
  fireEvent.click(within(slot).getByRole('button', { name: content.agents.save }))

  await waitFor(() => expect(server.calls.some((call) => call.method === 'PUT')).toBe(true))
  expect(server.body('PUT')).toMatchObject({ overridden: true, model: '', think: 'medium' })
})

/**
 * R-WP-16 for a slot: the key is a status, its field is never filled, an empty one is left out of the
 * save, and only its own button asks for the removal.
 */
test('anAgentKeyIsNeverPrefilledAndOnlyTheButtonRemovesIt', async () => {
  const server = stubServer({
    slots: [agent({ overridden: true, keySet: true })],
    saved: agent({ overridden: true, keySet: true }),
  })

  render(<Agents />)

  const slot = await waitFor(() => row())
  const key = within(slot).getByLabelText(content.config.key) as HTMLInputElement
  expect(key.value).toBe('')
  expect(key.type).toBe('password')
  expect(slot.textContent).toContain(content.config.keySet)
  expect(slot.textContent).not.toContain(KEY)

  fireEvent.click(within(slot).getByRole('button', { name: content.config.removeKey }))
  fireEvent.click(within(slot).getByRole('button', { name: content.agents.save }))

  await waitFor(() => expect(server.calls.some((call) => call.method === 'PUT')).toBe(true))
  const written = server.body('PUT')
  expect(written).toMatchObject({ overridden: true, removeApiKey: true })
  expect(JSON.stringify(written)).not.toContain('apiKey')
})

/**
 * R-WP-16 as config.md writes it: „im UI hebt Tippen ins Key-Feld eine vorgemerkte Entfernung auf". The
 * proof is the body of the save that follows — the row can look right while still sending the deletion.
 */
// R-WP-16
test('typingAKeyCancelsTheQueuedRemoval', async () => {
  const server = stubServer({
    slots: [agent({ overridden: true, keySet: true })],
    saved: agent({ overridden: true, keySet: true }),
  })

  render(<Agents />)

  const slot = await waitFor(() => row())
  fireEvent.click(within(slot).getByRole('button', { name: content.config.removeKey }))
  expect(within(slot).getByText(content.config.removeKeyQueued)).toBeTruthy()

  fireEvent.change(within(slot).getByLabelText(content.config.key), { target: { value: KEY } })
  // the row stops promising a deletion it will not send
  expect(within(slot).queryByText(content.config.removeKeyQueued)).toBeNull()

  fireEvent.click(within(slot).getByRole('button', { name: content.agents.save }))
  await waitFor(() => expect(server.calls.some((call) => call.method === 'PUT')).toBe(true))
  const written = server.body('PUT')
  expect(written).toMatchObject({ overridden: true, apiKey: KEY })
  expect(JSON.stringify(written)).not.toContain('removeApiKey')
})

/**
 * The two intents really can be typed — a key first, „Remove key" second — and the page shows the refusal
 * it earns: the server's message on the screen, what was typed still in the field, the slot not rewritten
 * from an answer that never came. (That the file stayed untouched is `AgentSettingsTest`'s proof.)
 */
// R-WP-16
test('aRefusedSaveSaysWhyAndKeepsWhatWasTyped', async () => {
  const refused = 'apiKey and removeApiKey are two answers — send one'
  const server = stubServer({
    slots: [agent({ overridden: true, keySet: true })],
    rejectWith: refused,
  })

  render(<Agents />)

  const slot = await waitFor(() => row())
  fireEvent.change(within(slot).getByLabelText(content.config.key), { target: { value: KEY } })
  fireEvent.click(within(slot).getByRole('button', { name: content.config.removeKey }))
  fireEvent.click(within(slot).getByRole('button', { name: content.agents.save }))

  // both intents travelled — the refusal answers this body, it is not a guard of the UI
  await waitFor(() => expect(screen.getByRole('alert').textContent).toContain('two answers'))
  expect(server.body('PUT')).toMatchObject({ apiKey: KEY, removeApiKey: true })

  // the draft survives the failure: the message names the field to fix, nothing has to be retyped
  expect((within(row()).getByLabelText(content.config.key) as HTMLInputElement).value).toBe(KEY)
  await waitFor(() =>
    expect(within(row()).getByRole('button', { name: content.agents.save })).toBeTruthy(),
  )
})