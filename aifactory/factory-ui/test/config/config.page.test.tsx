import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'

import { ConfigPage } from '../../src/config/config.page'
import { content } from '../../src/shared/content'
import { chooseOption, openChoice, shownChoice } from '../shared/choices'
import type { Settings } from '../../src/config/config.client'

const KEY = 'sk-file-secret-0123456789'

/**
 * The default form's think field by its own id (R-WP-49): the label is the Dev slot's too, so a label could
 * not say which of the two fields a test means.
 */
const DEFAULT_THINK = 'settings-think'

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
async function valueOf(label: string): Promise<string> {
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
 * The bodies the page saved, in the order it sent them. A choice shows its catalog label on the screen and
 * its wire value in the request — the value only exists here, so this is where it has to be pinned.
 */
function sentBodies(server: { calls: Call[] }): Record<string, unknown>[] {
  return server
    .calls.filter((call) => call.method === 'PUT')
    .map((call) => JSON.parse(call.body ?? '{}') as Record<string, unknown>)
}

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

/**
 * R-WP-49: the default form shows the Dev slot's think value in the form of the provider it names — the same
 * control the slot row builds, because both places show one value and neither has a store of its own. That a
 * stored level outside the offered list stays verbatim is the shared component's proof, pinned once for both
 * places in `config.agents.test.tsx` (`storedThinkStaysVerbatim`).
 */
// UC-WP-85
test('defaultThinkShowsTheProviderFormAndSaves', async () => {
  const server = stubServer({
    GET: settingsAnswer({ think: 'medium' }),
    PUT: settingsAnswer({ think: 'high' }),
  })
  render(<ConfigPage />)
  await screen.findByText(content.config.provider)

  // the provider's list, not a text box — and the empty entry that sends no value stands first
  expect(await openChoice(content.agents.thinkDefault, DEFAULT_THINK)).toEqual([
    content.agents.inheritOption,
    'minimal',
    'low',
    'medium',
    'high',
  ])
  expect(shownChoice(content.agents.thinkDefault, DEFAULT_THINK)).toBe('medium')

  await chooseOption(content.agents.thinkDefault, 'high', DEFAULT_THINK)
  fireEvent.click(screen.getByRole('button', { name: content.config.save }))

  await waitFor(() => expect(sentBodies(server)).toHaveLength(1))
  expect(sentBodies(server)[0], 'the picked level travels as the text the file reads').toHaveProperty(
    'think',
    'high',
  )
})

/**
 * UC-WP-85 GIVEN 4: the level and the switch are two answers — clearing the level deletes it (UC-WP-46) and
 * leaves the switch alone, as the plugin carries both side by side.
 */
// UC-WP-85
test('defaultThinkClearsIndependentlyOfSendThinking', async () => {
  const server = stubServer({
    GET: settingsAnswer({ think: 'high', sendThinking: true }),
    PUT: settingsAnswer({ think: null, sendThinking: true }),
  })
  render(<ConfigPage />)
  await waitFor(() =>
    expect(shownChoice(content.agents.thinkDefault, DEFAULT_THINK), 'the stored level').toBe('high'),
  )

  await chooseOption(content.agents.thinkDefault, content.agents.inheritOption, DEFAULT_THINK)
  fireEvent.click(screen.getByRole('button', { name: content.config.save }))

  await waitFor(() => expect(sentBodies(server)).toHaveLength(1))
  const sent = sentBodies(server)[0]
  expect(sent, 'an emptied level is the deletion of the value').toHaveProperty('think', '')
  expect(sent, 'the switch keeps its own answer').toHaveProperty('sendThinking', 'true')
})

/**
 * UC-WP-85 GIVEN 3: a provider without a think input gets no field, only the hint — and the missing field
 * must not become a deletion of what the file holds.
 */
// UC-WP-85
test('defaultThinkIsDisabledForAProviderWithoutThinking', async () => {
  const server = stubServer({
    GET: settingsAnswer({ think: 'high' }),
    PUT: settingsAnswer({ think: 'high' }),
  })
  render(<ConfigPage />)
  await screen.findByText(content.config.provider)
  expect(shownChoice(content.agents.thinkDefault, DEFAULT_THINK)).toBe('high')

  await chooseOption(content.config.provider, 'GOOGLE_GEMINI')

  // the hint is the whole answer, in the words of the agent section — one rule, one set of words
  expect(screen.queryByLabelText(content.agents.thinkDefault)).toBeNull()
  expect(screen.getByTestId(DEFAULT_THINK + '-unsupported')).toHaveTextContent(
    content.agents.thinkUnsupported,
  )

  fireEvent.click(screen.getByRole('button', { name: content.config.save }))
  await waitFor(() => expect(sentBodies(server)).toHaveLength(1))
  expect(
    sentBodies(server)[0],
    'a field that cannot show its value must not delete it',
  ).toHaveProperty('think', 'high')
})

/**
 * R-WP-51: the three advanced values of the base level travel as text — only text has the empty state the
 * three states need — and the answer of the server is what the fields show again.
 */
// UC-WP-87
test('maxTokensAndParamsTravelAsText', async () => {
  const server = stubServer({
    GET: settingsAnswer(),
    PUT: settingsAnswer({ maxTokens: 4096, queryParams: 'a=b,c=d', headerParams: 'k=v' }),
  })
  const { container } = render(<ConfigPage />)
  await screen.findByText(content.config.provider)

  // an advanced value the file does not carry is an empty field, never the word "null"
  expect(screen.getByLabelText(content.config.maxTokens)).toHaveValue('')
  expect(container.textContent).not.toContain('null')

  fireEvent.change(screen.getByLabelText(content.config.maxTokens), { target: { value: '4096' } })
  fireEvent.change(screen.getByLabelText(content.config.queryParams), { target: { value: 'a=b,c=d' } })
  fireEvent.change(screen.getByLabelText(content.config.headerParams), { target: { value: 'k=v' } })
  fireEvent.click(screen.getByRole('button', { name: content.config.save }))

  await waitFor(() => expect(sentBodies(server)).toHaveLength(1))
  expect(sentBodies(server)[0]).toMatchObject({
    maxTokens: '4096',
    queryParams: 'a=b,c=d',
    headerParams: 'k=v',
  })

  cleanup()
  stubServer({ GET: settingsAnswer({ maxTokens: 4096, queryParams: 'a=b,c=d', headerParams: 'k=v' }) })
  render(<ConfigPage />)

  expect(await valueOf(content.config.maxTokens)).toBe('4096')
  expect(await valueOf(content.config.queryParams)).toBe('a=b,c=d')
  expect(await valueOf(content.config.headerParams)).toBe('k=v')
})

/**
 * UC-WP-87 with R-WP-16: an emptied field travels empty and is therefore deleted, while a field nobody
 * touched keeps its stored text — clearing one field is never clearing the section.
 */
// UC-WP-87
test('anEmptyAdvancedFieldClearsTheValue', async () => {
  const server = stubServer({
    GET: settingsAnswer({ maxTokens: 4096, queryParams: 'a=b', headerParams: 'k=v' }),
    PUT: settingsAnswer({ maxTokens: null, queryParams: null, headerParams: null }),
  })
  render(<ConfigPage />)
  await screen.findByDisplayValue('4096')

  fireEvent.change(screen.getByLabelText(content.config.maxTokens), { target: { value: '' } })
  fireEvent.change(screen.getByLabelText(content.config.queryParams), { target: { value: '' } })
  fireEvent.click(screen.getByRole('button', { name: content.config.save }))

  await waitFor(() => expect(sentBodies(server)).toHaveLength(1))
  const sent = sentBodies(server)[0]
  expect(sent, 'an emptied field says delete').toHaveProperty('maxTokens', '')
  expect(sent, 'an emptied field says delete').toHaveProperty('queryParams', '')
  expect(sent, 'an untouched field keeps its value').toHaveProperty('headerParams', 'k=v')
})

/**
 * UC-WP-87 GIVEN 4: a value the server refuses comes back as its message naming the field, and the page
 * keeps what was typed — no reload, nothing retyped, nothing half stored.
 */
// UC-WP-87
test('arejectedAdvancedValueKeepsWhatWasTyped', async () => {
  const server = stubServer({
    GET: settingsAnswer(),
    PUT: new Error('maxTokens must be a whole number of tokens'),
  })
  render(<ConfigPage />)
  await screen.findByText(content.config.provider)

  fireEvent.change(screen.getByLabelText(content.config.maxTokens), { target: { value: 'abc' } })
  fireEvent.change(screen.getByLabelText(content.config.queryParams), { target: { value: 'oops' } })
  fireEvent.click(screen.getByRole('button', { name: content.config.save }))

  expect(await screen.findByRole('alert')).toHaveTextContent('maxTokens')
  expect(screen.getByLabelText(content.config.maxTokens)).toHaveValue('abc')
  expect(screen.getByLabelText(content.config.queryParams)).toHaveValue('oops')
  // the refusal is the end of the exchange: the three opening reads stay three
  expect(server.calls.filter((call) => call.method === 'GET')).toHaveLength(3)
})
