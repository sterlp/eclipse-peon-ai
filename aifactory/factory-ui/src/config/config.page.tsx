import { useEffect, useState } from 'react'
import { Alert, Button, Card, Form, Input, Select } from 'antd'

import { errorMessage, fieldErrorsOf } from '../shared/api.client'
import { ApiField, ApiFormErrors } from '../shared/api.error'
import { content } from '../shared/content'
import type { KeySource, ProviderDefaultsDto, SettingsDto, SettingsUpdateRequest } from '../shared/server-api'
import { Agents } from './config.agents'
import { getSettings, listModels, listProviders, thinkInputOf, updateSettings } from './config.client'
import { ModelField } from './config.model'
import { ThinkControl } from './config.think'

const SOURCE_LABEL: Record<KeySource, string> = {
  FILE: content.config.source.FILE,
  ENV: content.config.source.ENV,
  NONE: content.config.source.NONE,
}

/** The values of the think field — internal, never on the screen: the labels come from the catalog. */
const YES = 'yes'
const NO = 'no'

/** The same decision on the wire: the file holds text, the core reads it back as a boolean. */
const TRUE = 'true'
const FALSE = 'false'

/**
 * The fields this form wraps with `ApiField` — exactly the ones the server answers with a rule of its own
 * (`Wire*` constraints, R-WP-55). A name the answer carries besides these still reaches the screen as a
 * line, so a rule added on the server cannot be lost between the two display paths (R8).
 */
const SETTINGS_ERROR_FIELDS = [
  'provider',
  'tokenWindow',
  'timeoutSeconds',
  'sendThinking',
  'maxTokens',
  'queryParams',
  'headerParams',
  'apiKey',
]

/**
 * The form holds every editable value as text, so "empty" is one concept across text and number fields —
 * and empty is a value of its own: it says the user cleared the field, which deletes it (R-WP-16
 * "geleert = gelöscht", UC-WP-46). The key field is the exception: write-only, never prefilled.
 */
interface Draft {
  provider: string
  model: string
  think: string
  url: string
  apiKey: string
  tokenWindow: string
  timeoutSeconds: string
  sendThinking: string
  maxTokens: string
  queryParams: string
  headerParams: string
}

function draftOf(settings: SettingsDto): Draft {
  return {
    provider: settings.provider,
    model: settings.model ?? '',
    // R-WP-49: the Dev slot's think value, shown at the base level — one value, no second store
    think: settings.think ?? '',
    url: settings.url ?? '',
    // write-only: a stored key never comes back over the wire, so the field always starts empty
    apiKey: '',
    tokenWindow: String(settings.tokenWindow),
    timeoutSeconds: String(settings.timeoutSeconds),
    sendThinking: settings.sendThinking ? YES : NO,
    // an unset advanced value is an empty field: "unset" and 0 are two different answers (R-WP-51)
    maxTokens: String(settings.maxTokens ?? ''),
    queryParams: settings.queryParams ?? '',
    headerParams: settings.headerParams ?? '',
  }
}

/** Every field travels, cleared ones as empty text — that is how a deletion reaches the file. */
function patchOf(draft: Draft, removeKey: boolean): SettingsUpdateRequest {
  return {
    provider: draft.provider,
    model: draft.model,
    url: draft.url,
    // an empty key field is dropped by the client: unchanged is the only thing it can say
    apiKey: draft.apiKey,
    tokenWindow: draft.tokenWindow,
    timeoutSeconds: draft.timeoutSeconds,
    sendThinking: draft.sendThinking === YES ? TRUE : FALSE,
    think: draft.think,
    maxTokens: draft.maxTokens,
    queryParams: draft.queryParams,
    headerParams: draft.headerParams,
    ...(removeKey ? { removeApiKey: true } : {}),
  }
}

/**
 * The factory's config (R-WP-16): the values of the base level, the key <em>status</em> and where a value
 * came from.
 * The form behaves like the provider dialog of the plugin (UC-WP-62) — a provider change updates the
 * fields that depend on it at once — and it can remove a key (UC-WP-63).
 */
