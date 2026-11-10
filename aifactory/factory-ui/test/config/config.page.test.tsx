import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'

import { ConfigPage } from '../../src/config/config.page'
import { content } from '../../src/shared/content'
import { chooseOption, openChoice, shownChoice } from '../shared/choices'
import { refused } from '../shared/refusal'
import {
  KEY,
  controlValues,
  sentBodies,
  settingsAnswer,
  stubServer,
  valueOf,
} from '../shared/config.stub'

beforeEach(() => {
  cleanup()
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

// UC-WP-23
test('settingsShowsMaskedConfig', async () => {
  const server = stubServer({ GET: settingsAnswer() })
  const { container } = render(<ConfigPage />)

  expect(await valueOf(content.config.provider)).toBe('OPEN_AI')
  expect(await valueOf(content.config.model)).toBe('gpt-demo')
  expect(await valueOf(content.config.url)).toBe('https://api.example.com/v1')
  expect(await valueOf(content.config.tokenWindow)).toBe('64000')
  expect(await valueOf(content.config.timeout)).toBe('45')
  // the choice shows the catalog label ("No") while the body carries `false` — that difference is pinned by
  // `theChoiceSendsItsWireValue`, because a label is never a wire value
  expect(await valueOf(content.config.sendThinking)).toBe(content.config.no)
  expect(screen.getByText(content.config.keySet)).toBeInTheDocument()
  expect(
    screen.getByText(`${content.config.keySource}: ${content.config.source.FILE}`),
  ).toBeInTheDocument()

  expect(screen.queryByText(/sk-/)).toBeNull()
  expect(container.textContent).not.toContain('sk-')
  expect(container.textContent).not.toContain(KEY)
  expect(controlValues(container).some((value) => value.includes('sk-'))).toBe(false)
  // opening the settings reads the config, the provider defaults and the agent slots, and writes none of
  // them — the third read is Inc 7b's: a slot's state lives in its own file, not in the config. These three
  // reads are also the guard of R-WP-50: a model field must not ask a provider when the page mounts.
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

  // the submit chain of the `Form` carries the save and answers one microtask later — read the body once it
  // is on the wire, and read that it is on the wire exactly once
  await waitFor(() => expect(sentBodies(server)).toHaveLength(1))
  const put = server.calls.find((call) => call.method === 'PUT')
  expect(put?.path).toBe('/api/settings')
  const sent = JSON.parse(put?.body ?? '{}') as Record<string, unknown>
  expect(sent.model).toBe('new-model')
  // R-WP-16 since Inc 7a: every named field travels, an emptied one as an empty string — that is how a
  // cleared field is deleted from the file (UC-WP-46). The url was empty here, so it says "delete", which
  // the file answers by keeping nothing.
  expect(sent).toHaveProperty('url', '')
  // the choice's wire value travels as text next to it — the row shows a label, the file reads a boolean
  expect(sent).toHaveProperty('sendThinking', 'false')
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

/**
 * UC-WP-24 over the basis' choice: the think row is the one field whose label and value differ by contract
 * (`docs/config.md`: the file holds text, the core reads a boolean). Reading the displayed label would let a
 * swapped option pass — the body is the contract.
 */
// UC-WP-24
test('theChoiceSendsItsWireValue', async () => {
  const server = stubServer({ GET: settingsAnswer(), PUT: settingsAnswer() })
  render(<ConfigPage />)
  await screen.findByText(content.config.provider)

  // the stored answer says false: the field reads as "No" and the body says the wire value of that label
  fireEvent.click(screen.getByRole('button', { name: content.config.save }))
  await waitFor(() => expect(sentBodies(server)).toHaveLength(1))
  expect(sentBodies(server)[0], 'the untouched choice sends its own wire value').toHaveProperty(
    'sendThinking',
    'false',
  )

  await chooseOption(content.config.sendThinking, content.config.yes)
  expect(shownChoice(content.config.sendThinking), 'the screen shows the label').toBe(content.config.yes)
  fireEvent.click(screen.getByRole('button', { name: content.config.save }))

  await waitFor(() => expect(sentBodies(server)).toHaveLength(2))
  expect(sentBodies(server)[1], 'the chosen label sends its wire value, never its text').toHaveProperty(
    'sendThinking',
    'true',
  )
})

// UC-WP-62
test('changingTheProviderUpdatesTheUrl', async () => {
  const server = stubServer({
    GET: settingsAnswer({ url: 'https://old.example' }),
    PUT: settingsAnswer({ provider: 'GOOGLE_GEMINI', url: null, model: null }),
  })
  render(<ConfigPage />)
  expect(await valueOf(content.config.url)).toBe('https://old.example')

  await chooseOption(content.config.provider, 'OLLAMA')

  // the default the server named for that provider, at once and without a save in between (R-WP-16)
  expect(screen.getByLabelText(content.config.url)).toHaveValue('https://ollama.example')
  expect(server.calls.filter((call) => call.method === 'PUT')).toEqual([])

  // a provider without a default leaves the field empty — the page invents nothing
  await chooseOption(content.config.provider, 'GOOGLE_GEMINI')
  expect(screen.getByLabelText(content.config.url)).toHaveValue('')

  // and the choice of the list is what the file is told: the entry's name on the wire, not a display text
  fireEvent.click(screen.getByRole('button', { name: content.config.save }))
  await waitFor(() => expect(sentBodies(server)).toHaveLength(1))
  expect(sentBodies(server)[0], 'the provider travels as the name the server listed').toHaveProperty(
    'provider',
    'GOOGLE_GEMINI',
  )
})

// UC-WP-62
test('theModelFieldIsClearedOnProviderChange', async () => {
  stubServer({ GET: settingsAnswer({ model: 'gpt-demo' }) })
  render(<ConfigPage />)
  expect(await valueOf(content.config.model)).toBe('gpt-demo')

  await chooseOption(content.config.provider, 'OLLAMA')

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

  await chooseOption(content.config.provider, 'GOOGLE_GEMINI')

  // shown and disabled — not hidden: the row still says what the form is talking about (Q3)
  expect(screen.getByLabelText(content.config.sendThinking)).toBeDisabled()
  expect(screen.getByText(content.config.thinkUnsupported)).toBeInTheDocument()
  // disabled is not cleared: the stored value stands until a save says otherwise (UC-WP-46)
  expect(await valueOf(content.config.sendThinking)).toBe(content.config.no)
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

  // OPEN_AI answers a fixed list, so the agent field is that list and not a text box. ANTD-A3 (AntD 6): the list
  // is the dropdown of the base — opened and read there, since a choice renders no `<option>`s. Since R-WP-49
  // the page shows the label twice — the default form edits the same value — so the handle is the Dev slot's
  // own field id, and the reading stays inside the slot's card.
  const devSlot = await screen.findByTestId('agent-slot-dev')
  await within(devSlot).findByLabelText(content.agents.thinkDefault)
  expect(await openChoice(content.agents.thinkDefault, 'agent-dev-think')).toEqual([
    content.agents.inheritOption,
    'minimal',
    'low',
    'medium',
    'high',
  ])
  expect(shownChoice(content.agents.thinkDefault, 'agent-dev-think')).toBe('medium')

  // the provider chosen on the page decides, at once and without a save in between (as R-WP-16 does for the URL)
  await chooseOption(content.config.provider, 'GOOGLE_GEMINI')
  // neither place keeps a control it cannot use: the label is gone from the whole page
  expect(screen.queryByLabelText(content.agents.thinkDefault)).toBeNull()
  expect(screen.getByTestId('agent-dev-think-unsupported')).toBeInTheDocument()

  // one provider answer serves both areas — a second read would be a second opinion on one question
  expect(server.calls.filter((call) => call.path === '/api/settings/providers')).toHaveLength(1)
})

/**
 * R-WP-49: the default form and the Dev slot edit one and the same value — so a save on one side has
 * to refresh the other. Without the re-read the slot keeps its old draft and its next save silently
 * writes the superseded value back: the second store R-WP-49 rules out.
 */
// UC-WP-85
test('savingTheDefaultThinkShowsTheNewValueInTheDevSlot', async () => {
  const devFile = (think: string) => ({
    id: 'dev',
    name: 'Dev',
    overridden: true,
    model: null,
    url: null,
    think,
    temperature: null,
    keySet: false,
  })
  const server = stubServer({
    GET: settingsAnswer(),
    PUT: settingsAnswer({ think: 'high' }),
    AGENTS: [devFile('low')],
  })
  render(<ConfigPage />)

  const devSlot = await screen.findByTestId('agent-slot-dev')
  await within(devSlot).findByLabelText(content.agents.thinkDefault)
  expect(shownChoice(content.agents.thinkDefault, 'agent-dev-think')).toBe('low')

  // what the dev file holds once the default form has saved: the value the PUT below writes
  server.script.AGENTS = [devFile('high')]

  await chooseOption(content.agents.thinkDefault, 'high', 'settings-think')
  // the slot card has a save of its own — the default form's is the one being pressed here
  const form = document.querySelector('form') as HTMLElement
  fireEvent.click(within(form).getByRole('button', { name: content.config.save }))

  // the section reads its files again and the card shows the saved value — while only the settings
  // were written: the slot itself was not saved (the PUT to /api/settings is the page's only write)
  await waitFor(() =>
    expect(server.calls.filter((call) => call.path === '/api/settings/agents')).toHaveLength(2),
  )
  await waitFor(() =>
    expect(shownChoice(content.agents.thinkDefault, 'agent-dev-think')).toBe('high'),
  )
  expect(server.calls.filter((call) => call.method === 'PUT')).toHaveLength(1)
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

  // the submit chain of the `Form` answers one microtask later — once, which is what this counts
  await waitFor(() => expect(sentBodies(server)).toHaveLength(1))
  const sent = sentBodies(server)[0]
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

  // the submit chain of the `Form` answers one microtask later — once, which is what this counts
  await waitFor(() => expect(sentBodies(server)).toHaveLength(1))
  const sent = sentBodies(server)[0]
  expect(sent).toHaveProperty('model', '')
  expect(Object.keys(sent)).not.toContain('apiKey')
})

test('showsErrorMessageWhenSettingsCannotLoad', async () => {
  stubServer({ GET: new Error('could not read /tmp/home/config.properties') })
  render(<ConfigPage />)

  expect(await screen.findByRole('alert')).toHaveTextContent('config.properties')
})

// UC-WP-93
test('aRejectedSaveMarksTheMaxTokensFieldAndKeepsTheTypedValue', async () => {
  const REASON = 'maxTokens must be a whole number of tokens, or 0 for the provider default'
  const server = stubServer({
    GET: settingsAnswer(),
    PUT: refused({
      status: 400,
      error: 'Bad Request',
      message: REASON,
      fieldErrors: [{ field: 'maxTokens', message: REASON }],
    }),
  })
  render(<ConfigPage />)
  await screen.findByDisplayValue('gpt-demo')

  fireEvent.change(screen.getByLabelText(content.config.maxTokens), { target: { value: 'abc' } })
  fireEvent.click(screen.getByRole('button', { name: content.config.save }))

  // the server's sentence stands at the field it named, and only there (UC-WP-93 GIVEN 1)
  const help = await screen.findByTestId('field-error-maxTokens')
  expect(help).toHaveTextContent(REASON)
  const field = screen.getByLabelText(content.config.maxTokens).closest('.api-field')
  expect(field).not.toBeNull()
  expect(help.closest('.api-field')).toBe(field)
  expect(field).toHaveClass('api-field-error')
  expect(field?.closest('dd')).not.toBeNull()
  // what the viewer typed stays — the reason says which field to fix, it does not throw the answer away
  expect(screen.getByLabelText(content.config.maxTokens)).toHaveValue('abc')
  expect(screen.queryAllByTestId(/^field-error-/)).toHaveLength(1)
  expect(screen.queryByRole('alert')).toBeNull()
  expect(server.calls.filter((call) => call.method === 'PUT')).toHaveLength(1)
})

// UC-WP-93
test('aConflictWithoutFieldStillShowsTheAlert', async () => {
  stubServer({
    GET: settingsAnswer(),
    PUT: refused({ status: 409, error: 'Conflict', message: 'a turn is running on this project' }),
  })
  render(<ConfigPage />)
  await screen.findByDisplayValue('gpt-demo')

  fireEvent.click(screen.getByRole('button', { name: content.config.save }))

  expect(await screen.findByRole('alert')).toHaveTextContent('a turn is running on this project')
  expect(screen.queryAllByTestId(/^field-error-/)).toEqual([])
})

// UC-WP-93
test('anUnwrappedFieldErrorStillReachesTheScreen', async () => {
  const REASON = 'think is not offered by this provider'
  stubServer({
    GET: settingsAnswer(),
    PUT: refused({
      status: 400,
      error: 'Bad Request',
      message: REASON,
      fieldErrors: [{ field: 'think', message: REASON }],
    }),
  })
  render(<ConfigPage />)
  await screen.findByDisplayValue('gpt-demo')

  fireEvent.click(screen.getByRole('button', { name: content.config.save }))

  // the page wraps no `think` field — a name it does not draw must still be readable, never lost between
  // the two display paths (R8)
  expect(await screen.findByRole('alert')).toHaveTextContent(REASON)
  expect(screen.queryAllByTestId(/^field-error-/)).toEqual([])
  expect(screen.getByLabelText(content.config.model)).toHaveValue('gpt-demo')
})

// UC-WP-93
test('theSettingsFormIsOneAntdForm', async () => {
  stubServer({ GET: settingsAnswer() })
  const { container } = render(<ConfigPage />)
  await screen.findByDisplayValue('gpt-demo')

  // the native envelope was replaced, not nested (E2), and the fields stayed where their ids say
  const forms = container.querySelectorAll('form')
  expect(forms).toHaveLength(1)
  expect(forms[0]).toHaveClass('settings-form', 'ant-form')
  expect(forms[0].contains(document.getElementById('settings-provider'))).toBe(true)
})
