import { useEffect, useState } from 'react'

import { errorMessage } from '../shared/api.client'
import { content } from '../shared/content'
import {
  listAgents,
  updateAgent,
  type AgentPatch,
  type AgentSettings,
} from './config.agents.client'

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
  onChange: (event: React.ChangeEvent<HTMLInputElement>) => void
}) {
  return (
    <div className="config-agents-field">
      <label htmlFor={props.id}>{props.label}</label>
      {/* text, not `type="number"`: a number input reports its garbage as empty, and empty deletes */}
      <input
        id={props.id}
        value={props.value}
        inputMode={props.numeric ? 'numeric' : undefined}
        onChange={props.onChange}
      />
    </div>
  )
}

/**
 * The agent section of the config page (R-WP-23): one row per slot the factory runs, a checkbox that is
 * the file's content and not a flag of its own, and one save per slot. An empty field inherits, the
 * unchecked box clears the slot — both are the file's states, so the page holds no opinion about them.
 */
export function Agents() {
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

  function edit(id: string, field: Exclude<keyof Draft, 'overridden'>) {
    return (event: React.ChangeEvent<HTMLInputElement>) => {
      const value = event.target.value
      setDrafts((current) => ({ ...current, [id]: { ...current[id], [field]: value } }))
      // typing a key while one is queued for removal is two intents — the newer one wins
      if (field === 'apiKey' && value !== '') {
        setRemoveKey((current) => ({ ...current, [id]: false }))
      }
    }
  }

  function override(id: string) {
    return (event: React.ChangeEvent<HTMLInputElement>) => {
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
      {message && (
        <p className="form-error" role="alert">
          {message}
        </p>
      )}
      {slots.map((slot) => {
        const draft = drafts[slot.id]
        return (
          <div className="config-agents-slot" key={slot.id} data-testid={'agent-slot-' + slot.id}>
            <h3 className="config-agents-name">{slot.name}</h3>
            <label className="config-agents-override" htmlFor={'agent-' + slot.id + '-override'}>
              <input
                id={'agent-' + slot.id + '-override'}
                type="checkbox"
                checked={draft.overridden}
                onChange={override(slot.id)}
              />
              {content.agents.override}
            </label>
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
                <Field
                  id={'agent-' + slot.id + '-think'}
                  label={content.agents.think}
                  value={draft.think}
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
                  <input
                    id={'agent-' + slot.id + '-key'}
                    type="password"
                    autoComplete="off"
                    placeholder={content.config.unchanged}
                    value={draft.apiKey}
                    onChange={edit(slot.id, 'apiKey')}
                  />
                  <span className={slot.keySet ? 'key-set' : 'key-missing'}>
                    {slot.keySet ? content.config.keySet : content.config.keyMissing}
                  </span>
                  {/* a slot's key can only come from its own file, so it is the page's to remove */}
                  {slot.keySet && (
                    <button
                      type="button"
                      disabled={removeKey[slot.id]}
                      onClick={() => setRemoveKey((current) => ({ ...current, [slot.id]: true }))}
                    >
                      {content.config.removeKey}
                    </button>
                  )}
                  {removeKey[slot.id] && <span className="hint">{content.config.removeKeyQueued}</span>}
                </div>
                <p className="hint">{content.agents.inherits}</p>
              </div>
            )}
            <button type="button" disabled={saving !== null} onClick={() => void save(slot.id)}>
              {saving === slot.id ? content.config.saving : content.agents.save}
            </button>
          </div>
        )
      })}
    </section>
  )
}
