import { useEffect, useState } from 'react'
import { Alert, Button, Card, Input, Select } from 'antd'

import { errorMessage } from '../shared/api.client'
import { content } from '../shared/content'
import { Agents } from './config.agents'
import {
  getSettings,
  listProviders,
  thinkInputOf,
  updateSettings,
  type KeySource,
  type Provider,
  type Settings,
  type SettingsPatch,
} from './config.client'

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
 * The form holds every editable value as text, so "empty" is one concept across text and number fields —
 * and empty is a value of its own: it says the user cleared the field, which deletes it (R-WP-16
 * "geleert = gelöscht", UC-WP-46). The key field is the exception: write-only, never prefilled.
 */
interface Draft {
  provider: string
  model: string
  url: string
  apiKey: string
  tokenWindow: string
  timeoutSeconds: string
  sendThinking: string
}

function draftOf(settings: Settings): Draft {
  return {
    provider: settings.provider,
    model: settings.model ?? '',
    url: settings.url ?? '',
    // write-only: a stored key never comes back over the wire, so the field always starts empty
    apiKey: '',
    tokenWindow: String(settings.tokenWindow),
    timeoutSeconds: String(settings.timeoutSeconds),
    sendThinking: settings.sendThinking ? YES : NO,
  }
}

/** Every field travels, cleared ones as empty text — that is how a deletion reaches the file. */
function patchOf(draft: Draft, removeKey: boolean): SettingsPatch {
  return {
    provider: draft.provider,
    model: draft.model,
    url: draft.url,
    // an empty key field is dropped by the client: unchanged is the only thing it can say
    apiKey: draft.apiKey,
    tokenWindow: draft.tokenWindow,
    timeoutSeconds: draft.timeoutSeconds,
    sendThinking: draft.sendThinking === YES ? TRUE : FALSE,
    ...(removeKey ? { removeApiKey: true } : {}),
  }
}

/**
 * The factory's config (R-WP-16): the six values, the key <em>status</em> and where a value came from.
 * The form behaves like the provider dialog of the plugin (UC-WP-62) — a provider change updates the
 * fields that depend on it at once — and it can remove a key (UC-WP-63).
 */
export function ConfigPage() {
  const [settings, setSettings] = useState<Settings | null>(null)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [providers, setProviders] = useState<Provider[]>([])
  const [removeKey, setRemoveKey] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [saveMessage, setSaveMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

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

  async function save(event: React.FormEvent) {
    event.preventDefault()
    if (busy || draft === null) {
      return
    }
    setBusy(true)
    setSaveMessage(null)
    try {
      // the answer is the stored state, read back masked — the form is refilled from it, not from the
      // input, which is also what makes the key status fresh after every save (UC-WP-63)
      const saved = await updateSettings(patchOf(draft, removeKey))
      setSettings(saved)
      setDraft(draftOf(saved))
      setRemoveKey(false)
    } catch (error) {
      // the typed values stay: the message names the field to fix, nothing the user wrote is lost
      setSaveMessage(errorMessage(error))
    } finally {
      setBusy(false)
    }
  }

  // the provider the form currently names — the source of both dynamic answers below
  const chosen = draft ? providers.find((entry) => entry.name === draft.provider) : undefined
  // Q3: the think value stays a boolean; a provider that cannot carry it says so and gets no field to try
  const thinkUnsupported = chosen !== undefined && !chosen.thinkSupported

  // a stored provider the core list does not offer still has to show what the file holds
  const offered = providers.map((entry) => entry.name)
  const providerOptions = (
    draft !== null && !offered.includes(draft.provider) ? [draft.provider, ...offered] : offered
  ).map((name) => ({ value: name, label: name }))

  return (
    <section className="view" aria-label={content.config.title}>
      <h1>{content.config.title}</h1>
      {message && <Alert type="error" title={message} />}
      {settings && draft && (
        <Card size="small" className="settings-card">
          <form className="settings-form" onSubmit={(event) => void save(event)}>
            <dl className="settings">
              <div>
                <dt>{content.config.provider}</dt>
                <dd>
                  <Select
                    id="settings-provider"
                    aria-label={content.config.provider}
                    value={draft.provider}
                    onChange={editProvider}
                    options={providerOptions}
                  />
                </dd>
              </div>
              {saveMessage && (
                <Alert
                  type="error"
                  // the rejected field is named by the server's answer; a value never comes back
                  title={saveMessage}
                />
              )}
              <div>
                <dt>{content.config.model}</dt>
                <dd>
                  <Input
                    aria-label={content.config.model}
                    value={draft.model}
                    onChange={(event) => edit('model')(event.target.value)}
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
                  <Input
                    inputMode="numeric"
                    aria-label={content.config.tokenWindow}
                    value={draft.tokenWindow}
                    onChange={(event) => edit('tokenWindow')(event.target.value)}
                  />
                </dd>
              </div>
              <div>
                <dt>{content.config.timeout}</dt>
                <dd>
                  <Input
                    inputMode="numeric"
                    aria-label={content.config.timeout}
                    value={draft.timeoutSeconds}
                    onChange={(event) => edit('timeoutSeconds')(event.target.value)}
                  />
                  <span className="hint">{content.common.seconds}</span>
                </dd>
              </div>
              <div>
                <dt>{content.config.sendThinking}</dt>
                <dd>
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
                  {thinkUnsupported && (
                    <span className="hint">{content.config.thinkUnsupported}</span>
                  )}
                </dd>
              </div>
              <div>
                <dt>{content.config.key}</dt>
                <dd>
                  <Input
                    type="password"
                    aria-label={content.config.key}
                    autoComplete="off"
                    placeholder={content.config.unchanged}
                    value={draft.apiKey}
                    onChange={(event) => edit('apiKey')(event.target.value)}
                  />
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
          </form>
        </Card>
      )}
      {/* R-WP-23: the agent overrides below the default — their own read, their own save per slot. The
          think field is built from the provider the form names (UC-WP-67), so the section reads the
          answer of this page rather than asking the server the same question a second time. */}
      <Agents think={thinkInputOf(chosen)} />
    </section>
  )
}
