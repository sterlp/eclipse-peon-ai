import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'

import { content } from '../../src/shared/content'
import { chooseOption, openChoice, shownChoice } from '../shared/choices'
import {
  agent,
  BASE,
  FIELDS,
  KEY,
  modelQueries,
  renderAgents,
  row,
  stubServer,
} from '../shared/config.agents.stub'

beforeEach(() => {
  cleanup()
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

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

  await waitFor(() => row())
  // ANTD-A3 (AntD 6): the provider's form is the dropdown of the basis's choice — there is no `<option>` list,
  // and the entry that inherits is named by its label, not by an empty value
  expect(await openChoice(content.agents.thinkDefault)).toEqual([
    content.agents.inheritOption,
    'minimal',
    'low',
    'medium',
    'high',
  ])
  expect(shownChoice(content.agents.thinkDefault)).toBe('medium')

  await chooseOption(content.agents.thinkDefault, 'low')
  expect(shownChoice(content.agents.thinkDefault)).toBe('low')

  // one form per section, because the form is the base provider's — the toggle needs its own answer
  cleanup()
  stubServer({ slots: [agent({ id: 'po', name: 'Jon', overridden: true, think: 'true' })] })
  renderAgents({ form: 'TOGGLE', values: [] })

  await waitFor(() => row('po'))
  expect(await openChoice(content.agents.think)).toEqual([content.agents.inheritOption, 'true', 'false'])
  expect(shownChoice(content.agents.think)).toBe('true')
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
  // ANTD-A3 (AntD 6): the stored level the offered list does not carry shows as itself and is offered first
  expect(await openChoice(content.agents.thinkDefault)).toEqual([
    content.agents.inheritOption,
    'ultra',
    'low',
    'medium',
    'high',
  ])
  expect(shownChoice(content.agents.thinkDefault)).toBe('ultra')

  fireEvent.click(within(slot).getByRole('button', { name: content.agents.save }))
  await waitFor(() => expect(server.calls.some((call) => call.method === 'PUT')).toBe(true))
  expect(server.body('PUT')).toMatchObject({ overridden: true, think: 'ultra' })
})

/**
 * UC-WP-67 / INC7B-A1 (R-THINK-10): the Dev row names its field for the base value it carries; every other row
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

/**
 * UC-WP-86 for a slot: the field asks for the slot's own connection. The section hands the slot and its
 * drafted URL to the loader the page owns, and that is what the wire carries. A slot without a URL of its own
 * says so by being empty — its own rule (R-WP-23) — and is resolved where the base is known, not here, so the
 * base travels either way and a key travels never (R-WP-16).
 */
// UC-WP-86
test('agentModelFieldAsksForItsOwnConnection', async () => {
  const server = stubServer({
    slots: [
      agent({ id: 'po', name: 'Jon', overridden: true, url: 'http://slot.example/v1' }),
      agent({ id: 'plan', name: 'Plan', overridden: true }),
    ],
    models: ['gpt-slot'],
  })

  renderAgents()

  const slot = await waitFor(() => row('po'))
  expect(await openChoice(content.agents.model, 'agent-po-model')).toEqual(['gpt-slot'])
  expect(await openChoice(content.agents.model, 'agent-plan-model')).toEqual(['gpt-slot'])

  expect(modelQueries(server)[0]).toEqual({
    provider: 'OPEN_AI',
    url: BASE.url,
    agent: 'po',
    agentUrl: 'http://slot.example/v1',
    refresh: false,
  })
  expect(modelQueries(server)[1], 'an empty slot URL travels empty').toHaveProperty('agentUrl', '')
  expect(JSON.stringify(modelQueries(server))).not.toContain('apiKey')

  // a list is an offer and never an answer: the slot's field still holds what it held, and the section
  // saved nothing of its own on the way
  expect((within(slot).getByLabelText(content.agents.model) as HTMLInputElement).value).toBe('')
  expect(server.calls.filter((call) => call.method === 'PUT')).toEqual([])
})
