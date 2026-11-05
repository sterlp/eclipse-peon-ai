import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'

import { Agents } from '../../src/config/config.agents'
import { content } from '../../src/shared/content'
import type { AgentSettings } from '../../src/config/config.agents.client'
import type { ThinkInput } from '../../src/config/config.client'

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

/**
 * The four inheritable fields of a slot — visible only while the slot overrides (R-WP-23).
 * The Dev slot carries the base think value of every other agent (R-THINK-10), so its field is named for
 * that — the labels below are the Dev row's, which is the row both tests below read.
 */
const FIELDS = [
  content.agents.model,
  content.agents.url,
  content.agents.thinkDefault,
  content.agents.temperature,
]

/**
 * The section with the think form of the chosen base provider (UC-WP-67). The default is the core's honest
 * `UNKNOWN` — a text field, which is what every test here that is not about the form always saw.
 */
function renderAgents(think: ThinkInput = { form: 'UNKNOWN', values: [] }) {
  return render(<Agents think={think} />)
}

/** The values a selection offers, in the order the screen shows them. */
function options(select: HTMLSelectElement): string[] {
  return Array.from(select.options).map((option) => option.value)
}

// UC-WP-44
test('anAgentWithoutConfigShowsNoFields', async () => {
  stubServer({ slots: [agent()] })

  renderAgents()

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

  renderAgents()

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

  renderAgents()

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

  renderAgents()

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

  renderAgents()

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

  renderAgents()

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

  renderAgents()

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

  renderAgents()

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

/**
 * UC-WP-67 GIVEN 1: the think field is the base provider's own form, not a text box by choice. A `VALUES`
 * provider answers with its list, a `Toggle` provider with its two answers — and the empty choice that
 * inherits stays first either way (R-THINK-10).
 */
// UC-WP-67
test('thinkFieldFollowsTheProviderForm', async () => {
  stubServer({ slots: [agent({ overridden: true, think: 'medium' })] })

  renderAgents({ form: 'VALUES', values: ['minimal', 'low', 'medium', 'high'] })

  const slot = await waitFor(() => row())
  const think = within(slot).getByLabelText(content.agents.thinkDefault) as HTMLSelectElement
  expect(think.tagName).toBe('SELECT')
  expect(think.value).toBe('medium')
  expect(options(think)).toEqual(['', 'minimal', 'low', 'medium', 'high'])
  expect(think.options[0].textContent).toBe(content.agents.inheritOption)

  fireEvent.change(think, { target: { value: 'low' } })
  expect(think.value).toBe('low')

  // one form per section, because the form is the base provider's — the toggle needs its own answer
  cleanup()
  stubServer({ slots: [agent({ id: 'po', name: 'Jon', overridden: true, think: 'true' })] })
  renderAgents({ form: 'TOGGLE', values: [] })

  const toggle = (await waitFor(() => row('po')).then((po) =>
    within(po).getByLabelText(content.agents.think),
  )) as HTMLSelectElement
  expect(toggle.tagName).toBe('SELECT')
  expect(options(toggle)).toEqual(['', 'true', 'false'])
  expect(toggle.value).toBe('true')
})

/**
 * UC-WP-67 GIVEN 3: `NONE` is no control and no invented list — the hint is the whole answer. The stored
 * value still travels on save, so a provider that cannot show think does not delete what the file holds.
 */
// UC-WP-67
test('thinkFieldIsHiddenForNone', async () => {
  const server = stubServer({ slots: [agent({ overridden: true, think: 'high' })] })

  renderAgents({ form: 'NONE', values: [] })

  const slot = await waitFor(() => row())
  expect(within(slot).queryByLabelText(content.agents.thinkDefault)).toBeNull()
  expect(within(slot).getByTestId('agent-dev-think-unsupported').textContent).toBe(
    content.agents.thinkUnsupported,
  )
  // the other fields of the slot keep their form — only think has none here
  expect(within(slot).getByLabelText(content.agents.model)).toBeTruthy()

  fireEvent.click(within(slot).getByRole('button', { name: content.agents.save }))
  await waitFor(() => expect(server.calls.some((call) => call.method === 'PUT')).toBe(true))
  expect(server.body('PUT')).toMatchObject({ overridden: true, think: 'high' })
})

/**
 * UC-WP-67 with ADR-0064: a level the offered list does not carry is shown as itself and saved as itself.
 * A selection that quietly picked the nearest value would rewrite the file on the next save.
 */
// UC-WP-67
test('storedThinkStaysVerbatim', async () => {
  const server = stubServer({ slots: [agent({ overridden: true, think: 'ultra' })] })

  renderAgents({ form: 'VALUES', values: ['low', 'medium', 'high'] })

  const slot = await waitFor(() => row())
  const think = within(slot).getByLabelText(content.agents.thinkDefault) as HTMLSelectElement
  expect(think.value).toBe('ultra')
  expect(options(think)).toEqual(['', 'ultra', 'low', 'medium', 'high'])

  fireEvent.click(within(slot).getByRole('button', { name: content.agents.save }))
  await waitFor(() => expect(server.calls.some((call) => call.method === 'PUT')).toBe(true))
  expect(server.body('PUT')).toMatchObject({ overridden: true, think: 'ultra' })
})

/**
 * UC-WP-67 / A4 (R-THINK-10): the Dev row names its field for the base value it carries; every other row
 * keeps the plain name, or the page would claim twice that a slot is the default.
 */
// UC-WP-67
test('devThinkIsLabeledAsTheDefault', async () => {
  stubServer({
    slots: [agent({ overridden: true }), agent({ id: 'po', name: 'Jon', overridden: true })],
  })

  renderAgents({ form: 'FREE', values: [] })

  const dev = await waitFor(() => row())
  expect(within(dev).getByLabelText(content.agents.thinkDefault)).toBeTruthy()
  expect(within(dev).queryByLabelText(content.agents.think)).toBeNull()

  const jon = within(row('po'))
  expect(jon.getByLabelText(content.agents.think)).toBeTruthy()
  expect(jon.queryByLabelText(content.agents.thinkDefault)).toBeNull()
})
