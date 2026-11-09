import { afterEach, expect, test, vi } from 'vitest'

import { fieldErrorsOf, request } from '../../src/shared/api.client'

const REASON = 'maxTokens must be a whole number of tokens, or 0 for the provider default'

afterEach(() => {
  vi.unstubAllGlobals()
})

function stubAnswer(response: Response): void {
  vi.stubGlobal('fetch', vi.fn(async () => response))
}

/** The refused call, answered with the error the page would hold. */
async function refused(): Promise<unknown> {
  try {
    await request('/api/settings')
  } catch (error) {
    return error
  }
  throw new Error('the stub answered instead of refusing')
}

// UC-WP-93
test('fieldErrorsComeFromTheServerErrorBody', async () => {
  stubAnswer(
    new Response(
      JSON.stringify({
        status: 400,
        error: 'Bad Request',
        message: REASON,
        fieldErrors: [{ field: 'maxTokens', message: REASON }],
      }),
      { status: 400, headers: { 'content-type': 'application/json' } },
    ),
  )

  expect(fieldErrorsOf(await refused())).toEqual([{ field: 'maxTokens', message: REASON }])
})

// UC-WP-93
test('aTransportErrorHasNoFieldErrors', async () => {
  // the server says `fieldErrors: null` when it names no field, and a body that is not our contract
  // (a 500 from in front of the app) never names one either — both read as "no field"
  stubAnswer(
    new Response(JSON.stringify({ status: 409, error: 'Conflict', message: 'clone is busy' }), {
      status: 409,
      headers: { 'content-type': 'application/json' },
    }),
  )
  expect(fieldErrorsOf(await refused())).toEqual([])

  stubAnswer(new Response('boom', { status: 500, headers: { 'content-type': 'text/plain' } }))
  expect(fieldErrorsOf(await refused())).toEqual([])

  expect(fieldErrorsOf(new Error('offline'))).toEqual([])
  expect(fieldErrorsOf(undefined)).toEqual([])
})
