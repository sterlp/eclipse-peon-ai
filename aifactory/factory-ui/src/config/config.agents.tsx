import { useEffect, useState } from 'react'
import { Alert, Button, Card, Checkbox, Input, Select } from 'antd'

import { errorMessage } from '../shared/api.client'
import { content } from '../shared/content'
import {
  listAgents,
  updateAgent,
  type AgentPatch,
  type AgentSettings,
} from './config.agents.client'
import type { ThinkInput } from './config.client'

/** The slot whose record carries the base think value of every other agent (R-THINK-10). */
const DEV_SLOT = 'dev'

/** The two answers of a toggle form; the third state of `ThinkValueSupport.toggleItems()` is empty. */
const TOGGLE_ITEMS = ['true', 'false']

/**
 * One slot as its row holds it: text everywhere, so empty is one state across the four fields and it
 * means "inherits the default" (UC-WP-44). The key stays write-only and never prefilled, exactly like the
 * default level's (R-WP-16). `overridden` is the checkbox — initialised from the file, never stored by
 * itself.
 */
interface Draft {
  overridden: boolean
  model: string
  url: string
  think: string
  temperature: string
  apiKey: string
}

function draftOf(slot: AgentSettings): Draft {
  return {
    overridden: slot.overridden,
    model: slot.model ?? '',
    url: slot.url ?? '',
    think: slot.think ?? '',
    temperature: slot.temperature ?? '',
    // write-only: a stored key never comes back over the wire, so the field always starts empty
    apiKey: '',
  }
}

/**
 * Every visible field travels, a cleared one as empty text — that is how the deletion of a single field
 * reaches the file (UC-WP-46 GIVEN 2). The key is dropped while empty: unchanged is all it can say.
 */
function patchOf(draft: Draft, removeKey: boolean): AgentPatch {
  return {
    overridden: draft.overridden,
    model: draft.model,
    url: draft.url,
    think: draft.think,
    temperature: draft.temperature,
    apiKey: draft.apiKey,
    ...(removeKey ? { removeApiKey: true } : {}),
  }
}

/** A field of a slot: label, text input, and the empty state named once for the whole section. */
function Field(props: {
  id: string
  label: string
  value: string
  numeric?: boolean
  onChange: (value: string) => void
}) {
  return (
    <div className="config-agents-field">
      <label htmlFor={props.id}>{props.label}</label>
      {/* text, not `type="number"`: a number input reports its garbage as empty, and empty deletes */}
      <Input
        id={props.id}
        value={props.value}
        inputMode={props.numeric ? 'numeric' : undefined}
        onChange={(event) => props.onChange(event.target.value)}
      />
    </div>
  )
}

/**
 * The think field of a slot: the form of the **base provider**, never a free text box by choice
 * (R-WP-23, UC-WP-67) — the same four forms the plugin's think field takes. A stored value outside the
 * offered list stays on the screen and travels back unchanged (ADR-0064): the form narrows what can be
 * picked, it decides nothing about what the file already holds. `NONE` shows no control and still sends
 * the stored value, so a provider without think input does not delete a value it cannot show.
 */
function ThinkField(props: {
  id: string
  label: string
  value: string
  think: ThinkInput
  onChange: (value: string) => void
}) {
  const { form, values } = props.think

  if (form === 'NONE') {
    return (
      <div className="config-agents-field">
        {/* no control, so no label target: the hint is the whole answer of a slot here */}
        <label>{props.label}</label>
        <span className="hint" data-testid={props.id + '-unsupported'}>
          {content.agents.thinkUnsupported}
        </span>
      </div>
    )
  }

  if (form === 'VALUES' || form === 'TOGGLE') {
    const offered = (form === 'TOGGLE' ? TOGGLE_ITEMS : values).filter((item) => item !== '')
    // a stored value the list does not carry is offered as itself — never replaced (ADR-0064)
    const choices =
      props.value !== '' && !offered.includes(props.value) ? [props.value, ...offered] : offered
    return (
      <div className="config-agents-field">
        <label htmlFor={props.id}>{props.label}</label>
        <Select
          id={props.id}
          value={props.value}
          onChange={props.onChange}
          options={[
            { value: '', label: content.agents.inheritOption },
            ...choices.map((item) => ({ value: item, label: item })),
          ]}
        />
      </div>
    )
  }

  // FREE and the core's UNKNOWN are one thing on the screen: the text the provider passes on
  return <Field id={props.id} label={props.label} value={props.value} onChange={props.onChange} />
}

/**
 * The agent section of the config page (R-WP-23): one row per slot the factory runs, a checkbox that is
 * the file's content and not a flag of its own, and one save per slot. An empty field inherits, the
 * unchecked box clears the slot — both are the file's states, so the page holds no opinion about them.
 * The think field takes the form of the base provider, which the page owns and hands in (`think`): the
 * slots have no provider of their own, so a second read of the provider list would only be a second
 * answer to one question.
 */
