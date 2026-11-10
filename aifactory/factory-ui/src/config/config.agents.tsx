import { useEffect, useState } from 'react'
import { Alert, Button, Card, Checkbox, Form, Input, Tag } from 'antd'

import { errorMessage, fieldErrorsOf } from '../shared/api.client'
import { ApiField, ApiFormErrors } from '../shared/api.error'
import { content } from '../shared/content'
import type { AgentSettingsDto } from '../shared/server-api'
import { listAgents, previewTools, updateAgent, type AgentPatch } from './config.agents.client'
import type { ThinkInput } from './config.client'
import { ModelField } from './config.model'
import { ThinkField } from './config.think'

/** The slot whose record carries the base think value of every other agent (R-THINK-10). */
const DEV_SLOT = 'dev'

/** The slot that runs no project tools — so it has neither the allowlist field nor its preview (D6). */
const COMPACT_SLOT = 'compact'

/**
 * The slot fields this section wraps with `ApiField` — the ones the server answers with a rule of its own
 * (`Wire*` constraints, R-WP-55). A name the answer carries besides these still reaches the screen as a
 * line, so a rule added on the server cannot be lost between the two display paths (R8).
 */
const SLOT_ERROR_FIELDS = ['temperature', 'extraBody', 'apiKey']

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
  extraBody: string
  tools: string
  apiKey: string
}

