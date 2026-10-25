import { useEffect, useState } from 'react'

import { errorMessage } from '../api/projects'
import { getSettings, type KeySource, type Settings } from '../api/settings'

const SOURCE_LABEL: Record<KeySource, string> = {
  FILE: 'Datei',
  ENV: 'Umgebungsvariable',
  NONE: 'keine',
}

/**
 * The masked settings (R-WP-16): the six values of the factory's own config, the key <em>status</em>
 * and where a value came from. There is no key row to fill — the server sends no field that could
 * carry one, so nothing here can display a secret by accident.
 */
export function SettingsView() {
  const [settings, setSettings] = useState<Settings | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    // mount = read the config, an external system; the update lands after the await
    getSettings()
      .then(setSettings)
      .catch(error => setMessage(errorMessage(error)))
  }, [])

  return (
    <section className="view" aria-label="Einstellungen">
      <h1>Einstellungen</h1>
      {message && (
        <p className="form-error" role="alert">
          {message}
        </p>
      )}
      {settings && (
        <dl className="settings">
          <div>
            <dt>Provider</dt>
            <dd>{settings.provider}</dd>
          </div>
          <div>
            <dt>Modell</dt>
            <dd>{settings.model ?? '—'}</dd>
          </div>
          <div>
            <dt>URL</dt>
            <dd>{settings.url ?? '—'}</dd>
          </div>
          <div>
            <dt>Token-Fenster</dt>
            <dd>{settings.tokenWindow}</dd>
          </div>
          <div>
            <dt>Timeout</dt>
            <dd>{settings.timeoutSeconds} s</dd>
          </div>
          <div>
            <dt>Think senden</dt>
            <dd>{settings.sendThinking ? 'ja' : 'nein'}</dd>
          </div>
          <div>
            <dt>Key</dt>
            <dd>
              <span className={settings.keySet ? 'key-set' : 'key-missing'}>
                {settings.keySet ? 'gesetzt' : 'nicht gesetzt'}
              </span>
              <span className="hint">Quelle: {SOURCE_LABEL[settings.keySource]}</span>
            </dd>
          </div>
        </dl>
      )}
    </section>
  )
}
