import type { ReactElement, ReactNode } from 'react'
import { Form } from 'antd'

import type { ApiFieldError } from './server-api'

/**
 * The one way a server refusal reaches a form field (R-WP-56): the entry the answer names for `field`
 * becomes the basis' own field error — `validateStatus="error"` plus `help` with the server's sentence.
 * Nothing here is wrapped by a `name`, so the page keeps its controlled inputs; the wrapper brings the field
 * context and the help line and decides nothing about the value.
 *
 * Needs a `Form` above it: a `Form.Item` outside one keeps working but logs on every mount.
 *
 * The other half of the same rule belongs to the page, not the field: what no field of the answer owns
 * reaches {@link ApiErrorPanel} (R-WP-62) — one display path per half, never a second one (R-WP-56).
 */
export function ApiField({
  field,
  errors,
  children,
}: {
  field: string
  errors: ApiFieldError[]
  children: ReactNode
}): ReactElement {
  const hit = errors.find((entry) => entry.field === field)
  return (
    <Form.Item
      className={'api-field' + (hit ? ' api-field-error' : '')}
      validateStatus={hit ? 'error' : undefined}
      help={
        hit ? <span data-testid={'field-error-' + field}>{hit.message}</span> : undefined
      }
    >
      {children}
    </Form.Item>
  )
}
