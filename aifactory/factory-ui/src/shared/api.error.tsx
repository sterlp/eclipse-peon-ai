import type { ReactElement, ReactNode } from 'react'
import { Alert, Form } from 'antd'

import { errorMessage, fieldErrorsOf } from './api.client'
import type { ApiFieldError } from './server-api'

/**
 * The one way a server refusal reaches a form field (R-WP-56): the entry the answer names for `field`
 * becomes the basis' own field error — `validateStatus="error"` plus `help` with the server's sentence.
 * Nothing here is wrapped by a `name`, so the page keeps its controlled inputs; the wrapper brings the field
 * context and the help line and decides nothing about the value.
 *
 * Needs a `Form` above it: a `Form.Item` outside one keeps working but logs on every mount.
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

/**
 * The other half of the same rule (R-WP-56): what carries no field the page draws goes to the screen as an
 * `Alert` with the server's own text — a 409, the git output of a failed clone, a 500.
 *
 * It stays silent while every named field is one the page marks itself, because then the line would only
 * repeat the field. A name the page does not wrap is why `fields` exists at all: without it such a reason
 * would disappear between the two rules (R8).
 */
export function ApiFormErrors({
  error,
  fields,
}: {
  error: unknown
  fields: readonly string[]
}): ReactElement | null {
  if (error === null || error === undefined) {
    return null
  }
  const named = fieldErrorsOf(error)
  if (named.length > 0 && named.every((entry) => fields.includes(entry.field))) {
    return null
  }
  return <Alert type="error" title={errorMessage(error)} />
}
