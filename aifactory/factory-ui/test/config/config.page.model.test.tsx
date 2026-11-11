import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'

import { ConfigPage } from '../../src/config/config.page'
import { content } from '../../src/shared/content'
import { arrowOf, openChoice } from '../shared/choices'
import { modelQueries, sentBodies, settingsAnswer, stubServer } from '../shared/config.stub'

/**
 * The model field of the default form, named by its id: the word „Model" belongs to every slot row too
 * (R-WP-23), so only the field itself says which level a test is holding.
 */
const MODEL_FIELD = 'settings-model'

beforeEach(() => {
  cleanup()
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

/**
 * R-WP-50, UC-WP-86 GIVEN 1: the field offers the models of the connection the form describes. Asked when the
 * field is opened and not when the page mounts — reading the settings must not call a provider, which
 * `settingsShowsMaskedConfig` guards with its three reads. The body names the draft, and the key is not in it.
 */
// UC-WP-86
// R-WP-67
// UC-WP-104
test('modelFieldListsServerModelsOnOpen', async () => {
  const server = stubServer({ GET: settingsAnswer(), MODELS: ['gpt-x', 'llama-mock'] })
  render(<ConfigPage />)
  await screen.findByText(content.config.provider)

  expect(server.calls.filter((call) => call.method === 'POST'), 'a mount asks no provider').toEqual([])

  expect(await openChoice(content.config.model, MODEL_FIELD)).toEqual(['gpt-x', 'llama-mock'])
  // R-WP-67: the same arrow the think field carries — one definition, both offer fields (R-WP-46)
  expect(
    arrowOf(content.config.model, MODEL_FIELD)?.querySelector('svg'),
    'the model field shows the arrow of its offer',
  ).toBeTruthy()

  expect(modelQueries(server), 'one opening asks once, for the drafted connection').toEqual([
    { provider: 'OPEN_AI', url: 'https://api.example.com/v1', refresh: false },
  ])
})

/**
 * R-WP-50 "kein Anhängen": the list is the server's alone. A value the connection does not know is the
 * field's business and not the list's — an invented entry would offer a model no turn could run.
 */
// UC-WP-86
test('themodellistDoesNotAppendTheTypedValue', async () => {
  stubServer({ GET: settingsAnswer({ model: 'gpt-demo' }), MODELS: ['gpt-x'] })
  render(<ConfigPage />)
  await screen.findByText(content.config.provider)

  fireEvent.change(screen.getByLabelText(content.config.model), { target: { value: 'mine-local' } })

  expect(await openChoice(content.config.model, MODEL_FIELD)).toEqual(['gpt-x'])
})

/**
 * UC-WP-86 GIVEN 4: the field takes the server's entry as soon as the typed text names one — the case is what
 * nobody controls, the id is what the provider needs. Naming nothing of a listed model rewrites nothing.
 */
// UC-WP-86
test('amatchingModelIsWrittenAsTheServerEntry', async () => {
  const server = stubServer({
    GET: settingsAnswer(),
    PUT: settingsAnswer({ model: 'gpt-x' }),
    MODELS: ['gpt-x'],
  })
  render(<ConfigPage />)
  await screen.findByText(content.config.provider)
  await openChoice(content.config.model, MODEL_FIELD)

  fireEvent.change(screen.getByLabelText(content.config.model), { target: { value: 'GPT-X' } })
  expect(screen.getByLabelText(content.config.model)).toHaveValue('gpt-x')

  fireEvent.click(screen.getByRole('button', { name: content.config.save }))
  await waitFor(() => expect(sentBodies(server)).toHaveLength(1))
  expect(sentBodies(server)[0], 'the file gets the id the provider listed').toHaveProperty(
    'model',
    'gpt-x',
  )
})

/**
 * UC-WP-86 GIVEN 3: a model the list does not carry stays exactly as typed and is saved exactly so — a local
 * or renamed model is a legitimate answer, which is why the field is a text field with an offer and not a
 * selection.
 */
// UC-WP-86
test('atypedForeignModelStaysAndIsSaved', async () => {
  const server = stubServer({
    GET: settingsAnswer(),
    PUT: settingsAnswer({ model: 'mine-local-2' }),
    MODELS: ['gpt-x'],
  })
  render(<ConfigPage />)
  await screen.findByText(content.config.provider)
  await openChoice(content.config.model, MODEL_FIELD)

  fireEvent.change(screen.getByLabelText(content.config.model), { target: { value: 'mine-local-2' } })
  expect(screen.getByLabelText(content.config.model)).toHaveValue('mine-local-2')

  fireEvent.click(screen.getByRole('button', { name: content.config.save }))
  await waitFor(() => expect(sentBodies(server)).toHaveLength(1))
  expect(sentBodies(server)[0], 'a model the list does not know travels unchanged').toHaveProperty(
    'model',
    'mine-local-2',
  )
})

/**
 * R-WP-50: the button beside the field is the way to ask again. The first ask of a connection goes without
 * `refresh`, the click goes with it — between the two sits the core's cache, keyed by the connection.
 */
// UC-WP-86
test('arefreshClickAsksTheServerAgain', async () => {
  const server = stubServer({ GET: settingsAnswer(), MODELS: ['gpt-x'] })
  render(<ConfigPage />)
  await screen.findByText(content.config.provider)

  await openChoice(content.config.model, MODEL_FIELD)
  fireEvent.click(screen.getByRole('button', { name: content.config.modelRefresh }))

  await waitFor(() => expect(modelQueries(server)).toHaveLength(2))
  expect(modelQueries(server)[0], 'the opening asks plainly').toHaveProperty('refresh', false)
  expect(modelQueries(server)[1], 'the click asks past the cache').toHaveProperty('refresh', true)
})

/**
 * UC-WP-86 GIVEN 2: a connection that answers nothing and one that answers an error leave the same screen —
 * no option, no message, nothing cleared, the field still free text. A list that cannot be had is never a
 * blocker for the one value it exists to suggest.
 */
// UC-WP-86
test('afailedListingLeavesAFreeTextField', async () => {
  const server = stubServer({
    GET: settingsAnswer(),
    PUT: settingsAnswer(),
    MODELS: new Error('the provider is offline'),
  })
  render(<ConfigPage />)
  await screen.findByText(content.config.provider)

  fireEvent.mouseDown(screen.getByLabelText(content.config.model) as Element)
  await waitFor(() => expect(modelQueries(server)).toHaveLength(1))

  expect(screen.queryAllByRole('option')).toHaveLength(0)
  expect(screen.queryByRole('alert'), 'a failed list is nothing the page reports').toBeNull()

  fireEvent.change(screen.getByLabelText(content.config.model), { target: { value: 'still-mine' } })
  expect(screen.getByLabelText(content.config.model)).toHaveValue('still-mine')
  fireEvent.click(screen.getByRole('button', { name: content.config.save }))
  await waitFor(() => expect(sentBodies(server)).toHaveLength(1))
  expect(sentBodies(server)[0]).toHaveProperty('model', 'still-mine')

  // the same silence for a connection that answers an empty list
  cleanup()
  const empty = stubServer({ GET: settingsAnswer(), MODELS: [] })
  render(<ConfigPage />)
  await screen.findByText(content.config.provider)
  fireEvent.mouseDown(screen.getByLabelText(content.config.model) as Element)
  await waitFor(() => expect(modelQueries(empty)).toHaveLength(1))

  expect(screen.queryAllByRole('option')).toHaveLength(0)
  expect(screen.queryByRole('alert')).toBeNull()
})

/**
 * R-WP-50: the list is exactly the models of the connection that was asked for. When the form drafts a
 * different connection, the answer of the old one is stale and must not act on the field either: an id
 * of the old connection names nothing the new one could run, so it cannot canonicalize anything.
 */
// UC-WP-86
test('astaleAnswerOfTheOldConnectionIsNotOffered', async () => {
  const server = stubServer({ GET: settingsAnswer(), MODELS: ['gpt-x'] })
  render(<ConfigPage />)
  await screen.findByText(content.config.provider)
  await openChoice(content.config.model, MODEL_FIELD)

  // the same question is now asked of another connection — the recorded answer belongs to the old one
  server.script.MODELS = []
  fireEvent.change(screen.getByLabelText(content.config.url), {
    target: { value: 'https://other.example/v1' },
  })

  // typed text that matched the old list is nobody's id on the new one: nothing canonicalizes it
  fireEvent.change(screen.getByLabelText(content.config.model), { target: { value: 'GPT-X' } })
  expect(screen.getByLabelText(content.config.model)).toHaveValue('GPT-X')
})

