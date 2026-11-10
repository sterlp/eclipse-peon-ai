import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { Form, Input } from 'antd'

import { ApiErrorPanel } from '../../src/shared/api.error.panel'
import { ApiField } from '../../src/shared/api.error'
import { request } from '../../src/shared/api.client'
import { declarations } from './sheets'
import type { ApiFieldError } from '../../src/shared/server-api'

/** One server sentence, word for word as the wire carries it — the panel never writes its own text. */
const MAX_TOKENS_REASON = 'maxTokens must be a whole number of tokens, or 0 for the provider default'
const THINK_REASON = 'think is not offered by this provider'
const GIT_REASON = 'fatal: repository not found'

beforeEach(() => {
  cleanup()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

/**
 * The refusal as the client really produces it: the wire body goes through `request`, so the panel is tested
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
 * The panel above one field of a form, as a page holds it: the panel of the page and the field a refusal can
 * mark, so both halves of R-WP-56/R-WP-62 are read in the tree they stand in. The `Form` is the real envelope
 * — a `Form.Item` without one logs on every mount.
 */
function Page({
  error,
  fields = ['maxTokens'],
  errors = [],
  onClose = () => undefined,
}: {
  error?: unknown
  fields?: readonly string[]
  errors?: ApiFieldError[]
  onClose?: () => void
}) {
  return (
    <>
      <ApiErrorPanel error={error} fields={fields} onClose={onClose} />
      <Form>
        <ApiField field="maxTokens" errors={errors}>
          <Input aria-label="max tokens" />
        </ApiField>
      </Form>
    </>
  )
}

// UC-WP-99
test('thePanelShowsTheServersOwnSentence', async () => {
  const SENTENCE = 'a turn or another action is still running\nagent Jon is COMPACTING'
  const error = await refusal(409, { status: 409, error: 'Conflict', message: SENTENCE })

  render(<Page error={error} />)

  const panel = screen.getByTestId('error-panel')
  // the server's own words, nothing added: the title node holds exactly the answer it carried, line break
  // included — a refusal the UI would paraphrase is a refusal the user cannot match with the log
  expect(panel.querySelector('.ant-alert-title')?.textContent).toBe(SENTENCE)
  // the break of a Spring answer survives to the screen, and the sheet is what keeps it visible: jsdom lays
  // out nothing, so the clause is read where it is written (no second line would show without it)
  expect(declarations('.api-error-panel .ant-alert')).toContain('white-space: pre-wrap')
})

// UC-WP-99
test('thePanelIsTheBasisAlertAndOneAlertNode', async () => {
  const error = await refusal(409, { status: 409, error: 'Conflict', message: GIT_REASON })

  render(<Page error={error} />)

  const panel = screen.getByTestId('error-panel')
  // the widget of the basis, in its banner form (R-WP-46) — an own `<p>` would be a second display path
  expect(panel.querySelector('.ant-alert'), 'the basis paints the panel').not.toBeNull()
  expect(panel.querySelector('.ant-alert-banner'), 'as the band above the page').not.toBeNull()
  // exactly one node announces it: the basis brings `role="alert"`, so the wrapper must not add a second
  expect(document.querySelectorAll('[role=alert]')).toHaveLength(1)
})

// UC-WP-99
test('itsOwnCloseButtonHidesThePanel', async () => {
  const error = await refusal(409, { status: 409, error: 'Conflict', message: GIT_REASON })

  const { rerender } = render(<Page error={error} />)

  fireEvent.click(screen.getByTestId('error-panel-close'))
  // the page still holds the very same error and never had to clear it — closing is the panel's own
  // decision, which is why every page needs no dismissal state of its own
  rerender(<Page error={error} />)

  expect(screen.queryByTestId('error-panel')).toBeNull()
  expect(document.querySelectorAll('[role=alert]')).toHaveLength(0)
})

// UC-WP-99
test('aNewFailureReplacesWhatStands', async () => {
  const first = await refusal(409, { status: 409, error: 'Conflict', message: GIT_REASON })
  const { rerender } = render(<Page error={first} />)

  const second = await refusal(500, {
    status: 500,
    error: 'Internal Server Error',
    message: 'could not read /home/me/.peon/projects',
  })
  rerender(<Page error={second} />)

  const panel = screen.getByTestId('error-panel')
  expect(panel.querySelector('.ant-alert-title')?.textContent).toBe(
    'could not read /home/me/.peon/projects',
  )
  // one panel per page: the first refusal is gone rather than stacked below it
  expect(panel.textContent).not.toContain(GIT_REASON)
  expect(document.querySelectorAll('[role=alert]')).toHaveLength(1)
})

// UC-WP-93
// UC-WP-99
test('aRefusalThatNamesOnlyWrappedFieldsKeepsThePanelClosed', async () => {
  const error = await refusal(400, {
    status: 400,
    error: 'Bad Request',
    message: MAX_TOKENS_REASON,
    fieldErrors: [{ field: 'maxTokens', message: MAX_TOKENS_REASON }],
  })

  render(<Page error={error} errors={[{ field: 'maxTokens', message: MAX_TOKENS_REASON }]} />)

  // the field carries it, so the panel would only say it twice (UC-WP-99 GIVEN 3)
  expect(screen.queryByTestId('error-panel')).toBeNull()
  expect(screen.getByTestId('field-error-maxTokens')).toHaveTextContent(MAX_TOKENS_REASON)
})

// UC-WP-93
// UC-WP-99
test('anUnwrappedFieldOfAMultiFieldAnswerReachesThePanel', async () => {
  // the answer names one field the page wraps and one it does not — the unwrapped one may not be lost
  // between the two display paths, and the wrapped one may not appear twice (R8, UC-WP-99 GIVEN 3)
  const answers = [
    [{ field: 'think', message: THINK_REASON }],
    [
      { field: 'maxTokens', message: MAX_TOKENS_REASON },
      { field: 'think', message: THINK_REASON },
    ],
  ]

  for (const fieldErrors of answers) {
    cleanup()
    const error = await refusal(400, {
      status: 400,
      error: 'Bad Request',
      message: fieldErrors[0].message,
      fieldErrors,
    })

    render(
      <Page
        error={error}
        errors={fieldErrors.filter((entry) => entry.field === 'maxTokens')}
      />,
    )

    const panel = screen.getByTestId('error-panel')
    expect(panel).toHaveTextContent(THINK_REASON)
    expect(panel.textContent).not.toContain(MAX_TOKENS_REASON)
    expect(document.querySelectorAll('[role=alert]')).toHaveLength(1)
    expect(
      screen.queryAllByTestId(/^field-error-/),
      'a field the page wraps keeps its reason at itself',
    ).toHaveLength(fieldErrors.length === 2 ? 1 : 0)
  }
})

// UC-WP-99
test('theSameFailureAfterANewInteractionShowsAgain', async () => {
  const body = { status: 409, error: 'Conflict', message: GIT_REASON }
  const first = await refusal(409, body)
  const { rerender } = render(<Page error={first} />)

  fireEvent.click(screen.getByTestId('error-panel-close'))
  // every page clears its error before the next request: the panel that was closed stays closed while
  // nothing new arrives
  rerender(<Page error={null} />)
  expect(screen.queryByTestId('error-panel')).toBeNull()

  // the same refusal of a NEW interaction is a new answer — the user asked again and has to read why
  rerender(<Page error={await refusal(409, body)} />)
  expect(screen.getByTestId('error-panel')).toHaveTextContent(GIT_REASON)
})
