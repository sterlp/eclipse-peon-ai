import { beforeEach, expect, test } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { Form, Input } from 'antd'

import { ApiField } from '../../src/shared/api.error'
import type { ApiFieldError } from '../../src/shared/server-api'

/** One server sentence, word for word as the wire carries it — the widget never writes its own text. */
const MAX_TOKENS_REASON = 'maxTokens must be a whole number of tokens, or 0 for the provider default'

beforeEach(() => {
  cleanup()
})

/**
 * The two fields of a form, wrapped the way a page wraps them. A `Form.Item` without a `Form` above it logs
 * a warning on every mount, so the harness is the real envelope rather than a bare fragment. What no field
 * owns is the page panel's answer and tested in `api.error.panel.test.tsx` (R-WP-62).
 */
function Harness({ errors = [] }: { errors?: ApiFieldError[] }) {
  return (
    <Form>
      <ApiField field="maxTokens" errors={errors}>
        <Input aria-label="max tokens" />
      </ApiField>
      <ApiField field="tokenWindow" errors={errors}>
        <Input aria-label="token window" />
      </ApiField>
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

// the fieldless half of both cases — the panel of the page, its words, its X — is `api.error.panel.test.tsx`
// (R-WP-62, UC-WP-99); what a field owns stays here, at the field (R-WP-56, UC-WP-93).
