import { beforeEach, afterEach, expect, test, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { Form, Input } from 'antd'

import { ApiField, ApiFormErrors } from '../../src/shared/api.error'
import { request } from '../../src/shared/api.client'
import type { ApiFieldError } from '../../src/shared/server-api'

/** One server sentence, word for word as the wire carries it — the widget never writes its own text. */
const MAX_TOKENS_REASON = 'maxTokens must be a whole number of tokens, or 0 for the provider default'
const GIT_REASON = 'fatal: repository not found'

beforeEach(() => {
  cleanup()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

/**
 * The refusal as the client really produces it: the wire body goes through `request`, so the widget is tested
 * against the error type a page holds and not against a shape written for the test.
 */
async function refusal(status: number, body: unknown): Promise<unknown> {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () =>
      new Response(JSON.stringify(body), {
        status,
        headers: { 'content-type': 'application/json' },
      }),
    ),
  )
  try {
    await request('/api/settings')
  } catch (error) {
    return error
  }
  throw new Error('the stub answered instead of refusing')
}

/**
 * The two fields of a form, wrapped the way a page wraps them. A `Form.Item` without a `Form` above it logs
 * a warning on every mount, so the harness is the real envelope rather than a bare fragment.
 */
function Harness({
  errors = [],
  error,
  fields = ['maxTokens', 'tokenWindow'],
}: {
  errors?: ApiFieldError[]
  error?: unknown
  fields?: readonly string[]
}) {
  return (
    <Form>
      <ApiField field="maxTokens" errors={errors}>
        <Input aria-label="max tokens" />
      </ApiField>
      <ApiField field="tokenWindow" errors={errors}>
        <Input aria-label="token window" />
      </ApiField>
      <ApiFormErrors error={error} fields={fields} />
    </Form>
  )
}

// UC-WP-93
test('aFieldErrorMarksExactlyItsOwnField', () => {
  render(<Harness errors={[{ field: 'maxTokens', message: MAX_TOKENS_REASON }]} />)

  expect(screen.getByTestId('field-error-maxTokens')).toHaveTextContent(MAX_TOKENS_REASON)
  const marked = document.querySelectorAll('.api-field')
  expect(marked).toHaveLength(2)
  // "red" is not measurable in jsdom except as the basis' own status class — that is the one `.ant-*`
  // read of this suite, and it lives here, at the widget (§2.4 of the plan)
  expect(marked[0]).toHaveClass('api-field-error', 'ant-form-item-has-error')
  expect(marked[1]).not.toHaveClass('api-field-error', 'ant-form-item-has-error')
  expect(screen.getAllByTestId(/^field-error-/)).toHaveLength(1)
})

// UC-WP-93
test('aFieldErrorSharesOneFormItemWithItsControl', () => {
  render(<Harness errors={[{ field: 'maxTokens', message: MAX_TOKENS_REASON }]} />)

  const help = screen.getByTestId('field-error-maxTokens')
  const wrapper = help.closest('.api-field')
  // the reason sits at the control the user just typed into — proven by the shared wrapper, never by a
  // CSS sibling selector, which jsdom cannot see
  expect(wrapper).not.toBeNull()
  expect(screen.getByLabelText('max tokens').closest('.api-field')).toBe(wrapper)
  expect(screen.getByLabelText('token window').closest('.api-field')).not.toBe(wrapper)
})

// UC-WP-93
test('anErrorWithoutFieldShowsTheServerTextAsAnAlert', async () => {
  const error = await refusal(409, { status: 409, error: 'Conflict', message: GIT_REASON })

  render(<Harness error={error} />)

  expect(screen.getByRole('alert')).toHaveTextContent(GIT_REASON)
  for (const field of document.querySelectorAll('.api-field')) {
    expect(field).not.toHaveClass('api-field-error')
  }
  expect(screen.queryAllByTestId(/^field-error-/)).toEqual([])
})

// UC-WP-93
test('anAlertNeverRepeatsAFieldError', async () => {
  const error = await refusal(400, {
    status: 400,
    error: 'Bad Request',
    message: MAX_TOKENS_REASON,
    fieldErrors: [{ field: 'maxTokens', message: MAX_TOKENS_REASON }],
  })

  render(<Harness error={error} errors={[{ field: 'maxTokens', message: MAX_TOKENS_REASON }]} />)

  // the field carries it, so the line would only say it twice
  expect(screen.queryByRole('alert')).toBeNull()
  expect(screen.getByTestId('field-error-maxTokens')).toHaveTextContent(MAX_TOKENS_REASON)
})

// UC-WP-93
test('anUnwrappedFieldErrorStillShowsTheServerText', async () => {
  const REASON = 'think is not offered by this provider'
  const error = await refusal(400, {
    status: 400,
    error: 'Bad Request',
    message: REASON,
    fieldErrors: [{ field: 'think', message: REASON }],
  })

  render(<Harness error={error} />)

  // the page wraps no `think` field — the text still reaches the screen instead of vanishing silently
  expect(screen.getByRole('alert')).toHaveTextContent(REASON)
  expect(screen.queryAllByTestId(/^field-error-/)).toEqual([])
  for (const field of document.querySelectorAll('.api-field')) {
    expect(field).not.toHaveClass('api-field-error')
  }
})

// UC-WP-93
test('anUnwrappedFieldOfAMultiFieldAnswerStillReachesTheScreen', async () => {
  const THINK_REASON = 'think is not offered by this provider'
  const error = await refusal(400, {
    status: 400,
    error: 'Bad Request',
    message: MAX_TOKENS_REASON,
    fieldErrors: [
      // the answer names two fields: one the harness wraps, one it does not
      { field: 'maxTokens', message: MAX_TOKENS_REASON },
      { field: 'think', message: THINK_REASON },
    ],
  })

  render(
    <Harness
      error={error}
      errors={[
        { field: 'maxTokens', message: MAX_TOKENS_REASON },
        { field: 'think', message: THINK_REASON },
      ]}
    />,
  )

  // the wrapped reason stays at its field, the unwrapped one reaches the screen — the first violation is
  // not the whole answer, so nothing of the second one may be dropped (R8)
  expect(screen.getAllByRole('alert')).toHaveLength(1)
  expect(screen.getByRole('alert')).toHaveTextContent(THINK_REASON)
  expect(screen.getByRole('alert').textContent).not.toContain(MAX_TOKENS_REASON)
  expect(screen.getByTestId('field-error-maxTokens')).toHaveTextContent(MAX_TOKENS_REASON)
  expect(document.querySelectorAll('.api-field-error')).toHaveLength(1)
})