export function ConfigPage() {
  const [settings, setSettings] = useState<SettingsDto | null>(null)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [providers, setProviders] = useState<ProviderDefaultsDto[]>([])
  const [removeKey, setRemoveKey] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  // R-WP-49: the dev slot's file holds the default think value — after a save the section reads again
  const [agentsReload, setAgentsReload] = useState(0)

  useEffect(() => {
    // mount = read the config and the provider defaults, two reads of external systems. The form does
    // not wait for the list: a page that cannot list providers still shows what the file holds.
    getSettings()
      .then((loaded) => {
        setSettings(loaded)
        setDraft(draftOf(loaded))
      })
      .catch((error) => setMessage(errorMessage(error)))
    listProviders()
      .then(setProviders)
      .catch((error) => setMessage(errorMessage(error)))
  }, [])

  /** The one way a field changes: by its new value, whatever control reported it. */
  function edit(field: keyof Draft) {
    return (value: string) => {
      setDraft((current) => (current ? { ...current, [field]: value } : current))
      // typing a key while one is queued for removal is two intents — the newer one wins
      if (field === 'apiKey' && value !== '') {
        setRemoveKey(false)
      }
    }
  }

  /**
   * R-WP-16 "Dynamische Felder wie im Plugin", without a save in between: the URL becomes the core's
   * default for the chosen provider, and the model is *cleared* rather than carried over — a model
   * belongs to a provider, and inventing a default for it is not the page's (Q2).
   */
  function editProvider(name: string) {
    const chosen = providers.find((entry) => entry.name === name)
    setDraft((current) =>
      current
        ? {
            ...current,
            provider: name,
            url: chosen ? (chosen.defaultUrl ?? '') : current.url,
            model: '',
          }
        : current,
    )
  }

  async function save() {
    if (busy || draft === null) {
      return
    }
    setBusy(true)
    setSaveError(null)
    try {
      // the answer is the stored state, read back masked — the form is refilled from it, not from the
      // input, which is also what makes the key status fresh after every save (UC-WP-63)
      const saved = await updateSettings(patchOf(draft, removeKey))
      setSettings(saved)
      setDraft(draftOf(saved))
      setRemoveKey(false)
      // the save can have written the dev slot's file (think, R-WP-49) — the section may not keep a
      // draft of a value the page has just changed, its next save would write the old one back
      setAgentsReload((current) => current + 1)
    } catch (error) {
      // the typed values stay: the message names the field to fix, nothing the user wrote is lost
      setSaveError(error)
    } finally {
      setBusy(false)
    }
  }

  // the provider the form currently names — the source of both dynamic answers below
  const chosen = draft ? providers.find((entry) => entry.name === draft.provider) : undefined
  // Q3: the think value stays a boolean; a provider that cannot carry it says so and gets no field to try
  const thinkUnsupported = chosen !== undefined && !chosen.thinkSupported
  // one provider answer builds both think fields on this page — the form's own and the slots' (UC-WP-67)
  const thinkInput = thinkInputOf(chosen)

  /**
   * The model list of one slot (R-WP-50): the slot's own fields are the section's, the connection is the
   * form's — until the config has arrived there is no connection to name, and a list that cannot be named is
   * the empty answer the field already knows how to stay silent about (UC-WP-86 GIVEN 2).
   */
  function loadSlotModels(slotId: string, slotUrl: string, force: boolean): Promise<string[]> {
    if (draft === null) {
      return Promise.resolve([])
    }
    return listModels({
      provider: draft.provider,
      url: draft.url,
      agent: slotId,
      agentUrl: slotUrl,
      refresh: force,
    })
  }

  // a stored provider the core list does not offer still has to show what the file holds
  const offered = providers.map((entry) => entry.name)
  const providerOptions = (
    draft !== null && !offered.includes(draft.provider) ? [draft.provider, ...offered] : offered
  ).map((name) => ({ value: name, label: name }))

  // the fields the last refusal named — read off the error, never kept beside it as a second state
  const errors = fieldErrorsOf(saveError)

  return (
    <section className="view" aria-label={content.config.title}>
      <h1>{content.config.title}</h1>
      {message && <Alert type="error" title={message} />}
      {settings && draft && (
        <Card size="small" className="settings-card">
          <Form className="settings-form" onFinish={() => void save()}>
            {/* what carries no field this form draws speaks here, with the server's own text (R-WP-56) */}
            <ApiFormErrors error={saveError} fields={SETTINGS_ERROR_FIELDS} />
            <dl className="settings">
              <div>
                <dt>{content.config.provider}</dt>
                <dd>
                  <ApiField field="provider" errors={errors}>
                    <Select
                      id="settings-provider"
                      aria-label={content.config.provider}
                      value={draft.provider}
                      onChange={editProvider}
                      options={providerOptions}
                    />
                  </ApiField>
                </dd>
              </div>
              <div>
                <dt>{content.config.model}</dt>
                <dd>
                  {/* R-WP-50: the field lists the models of the connection this form describes — of the
                      draft, not of the file, because that is the connection the next save will build. */}
                  <ModelField
                    id="settings-model"
                    label={content.config.model}
                    value={draft.model}
                    identity={draft.provider + ' ' + draft.url}
                    load={(force) =>
                      listModels({
                        provider: draft.provider,
                        url: draft.url,
                        refresh: force,
                      })
                    }
                    onChange={edit('model')}
                  />
                </dd>
              </div>
              {/*
                R-WP-49: the Dev slot's think value at the base level — the same field, read and written
                through the slot's file, so neither place holds a copy. The words are the section's (D5).
              */}
              <div>
                <dt>{content.agents.thinkDefault}</dt>
                <dd>
                  <ThinkControl
                    id="settings-think"
                    label={content.agents.thinkDefault}
                    think={thinkInput}
                    value={draft.think}
                    onChange={edit('think')}
                  />
                </dd>
              </div>
              <div>
                <dt>{content.config.url}</dt>
                <dd>
                  <Input
                    aria-label={content.config.url}
                    value={draft.url}
                    onChange={(event) => edit('url')(event.target.value)}
                  />
                </dd>
              </div>
              {/*
                Text fields, not `type="number"`: a number input reports its own garbage as empty, and empty
                now means "delete" — the browser would turn a typo into a deletion. As text the value reaches
                the server, which refuses it and names the field.
              */}
              <div>
                <dt>{content.config.tokenWindow}</dt>
                <dd>
                  <ApiField field="tokenWindow" errors={errors}>
                    <Input
                      inputMode="numeric"
                      aria-label={content.config.tokenWindow}
                      value={draft.tokenWindow}
                      onChange={(event) => edit('tokenWindow')(event.target.value)}
                    />
                  </ApiField>
                </dd>
              </div>
              <div>
                <dt>{content.config.timeout}</dt>
                <dd>
                  <ApiField field="timeoutSeconds" errors={errors}>
                    <Input
                      inputMode="numeric"
                      aria-label={content.config.timeout}
                      value={draft.timeoutSeconds}
                      onChange={(event) => edit('timeoutSeconds')(event.target.value)}
                    />
                  </ApiField>
                  <span className="hint">{content.common.seconds}</span>
                </dd>
              </div>
              <div>
                <dt>{content.config.sendThinking}</dt>
                <dd>
                  <ApiField field="sendThinking" errors={errors}>
                    <Select
                      id="settings-send-thinking"
                      aria-label={content.config.sendThinking}
                      value={draft.sendThinking}
                      onChange={edit('sendThinking')}
                      disabled={thinkUnsupported}
                      options={[
                        { value: YES, label: content.config.yes },
                        { value: NO, label: content.config.no },
                      ]}
                    />
                  </ApiField>
                  {thinkUnsupported && (
                    <span className="hint">{content.config.thinkUnsupported}</span>
                  )}
                </dd>
              </div>
              {/*
                R-WP-51: the advanced trio of the base level. Text again — empty is a value of its own here
                (it deletes), and the server is the one that parses and refuses by field name.
              */}
              <div>
                <dt>{content.config.maxTokens}</dt>
                <dd>
                  <ApiField field="maxTokens" errors={errors}>
                    <Input
                      inputMode="numeric"
                      aria-label={content.config.maxTokens}
                      value={draft.maxTokens}
                      onChange={(event) => edit('maxTokens')(event.target.value)}
                    />
                  </ApiField>
                </dd>
              </div>
              <div>
                <dt>{content.config.queryParams}</dt>
                <dd>
                  <ApiField field="queryParams" errors={errors}>
                    <Input
                      aria-label={content.config.queryParams}
                      placeholder={content.config.paramsPlaceholder}
                      value={draft.queryParams}
                      onChange={(event) => edit('queryParams')(event.target.value)}
                    />
                  </ApiField>
                </dd>
              </div>
              <div>
                <dt>{content.config.headerParams}</dt>
                <dd>
                  <ApiField field="headerParams" errors={errors}>
                    <Input
                      aria-label={content.config.headerParams}
                      placeholder={content.config.paramsPlaceholder}
                      value={draft.headerParams}
                      onChange={(event) => edit('headerParams')(event.target.value)}
                    />
                  </ApiField>
                </dd>
              </div>
              <div>
                <dt>{content.config.key}</dt>
                <dd>
                  <ApiField field="apiKey" errors={errors}>
                    <Input
                      type="password"
                      aria-label={content.config.key}
                      autoComplete="off"
                      placeholder={content.config.unchanged}
                      value={draft.apiKey}
                      onChange={(event) => edit('apiKey')(event.target.value)}
                    />
                  </ApiField>
                  <span className={settings.keySet ? 'key-set' : 'key-missing'}>
                    {settings.keySet ? content.config.keySet : content.config.keyMissing}
                  </span>
                  <span className="hint">
                    {content.config.keySource}: {SOURCE_LABEL[settings.keySource]}
                  </span>
                  {/* a key from the environment can be reported but never removed here (UC-WP-63 GIVEN 2) */}
                  {settings.keySource === 'FILE' && (
                    <Button size="small" disabled={removeKey} onClick={() => setRemoveKey(true)}>
                      {content.config.removeKey}
                    </Button>
                  )}
                  {removeKey && <span className="hint">{content.config.removeKeyQueued}</span>}
                </dd>
              </div>
            </dl>
            <Button type="primary" htmlType="submit" disabled={busy}>
              {busy ? content.config.saving : content.config.save}
            </Button>
          </Form>
        </Card>
      )}
      {/* R-WP-23: the agent overrides below the default — their own read, their own save per slot. The
          think field is built from the provider the form names (UC-WP-67), so the section reads the
          answer of this page rather than asking the server the same question a second time. The model
          lists of the slots are asked the same way (R-WP-50): a slot has no connection of its own, so
          the page names provider and base URL and the section adds only the slot and its drafted URL.
          The extra body's capacity is the same provider's answer (R-WP-52) — a provider the list does
          not offer cannot carry it, and the draft keeps the stored value either way. */}
      <Agents
        think={thinkInput}
        connection={draft === null ? '' : draft.provider + ' ' + draft.url}
        extraBodySupported={chosen?.extraBodySupported === true}
        loadModels={loadSlotModels}
        reloadToken={agentsReload}
      />
    </section>
  )
}
