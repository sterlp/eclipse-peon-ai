import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'

import { ConfigPage } from '../../src/config/config.page'
import { content } from '../../src/shared/content'
import { chooseOption, openChoice, shownChoice } from '../shared/choices'
import { sentBodies, settingsAnswer, stubServer, valueOf } from '../shared/config.stub'

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
