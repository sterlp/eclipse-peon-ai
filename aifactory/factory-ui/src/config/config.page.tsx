import { useEffect, useState } from 'react'

import { errorMessage } from '../shared/api.client'
import { content } from '../shared/content'
import {
  getSettings,
  PROVIDERS,
  updateSettings,
  type KeySource,
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

/**
 * The form holds every editable value as text, so "empty" is one concept across text and number
 * fields: an empty input is not a value and is never sent (R-WP-16: an empty field stays unchanged).
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

function patchOf(draft: Draft): SettingsPatch {
  return {
    provider: draft.provider,
    model: draft.model,
    url: draft.url,
    apiKey: draft.apiKey,
    tokenWindow: numberOf(draft.tokenWindow),
    timeoutSeconds: numberOf(draft.timeoutSeconds),
    sendThinking: draft.sendThinking === YES,
  }
}

/** A number field is sent as a number or not at all — `type="number"` already reports '' on garbage. */
function numberOf(text: string): number | undefined {
  const trimmed = text.trim()
  return trimmed === '' || !Number.isFinite(Number(trimmed)) ? undefined : Number(trimmed)
}

/**
 * The factory's config (R-WP-16): the six values, the key <em>status</em> and where a value came from.
 * The key row shows a status because the server sends no field that could carry one — its input is
 * write-only, so a saved key stays stored and an empty input leaves it alone.
 */
export function ConfigPage() {
  const [settings, setSettings] = useState<Settings | null>(null)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [saveMessage, setSaveMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    // mount = read the config, an external system; the update lands after the await
    getSettings()
      .then(loaded => {
        setSettings(loaded)
        setDraft(draftOf(loaded))
      })
      .catch(error => setMessage(errorMessage(error)))
  }, [])

  function edit(field: keyof Draft) {
    return (event: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
      setDraft(current => (current ? { ...current, [field]: event.target.value } : current))
  }

  async function save(event: React.FormEvent) {
    event.preventDefault()
    if (busy || draft === null) {
      return
    }
    setBusy(true)
    setSaveMessage(null)
    try {
      // the answer is the stored state, read back masked — the form is refilled from it, not from the input
      const saved = await updateSettings(patchOf(draft))
      setSettings(saved)
      setDraft(draftOf(saved))
    } catch (error) {
      // the typed values stay: the message names the field to fix, nothing the user wrote is lost
      setSaveMessage(errorMessage(error))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="view" aria-label={content.config.title}>
      <h1>{content.config.title}</h1>
      {message && (
        <p className="form-error" role="alert">
          {message}
        </p>
      )}
      {settings && draft && (
        <form className="settings-form" onSubmit={event => void save(event)}>
          <dl className="settings">
            <div>
              <dt>{content.config.provider}</dt>
              <dd>
                <select aria-label={content.config.provider} value={draft.provider} onChange={edit('provider')}>
                  {PROVIDERS.map(provider => (
                    <option key={provider} value={provider}>
                      {provider}
                    </option>
                  ))}
                </select>
              </dd>
            </div>
            {saveMessage && (
              <p className="form-error" role="alert">
                {/* the rejected field is named by the server's answer; a value never comes back */}
                {saveMessage}
              </p>
            )}
            <div>
              <dt>{content.config.model}</dt>
              <dd>
                <input
                  aria-label={content.config.model}
                  placeholder={content.config.unchanged}
                  value={draft.model}
                  onChange={edit('model')}
                />
              </dd>
            </div>
            <div>
              <dt>{content.config.url}</dt>
              <dd>
                <input
                  type="url"
                  aria-label={content.config.url}
                  placeholder={content.config.unchanged}
                  value={draft.url}
                  onChange={edit('url')}
                />
              </dd>
            </div>
            <div>
              <dt>{content.config.tokenWindow}</dt>
              <dd>
                <input
                  type="number"
                  aria-label={content.config.tokenWindow}
                  placeholder={content.config.unchanged}
                  value={draft.tokenWindow}
                  onChange={edit('tokenWindow')}
                />
              </dd>
            </div>
            <div>
              <dt>{content.config.timeout}</dt>
              <dd>
                <input
                  type="number"
                  aria-label={content.config.timeout}
                  placeholder={content.config.unchanged}
                  value={draft.timeoutSeconds}
                  onChange={edit('timeoutSeconds')}
                />
                <span className="hint">{content.common.seconds}</span>
              </dd>
            </div>
            <div>
              <dt>{content.config.sendThinking}</dt>
              <dd>
                <select aria-label={content.config.sendThinking} value={draft.sendThinking} onChange={edit('sendThinking')}>
                  <option value={YES}>{content.config.yes}</option>
                  <option value={NO}>{content.config.no}</option>
                </select>
              </dd>
            </div>
            <div>
              <dt>{content.config.key}</dt>
              <dd>
                <input
                  type="password"
                  aria-label={content.config.key}
                  autoComplete="off"
                  placeholder={content.config.unchanged}
                  value={draft.apiKey}
                  onChange={edit('apiKey')}
                />
                <span className={settings.keySet ? 'key-set' : 'key-missing'}>
                  {settings.keySet ? content.config.keySet : content.config.keyMissing}
                </span>
                <span className="hint">
                  {content.config.keySource}: {SOURCE_LABEL[settings.keySource]}
                </span>
              </dd>
            </div>
          </dl>
          <button type="submit" disabled={busy}>
            {busy ? content.config.saving : content.config.save}
          </button>
        </form>
      )}
    </section>
  )
}