function draftOf(slot: AgentSettingsDto): Draft {
  return {
    overridden: slot.overridden,
    model: slot.model ?? '',
    url: slot.url ?? '',
    think: slot.think ?? '',
    temperature: slot.temperature ?? '',
    // R-WP-52: even a field the screen does not show (D8, compact) starts from the stored value —
    // an invisible field must never answer empty, that would delete what the file holds
    extraBody: slot.extraBody ?? '',
    tools: slot.tools ?? '',
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
    extraBody: draft.extraBody,
    tools: draft.tools,
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
 * The agent section of the config page (R-WP-23): one row per slot the factory runs, a checkbox that is
 * the file's content and not a flag of its own, and one save per slot. An empty field inherits, the
 * unchecked box clears the slot — both are the file's states, so the page holds no opinion about them.
 * The think field takes the form of the base provider, which the page owns and hands in (`think`): the
 * slots have no provider of their own, so a second read of the provider list would only be a second
 * answer to one question. The model lists follow the same rule (`connection`, `loadModels`): a slot's
 * connection is the base one plus whatever its own URL field drafts, so only the page can name it. The
 * extra body follows the same rule in reverse (`extraBodySupported`): it is the base provider's capacity,
 * and without it the field is not on the screen (R-WP-52, D8).
 */
export function Agents({
  think,
  connection,
  loadModels,
  extraBodySupported,
  reloadToken,
}: {
  think: ThinkInput
  /** The base connection the slots inherit from: provider and base URL as the form holds them (R-WP-50). */
  connection: string
  loadModels: (slotId: string, slotUrl: string, force: boolean) => Promise<string[]>
  /** R-WP-52: whether the form's provider can carry an extra body at all. */
  extraBodySupported: boolean
  /** Change it to read the files again: the dev slot holds the default think value, so a save on the
      default side changes a slot draft's source (R-WP-49). */
  reloadToken: number
}) {
  const [slots, setSlots] = useState<AgentSettingsDto[]>([])
  const [drafts, setDrafts] = useState<Record<string, Draft>>({})
  // the removal of a key is queued per slot: the slot's own save carries it (R-WP-16, UC-WP-63)
  const [removeKey, setRemoveKey] = useState<Record<string, boolean>>({})
  // the preview is one answer, shown on the slot it was asked for (UC-WP-88) — the entry it read stays put
  const [preview, setPreview] = useState<{ slot: string; tools: string[] } | null>(null)
  const [previewBusy, setPreviewBusy] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [saving, setSaving] = useState<string | null>(null)
  // one refusal per slot, under the slot's own id: a card answers its own save and keeps its own reason
  const [saveErrors, setSaveErrors] = useState<Record<string, unknown>>({})

  useEffect(() => {
    // a read of the files; a slot that never overrode anything has no file, and reading creates none.
    // The token re-reads after a save on the default side — that save can write this section's dev file
    listAgents()
      .then((loaded) => {
        setSlots(loaded)
        setDrafts(Object.fromEntries(loaded.map((slot) => [slot.id, draftOf(slot)])))
      })
      .catch((error) => setMessage(errorMessage(error)))
  }, [reloadToken])

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
    setSaveErrors((current) => ({ ...current, [id]: null }))
    try {
      // the answer is the slot's file, read again: the checkbox and the key status come from it, so a
      // save that stored nothing cannot show a value the file refused (same rule as the default level)
      const saved = await updateAgent(id, patchOf(drafts[id], removeKey[id] === true))
      setSlots((current) => current.map((slot) => (slot.id === id ? saved : slot)))
      setDrafts((current) => ({ ...current, [id]: draftOf(saved) }))
      setRemoveKey((current) => ({ ...current, [id]: false }))
    } catch (error) {
      // what was typed stays on the screen: the message names the field to fix, never a value
      setSaveErrors((current) => ({ ...current, [id]: error }))
    } finally {
      setSaving(null)
    }
  }

  /**
   * The tools that would run with the entry as typed (UC-WP-88): a read, never a save. An offer that
   * cannot be made says nothing — silent like the model list, with nothing stored to roll back.
   */
  async function runPreview(id: string) {
    if (previewBusy !== null) {
      return
    }
    setPreviewBusy(id)
    try {
      setPreview({ slot: id, tools: await previewTools(id, drafts[id].tools) })
    } catch {
      setPreview(null)
    } finally {
      setPreviewBusy(null)
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
      {/* a refusal whose field no card draws speaks here, with the server's own text — a card that marks its
          own field says nothing twice (R-WP-56) */}
      {Object.entries(saveErrors).map(([slot, error]) => (
        <ApiFormErrors key={slot} error={error} fields={SLOT_ERROR_FIELDS} />
      ))}
      {slots.map((slot) => {
        const draft = drafts[slot.id]
        const errors = fieldErrorsOf(saveErrors[slot.id])
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
                /* the field block of one slot, without a submit of its own: saving stays the card's click, so
                   an enter in a field cannot write a file (D7) */
                <Form className="config-agents-fields">
                  {/* R-WP-50: the same field the default level has — a slot lists the models of the
                      connection its turn will use, which is the base one unless this field drafts another. */}
                  <div className="config-agents-field">
                    <label htmlFor={'agent-' + slot.id + '-model'}>{content.agents.model}</label>
                    <ModelField
                      id={'agent-' + slot.id + '-model'}
                      label={content.agents.model}
                      value={draft.model}
                      identity={connection + ' ' + draft.url}
                      load={(force) => loadModels(slot.id, draft.url, force)}
                      onChange={edit(slot.id, 'model')}
                    />
                  </div>
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
                  <ApiField field="temperature" errors={errors}>
                    <Field
                      id={'agent-' + slot.id + '-temperature'}
                      label={content.agents.temperature}
                      value={draft.temperature}
                      numeric
                      onChange={edit(slot.id, 'temperature')}
                    />
                  </ApiField>
                  {/*
                    R-WP-52: the provider's body, only where a provider can carry it — and with the
                    capability gone the draft still carries the stored value into the save (D8), because
                    a field the screen does not show must never answer empty. Compact runs no project
                    tools — neither the tools field nor its preview (D6) — but the core sends its extra
                    body like every other slot's (R-WP-23: same handling), so the field is compact's too.
                  */}
                  {extraBodySupported && (
                    <div className="config-agents-field">
                      <label htmlFor={'agent-' + slot.id + '-extra-body'}>
                        {content.agents.extraBody}
                      </label>
                      <ApiField field="extraBody" errors={errors}>
                        <Input.TextArea
                          id={'agent-' + slot.id + '-extra-body'}
                          rows={2}
                          value={draft.extraBody}
                          onChange={(event) => edit(slot.id, 'extraBody')(event.target.value)}
                        />
                      </ApiField>
                    </div>
                  )}
                  {slot.id !== COMPACT_SLOT && (
                    <div className="config-agents-field">
                      <label htmlFor={'agent-' + slot.id + '-tools'}>{content.agents.tools}</label>
                      <div className="config-agents-tools">
                        <div className="config-agents-tools-row">
                          <Input
                            id={'agent-' + slot.id + '-tools'}
                            placeholder={content.agents.toolsPlaceholder}
                            value={draft.tools}
                            onChange={(event) => edit(slot.id, 'tools')(event.target.value)}
                          />
                          <Button
                            size="small"
                            loading={previewBusy === slot.id}
                            onClick={() => void runPreview(slot.id)}
                          >
                            {content.agents.preview}
                          </Button>
                        </div>
                        <span className="hint">{content.agents.previewHint}</span>
                        {preview?.slot === slot.id &&
                          (preview.tools.length === 0 ? (
                            <span
                              className="hint"
                              data-testid={'agent-' + slot.id + '-tools-preview'}
                            >
                              {content.agents.previewEmpty}
                            </span>
                          ) : (
                            <div
                              className="config-agents-preview"
                              data-testid={'agent-' + slot.id + '-tools-preview'}
                            >
                              <span className="hint">
                                {content.agents.previewCount(preview.tools.length)}
                              </span>
                              {preview.tools.map((name) => (
                                <Tag key={name}>{name}</Tag>
                              ))}
                            </div>
                          ))}
                      </div>
                    </div>
                  )}
                  <div className="config-agents-key">
                    <label htmlFor={'agent-' + slot.id + '-key'}>{content.config.key}</label>
                    <ApiField field="apiKey" errors={errors}>
                      <Input
                        id={'agent-' + slot.id + '-key'}
                        type="password"
                        autoComplete="off"
                        placeholder={content.config.unchanged}
                        value={draft.apiKey}
                        onChange={(event) => edit(slot.id, 'apiKey')(event.target.value)}
                      />
                    </ApiField>
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
                </Form>
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
