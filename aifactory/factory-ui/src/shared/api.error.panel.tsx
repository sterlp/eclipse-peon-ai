import { useState } from 'react'
import type { ReactElement } from 'react'
import { Alert } from 'antd'
import { CloseOutlined } from '@ant-design/icons'

import { errorMessage, fieldErrorsOf } from './api.client'
import { content } from './content'

/**
 * What the page cannot place at a field, in the server's own words — the sentence this panel shows
 * (R-WP-62); `null` is the answer that keeps it closed.
 *
 * An answer that names fields belongs to those fields (R-WP-56): of its entries only the names the page does
 * not wrap with `ApiField` are left, and a page that wraps every one of them has nothing to say above
 * itself. The top-level `message` is deliberately not added to them — it is the first violation, which its
 * field already carries, so the panel would only say it twice (R8). What names no field at all is the whole
 * answer: a 409, the git output of a failed clone, a 500. Several unplaced names are the server's own
 * sentences, one line each — the answer never stops at its first violation.
 */
function unplacedMessageOf(error: unknown, fields: readonly string[]): string | null {
  if (error === null || error === undefined) {
    return null
  }
  const named = fieldErrorsOf(error)
  if (named.length === 0) {
    return errorMessage(error)
  }
  const unplaced = named
    .filter((entry) => !fields.includes(entry.field))
    .map((entry) => entry.message)
  return unplaced.length === 0 ? null : unplaced.join('\n')
}

/**
 * The one error panel of a page (R-WP-62): the basis' own `Alert`, which the page places above whatever of
 * itself scrolls, holding the server's sentence in the server's own words. One panel per page and one display
 * path — the reason of a field stays at that field (`ApiField`, R-WP-56) and never stands here as well
 * (`unplacedMessageOf` answers `null` for it).
 *
 * Closing is the panel's own decision: its X hides what stands while the page keeps its error, because a page
 * that had to hold a dismissal flag would hold one per page. A **new** answer opens it again — whether the
 * page cleared its error first, which every page does before a request, or only the text changed: the answer
 * he has not read yet may never be swallowed by the one he dismissed.
 *
 * The wrapper carries no `role` of its own: the `Alert` of the basis announces itself (`role="alert"`), and a
 * second announcing node would make "the alert of this page" two answers.
 */
export function ApiErrorPanel({
  error,
  fields = [],
  onClose,
}: {
  error: unknown
  /** the fields this page marks itself — a refusal that names only those keeps the panel closed (R-WP-56) */
  fields?: readonly string[]
  /** tells the page its error is read; the panel closes whatever the page then makes of it */
  onClose: () => void
}): ReactElement | null {
  const text = unplacedMessageOf(error, fields)
  const [seen, setSeen] = useState(text)
  const [dismissed, setDismissed] = useState(false)
  if (text !== seen) {
    // the state of a fact follows the fact in the render that met it — an effect would paint the dismissed
    // sentence for one frame first, and a refusal the user already closed would stay on the screen
    setSeen(text)
    if (text !== null) {
      setDismissed(false)
    }
  }
  if (text === null || dismissed) {
    return null
  }
  return (
    <div className="api-error-panel" data-testid="error-panel">
      {/* R-WP-62: the band above the page's scroll area; `colorError` is `--problem`, so both themes
          answer with their own red and the sheet paints no colour of its own (R-WP-39, R-WP-46) */}
      <Alert
        type="error"
        banner
        showIcon
        title={text}
        closable={{
          onClose: () => {
            setDismissed(true)
            onClose()
          },
          // the X of the basis comes unnamed — it takes our word, as the drawer's does
          closeIcon: (
            <CloseOutlined data-testid="error-panel-close" aria-label={content.common.close} />
          ),
        }}
      />
    </div>
  )
}