export function Agents({ think }: { think: ThinkInput }) {
  const [slots, setSlots] = useState<AgentSettings[]>([])
  const [drafts, setDrafts] = useState<Record<string, Draft>>({})
  // the removal of a key is queued per slot: the slot's own save carries it (R-WP-16, UC-WP-63)
  const [removeKey, setRemoveKey] = useState<Record<string, boolean>>({})
  const [message, setMessage] = useState<string | null>(null)
  const [saving, setSaving] = useState<string | null>(null)

  useEffect(() => {
    // mount = read the files; a slot that never overrode anything has no file, and reading creates none
    listAgents()
      .then((loaded) => {
        setSlots(loaded)
        setDrafts(Object.fromEntries(loaded.map((slot) => [slot.id, draftOf(slot)])))
      })
      .catch((error) => setMessage(errorMessage(error)))
  }, [])

  /** The one way a field of a slot changes: by its new value, whatever control reported it. */
  function edit(id: string, field: Exclude<keyof Draft, 'overridden'>) {
    return (value: string) => {
      setDrafts((current) => ({ ...current, [id]: { ...current[id], [field]: value } }))
      // typing a key while one is queued for removal is two intents — the newer one wins
      if (field === 'apiKey' && value !== '') {
        setRemoveKey((current) => ({ ...current, [id]: false }))
      }
    }
  }

  function override(id: string) {
    return (event: { target: { checked: boolean } }) => {
      const checked = event.target.checked
      setDrafts((current) => ({ ...current, [id]: { ...current[id], overridden: checked } }))
    }
  }

  async function save(id: string) {
    if (saving !== null) {
      return
    }
    setSaving(id)
    setMessage(null)
    try {
      // the answer is the slot's file, read again: the checkbox and the key status come from it, so a
      // save that stored nothing cannot show a value the file refused (same rule as the default level)
      const saved = await updateAgent(id, patchOf(drafts[id], removeKey[id] === true))
      setSlots((current) => current.map((slot) => (slot.id === id ? saved : slot)))
      setDrafts((current) => ({ ...current, [id]: draftOf(saved) }))
      setRemoveKey((current) => ({ ...current, [id]: false }))
    } catch (error) {
      // what was typed stays on the screen: the message names the field to fix, never a value
      setMessage(errorMessage(error))
    } finally {
      setSaving(null)
    }
  }

  if (slots.length === 0) {
    // before the first read there is nothing to say — an empty section would promise slots that are not there
    return null
  }

  return (
    <section className="config-agents" aria-label={content.agents.title}>
      <h2>{content.agents.title}</h2>
      {message && <Alert type="error" title={message} />}
      {slots.map((slot) => {
        const draft = drafts[slot.id]
        return (
          <Card
            size="small"
            className="config-agents-card"
            key={slot.id}
            data-testid={'agent-slot-' + slot.id}
          >
            <div className="config-agents-slot">
              <h3 className="config-agents-name">{slot.name}</h3>
              <Checkbox checked={draft.overridden} onChange={override(slot.id)}>
                {content.agents.override}
              </Checkbox>
              {draft.overridden && (
                <div className="config-agents-fields">
                  <Field
                    id={'agent-' + slot.id + '-model'}
                    label={content.agents.model}
                    value={draft.model}
                    onChange={edit(slot.id, 'model')}
                  />
                  <Field
                    id={'agent-' + slot.id + '-url'}
                    label={content.agents.url}
                    value={draft.url}
                    onChange={edit(slot.id, 'url')}
                  />
                  {/* Devs record is the base think value of every other agent (R-THINK-10, R-WP-23) */}
                  <ThinkField
                    id={'agent-' + slot.id + '-think'}
                    label={slot.id === DEV_SLOT ? content.agents.thinkDefault : content.agents.think}
                    value={draft.think}
                    think={think}
                    onChange={edit(slot.id, 'think')}
                  />
                  <Field
                    id={'agent-' + slot.id + '-temperature'}
                    label={content.agents.temperature}
                    value={draft.temperature}
                    numeric
                    onChange={edit(slot.id, 'temperature')}
                  />
                  <div className="config-agents-key">
                    <label htmlFor={'agent-' + slot.id + '-key'}>{content.config.key}</label>
                    <Input
                      id={'agent-' + slot.id + '-key'}
                      type="password"
                      autoComplete="off"
                      placeholder={content.config.unchanged}
                      value={draft.apiKey}
                      onChange={(event) => edit(slot.id, 'apiKey')(event.target.value)}
                    />
                    <span className={slot.keySet ? 'key-set' : 'key-missing'}>
                      {slot.keySet ? content.config.keySet : content.config.keyMissing}
                    </span>
                    {/* a slot's key can only come from its own file, so it is the page's to remove */}
                    {slot.keySet && (
                      <Button
                        size="small"
                        disabled={removeKey[slot.id]}
                        onClick={() => setRemoveKey((current) => ({ ...current, [slot.id]: true }))}
                      >
                        {content.config.removeKey}
                      </Button>
                    )}
                    {removeKey[slot.id] && (
                      <span className="hint">{content.config.removeKeyQueued}</span>
                    )}
                  </div>
                  <p className="hint">{content.agents.inherits}</p>
                </div>
              )}
              <Button
                type="primary"
                disabled={saving !== null}
                onClick={() => void save(slot.id)}
              >
                {saving === slot.id ? content.config.saving : content.agents.save}
              </Button>
            </div>
          </Card>
        )
      })}
    </section>
  )
}
