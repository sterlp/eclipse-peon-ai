import { useState } from 'react'
import { AutoComplete, Button } from 'antd'

import { content } from '../shared/content'

/**
 * The field's text is the server's entry as soon as it names one, whatever case was typed (UC-WP-86 GIVEN 4).
 * Naming nothing of a listed model rewrites nothing: a model the connection does not list stays exactly as
 * typed, because the field is the answer and the list only an offer (R-WP-50 "kein Anhängen").
 */
function canonicalModel(value: string, ids: string[]): string {
  const typed = value.trim().toLowerCase()
  if (typed === '') {
    return value
  }
  return ids.find((id) => id.toLowerCase() === typed) ?? value
}

/**
 * The model field of both config levels (R-WP-50, UC-WP-86): a text field that also offers the models the
 * server lists for the connection the draft describes. One component for both levels because both decide the
 * same question — which model a turn of this connection runs with — and because the plugin's model tab offers
 * exactly one such field per level too.
 *
 * The list is asked when the field is opened and not when the page mounts: a page that is only read has no
 * business calling a provider. A new `identity` — the draft of the provider or URL it was built from — re-arms
 * the next opening, and answers are kept per identity, which is also what drops one that arrives after the
 * connection moved on. The button beside the field asks past the cache.
 *
 * A list that fails or stays empty is silent by rule (UC-WP-86 GIVEN 2): no message, nothing cleared, no
 * option invented — the field simply keeps being what it always was, a text field. That silence is the
 * difference to every other error of the page, which is reported.
 */
export function ModelField(props: {
  id: string
  label: string
  value: string
  /** The connection the list belongs to: provider and URL as the draft holds them. */
  identity: string
  load: (force: boolean) => Promise<string[]>
  onChange: (value: string) => void
}) {
  const [listed, setListed] = useState<{ identity: string; ids: string[] } | null>(null)
  const [loading, setLoading] = useState(false)
  const ids = listed?.identity === props.identity ? listed.ids : []

  function ask(force: boolean) {
    const asked = props.identity
    setLoading(true)
    const settled = (fetched: string[]) => {
      // keyed by the connection asked for, so an answer of a connection the form left shows nowhere
      setListed({ identity: asked, ids: fetched })
      setLoading(false)
    }
    props.load(force).then(settled, () => settled([]))
  }

  return (
    <div className="config-model-row">
      <AutoComplete
        id={props.id}
        aria-label={props.label}
        value={props.value}
        options={ids.map((id) => ({ value: id }))}
        // no appending: the list is the server's, whole (R-WP-50). The basis offers an AutoComplete unfiltered
        // and with no pre-selected entry — both are the point here, so neither is asked for
        notFoundContent={null}
        onChange={(value) => props.onChange(canonicalModel(value ?? '', ids))}
        onOpenChange={(open) => {
          if (open && listed?.identity !== props.identity) {
            ask(false)
          }
        }}
      />
      <Button size="small" loading={loading} onClick={() => ask(true)}>
        {content.config.modelRefresh}
      </Button>
    </div>
  )
}
