import { useEffect, useState } from 'react'

import { errorMessage } from '../shared/api.client'
import {
  getSettings,
  PROVIDERS,
  updateSettings,
  type KeySource,
  type Settings,
  type SettingsPatch,
} from './config.client'

const SOURCE_LABEL: Record<KeySource, string> = {
  FILE: 'Datei',
  ENV: 'Umgebungsvariable',
  NONE: 'keine',
}

/** Said out loud at every field that may stay empty — R-WP-16 "leeres Feld = unverändert". */
const UNCHANGED_HINT = 'leer = unverändert'

/**
 * The form holds every editable value as text, so "empty" is one concept across text and number
 * fields: an empty input is not a value and is never sent.
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
    sendThinking: settings.sendThinking ? 'ja' : 'nein',
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
    sendThinking: draft.sendThinking === 'ja',
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
    <section className="view" aria-label="Einstellungen">
      <h1>Einstellungen</h1>
      {message && (
        <p className="form-error" role="alert">
          {message}
        </p>
      )}
      {settings && draft && (
        <form className="settings-form" onSubmit={event => void save(event)}>
          <dl className="settings">
            <div>
              <dt>Provider</dt>
              <dd>
                <select aria-label="Provider" value={draft.provider} onChange={edit('provider')}>
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
              <dt>Modell</dt>
              <dd>
                <input aria-label="Modell" placeholder={UNCHANGED_HINT} value={draft.model} onChange={edit('model')} />
              </dd>
            </div>
            <div>
              <dt>URL</dt>
              <dd>
                <input type="url" aria-label="URL" placeholder={UNCHANGED_HINT} value={draft.url} onChange={edit('url')} />
              </dd>
            </div>
            <div>
              <dt>Token-Fenster</dt>
              <dd>
                <input
                  type="number"
                  aria-label="Token-Fenster"
                  placeholder={UNCHANGED_HINT}
                  value={draft.tokenWindow}
                  onChange={edit('tokenWindow')}
                />
              </dd>
            </div>
            <div>
              <dt>Timeout</dt>
              <dd>
                <input
                  type="number"
                  aria-label="Timeout"
                  placeholder={UNCHANGED_HINT}
                  value={draft.timeoutSeconds}
                  onChange={edit('timeoutSeconds')}
                />
                <span className="hint">Sekunden</span>
              </dd>
            </div>
            <div>
              <dt>Think senden</dt>
              <dd>
                <select aria-label="Think senden" value={draft.sendThinking} onChange={edit('sendThinking')}>
                  <option value="ja">ja</option>
                  <option value="nein">nein</option>
                </select>
              </dd>
            </div>
            <div>
              <dt>Key</dt>
              <dd>
                <input
                  type="password"
                  aria-label="Key"
                  autoComplete="off"
                  placeholder={UNCHANGED_HINT}
                  value={draft.apiKey}
                  onChange={edit('apiKey')}
                />
                <span className={settings.keySet ? 'key-set' : 'key-missing'}>
                  {settings.keySet ? 'gesetzt' : 'nicht gesetzt'}
                </span>
                <span className="hint">Quelle: {SOURCE_LABEL[settings.keySource]}</span>
              </dd>
            </div>
          </dl>
          <button type="submit" disabled={busy}>
            {busy ? 'Speichert…' : 'Speichern'}
          </button>
        </form>
      )}
    </section>
  )
}
