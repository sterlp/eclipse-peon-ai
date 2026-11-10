import { Input, Select } from 'antd'

import { content } from '../shared/content'
import type { ThinkInput } from './config.client'

/** The two answers of a toggle form; the third state of `ThinkValueSupport.toggleItems()` is empty. */
const TOGGLE_ITEMS = ['true', 'false']

/**
 * What a think level needs: its own `id` (the page shows the label twice by rule — R-WP-49 — so a test and a
 * screen reader need the field, not the words), the value, the provider's form and the way back to the draft.
 */
interface ThinkProps {
  id: string
  label: string
  think: ThinkInput
  value: string
  onChange: (value: string) => void
}

/**
 * The think control of one level: the form of the **base provider**, never a free text box by choice
 * (R-WP-23, R-WP-49, UC-WP-67) — the four forms the plugin's think field takes, and the one place that knows
 * them. A stored value outside the offered list stays on the screen and travels back unchanged (ADR-0064):
 * the form narrows what can be picked, it decides nothing about what the file already holds. `NONE` shows no
 * control and still sends the stored value, so a provider without think input does not delete what it cannot
 * show.
 *
 * The visible label is the host's — the agent row paints a `<label>`, the default form a `<dt>` — so the
 * control carries the accessible name only. That is what lets both levels use one control.
 */
export function ThinkControl(props: ThinkProps) {
  const { form, values } = props.think

  if (form === 'NONE') {
    return (
      <span className="hint" data-testid={props.id + '-unsupported'}>
        {content.agents.thinkUnsupported}
      </span>
    )
  }

  if (form === 'VALUES' || form === 'TOGGLE') {
    const offered = (form === 'TOGGLE' ? TOGGLE_ITEMS : values).filter((item) => item !== '')
    // a stored value the list does not carry is offered as itself — never replaced (ADR-0064)
    const choices =
      props.value !== '' && !offered.includes(props.value) ? [props.value, ...offered] : offered
    return (
      <Select
        id={props.id}
        aria-label={props.label}
        value={props.value}
        onChange={props.onChange}
        options={[
          { value: '', label: content.agents.inheritOption },
          ...choices.map((item) => ({ value: item, label: item })),
        ]}
      />
    )
  }

  // FREE and the core's UNKNOWN are one thing on the screen: the text the provider passes on
  return (
    <Input
      id={props.id}
      aria-label={props.label}
      value={props.value}
      onChange={(event) => props.onChange(event.target.value)}
    />
  )
}

/**
 * The think row of an agent section: the section's label and the shared control. The default level builds the
 * same control into its own `<dl>` row, which is why the label lives here rather than in `ThinkControl`.
 */
export function ThinkField(props: ThinkProps) {
  return (
    <div className="config-agents-field">
      {/* a slot whose provider cannot carry think has no control to point the label at: the hint is the answer */}
      <label htmlFor={props.think.form === 'NONE' ? undefined : props.id}>{props.label}</label>
      <ThinkControl {...props} />
    </div>
  )
}
