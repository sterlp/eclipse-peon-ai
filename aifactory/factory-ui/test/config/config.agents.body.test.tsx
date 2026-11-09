import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'

import { content } from '../../src/shared/content'
import {
  agent,
  previewPaths,
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

/**
 * UC-WP-88 / D8: the extra body is the provider's capability, not the slot's opinion — without it the
 * field is not on the screen. A field that is not there must never answer empty either: the stored value
 * travels with the save (the think rule of a NONE provider), or hiding it would delete it.
 */
// UC-WP-88
test('agentExtraBodyFollowsTheProviderCapability', async () => {
  const stored = agent({ id: 'po', name: 'Jon', overridden: true, extraBody: '{"a":1}' })
  const server = stubServer({ slots: [stored], saved: stored })

  renderAgents(undefined, { extraBodySupported: false })

  const slot = await waitFor(() => row('po'))
  expect(within(slot).queryByLabelText(content.agents.extraBody)).toBeNull()

  fireEvent.click(within(slot).getByRole('button', { name: content.agents.save }))
  await waitFor(() => expect(server.calls.some((call) => call.method === 'PUT')).toBe(true))
  expect(server.body('PUT')).toMatchObject({ overridden: true, extraBody: '{"a":1}' })
})

/** UC-WP-88: both new fields belong to the slot's save and come back from its answer. */
// UC-WP-88
test('agentExtraBodyAndToolsSaveWithTheSlot', async () => {
  const server = stubServer({
    slots: [agent({ overridden: true })],
    saved: agent({ overridden: true, extraBody: '{"b":2}', tools: 'read, grep' }),
  })

  renderAgents()

  const slot = await waitFor(() => row())
  fireEvent.change(within(slot).getByLabelText(content.agents.extraBody), {
    target: { value: '{"b":2}' },
  })
  fireEvent.change(within(slot).getByLabelText(content.agents.tools), {
    target: { value: 'read, grep' },
  })
  fireEvent.click(within(slot).getByRole('button', { name: content.agents.save }))

  await waitFor(() => expect(server.calls.some((call) => call.method === 'PUT')).toBe(true))
  expect(server.body('PUT')).toMatchObject({
    overridden: true,
    extraBody: '{"b":2}',
    tools: 'read, grep',
  })
  // the row is rebuilt from the answer, so what the file holds is what the next open shows
  await waitFor(() =>
    expect(
      (within(row()).getByLabelText(content.agents.extraBody) as HTMLTextAreaElement).value,
    ).toBe('{"b":2}'),
  )
  expect((within(row()).getByLabelText(content.agents.tools) as HTMLInputElement).value).toBe(
    'read, grep',
  )
})

/** UC-WP-88 / R-WP-16: an emptied tools field is its deletion — and empty means every tool (D9). */
// UC-WP-88
test('clearingTheToolsFieldClearsTheKey', async () => {
  const server = stubServer({
    slots: [agent({ overridden: true, tools: 'read' })],
    saved: agent({ overridden: true }),
  })

  renderAgents()

  const slot = await waitFor(() => row())
  expect((within(slot).getByLabelText(content.agents.tools) as HTMLInputElement).value).toBe('read')

  fireEvent.change(within(slot).getByLabelText(content.agents.tools), { target: { value: '' } })
  fireEvent.click(within(slot).getByRole('button', { name: content.agents.save }))

  await waitFor(() => expect(server.calls.some((call) => call.method === 'PUT')).toBe(true))
  expect(server.body('PUT')).toMatchObject({ overridden: true, tools: '' })
})

/**
 * UC-WP-88: the preview names what runs with the typed entry — a read of the typed value, answered as
 * tags and their number. It never saves and never warns: an offer is not an answer.
 */
// UC-WP-88
test('thetoolsPreviewNamesWhatRuns', async () => {
  const server = stubServer({
    slots: [agent({ overridden: true })],
    tools: ['diskReadFile', 'shellRunCommand', 'searchAgent'],
  })

  renderAgents()

  const slot = await waitFor(() => row())
  fireEvent.change(within(slot).getByLabelText(content.agents.tools), {
    target: { value: 'diskReadFile, shellRunCommand' },
  })
  fireEvent.click(within(slot).getByRole('button', { name: content.agents.preview }))

  await waitFor(() => expect(previewPaths(server)).toHaveLength(1))
  expect(previewPaths(server)[0]).toBe(
    '/api/settings/agents/dev/tools?allowlist=' + encodeURIComponent('diskReadFile, shellRunCommand'),
  )

  const preview = within(row()).getByTestId('agent-dev-tools-preview')
  expect(within(preview).getByText('diskReadFile')).toBeTruthy()
  expect(within(preview).getByText('shellRunCommand')).toBeTruthy()
  expect(preview.textContent).toContain('2')

  // an offer: nothing saved itself on the way, and nothing warned either
  expect(server.calls.filter((call) => call.method === 'PUT')).toEqual([])
  expect(screen.queryByRole('alert')).toBeNull()
})

/** UC-WP-88: the preview follows the typed value, not the saved one — the second ask carries the new entry. */
// UC-WP-88
test('thetoolsPreviewFollowsTheTypedValue', async () => {
  const server = stubServer({
    slots: [agent({ overridden: true, tools: 'diskReadFile' })],
    tools: ['diskReadFile', 'shellRunCommand', 'searchAgent'],
  })

  renderAgents()

  const slot = await waitFor(() => row())
  fireEvent.click(within(slot).getByRole('button', { name: content.agents.preview }))
  await waitFor(() => expect(previewPaths(server)).toHaveLength(1))
  expect(previewPaths(server)[0]).toBe('/api/settings/agents/dev/tools?allowlist=diskReadFile')

  fireEvent.change(within(row()).getByLabelText(content.agents.tools), {
    target: { value: 'searchAgent' },
  })
  fireEvent.click(within(row()).getByRole('button', { name: content.agents.preview }))
  await waitFor(() => expect(previewPaths(server)).toHaveLength(2))
  expect(previewPaths(server)[1]).toBe('/api/settings/agents/dev/tools?allowlist=searchAgent')

  const preview = within(row()).getByTestId('agent-dev-tools-preview')
  await waitFor(() => expect(within(preview).queryByText('diskReadFile')).toBeNull())
  expect(within(preview).getByText('searchAgent')).toBeTruthy()
})
