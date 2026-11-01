import { useEffect, useState } from 'react'

import { readTranscript, type AgentTranscript, type TranscriptKind } from './chat.agents.client'
import { errorMessage } from '../shared/api.client'

interface Props {
  /** The project whose runtime holds this agent — the read is addressed by project and tile name. */
  projectId: string
  /** The tile's name as the roster sends it: the server resolves it, this component never maps a name. */
  name: string
  onClose: () => void
}

const KIND_LABEL: Record<TranscriptKind, string> = {
  user: 'Eingang',
  agent: 'Antwort',
  tool: 'Werkzeug',
}

/**
 * Der Blick in das eigene Gespräch eines Agenten (R-WP-26, UC-WP-49): ein Slide-over neben dem Chat, das
 * zeigt, was das Memory genau dieses Agenten hält — die gestellte Aufgabe, seine eigenen Werkzeug-Zeilen,
 * seine Antwort. Gezeichnet wird, was die Kante schickt: kein Eingabefeld, kein Senden, keine zweite
 * Seite, denn die Kante hat nichts davon (UC-WP-40 bleibt scharf — Jons Chat bleibt frei davon).
 *
 * Die Reads gehören zur Komponente und hängen an Projekt und Name: eine andere Kachel liest erneut, und
 * nach einem Neustart ist der Blick leer, weil ein Mitglieds-Memory RAM und sonst nichts ist (R-WP-11).
 * Er ist ein Bereich des Chat-Views und kein vierter Tab (R-WP-1), und er kennt nur den Namen, den die
 * Kachel trägt — die Fähigkeitsmenge der Kachel entscheidet, ob er sich überhaupt öffnen lässt (D-14).
 */
export function ChatInsight({ projectId, name, onClose }: Props) {
  const [transcript, setTranscript] = useState<AgentTranscript | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let mounted = true
    readTranscript(projectId, name)
      .then(loaded => {
        if (mounted) {
          setTranscript(loaded)
          setError(null)
        }
      })
      // ein abgelehnter Name ist eine Zeile Text dort, wo das Gespräch stünde — kein Dialog, kein Retry
      .catch(reason => {
        if (mounted) {
          setTranscript(null)
          setError(errorMessage(reason))
        }
      })
    return () => {
      mounted = false
    }
  }, [projectId, name])

  return (
    <aside className="chat-insight" aria-label={`Blick in ${name}`} data-testid="insight-panel">
      <header className="chat-insight-head">
        <span className="chat-insight-agent" data-testid="insight-agent">
          {name}
        </span>
        <span className="chat-insight-readonly">schreibgeschützt</span>
        <button type="button" data-testid="insight-close" onClick={onClose}>
          Schließen
        </button>
      </header>
      {error !== null && (
        <p className="chat-insight-error" role="alert" data-testid="insight-error">
          {error}
        </p>
      )}
      {transcript === null && error === null && <p className="hint">Lädt…</p>}
      {transcript !== null && (
        <>
          {transcript.lines.length === 0 ? (
            <p className="empty-state" data-testid="insight-empty">
              Kein Gespräch — dieser Agent hat in diesem Lauf noch keines.
            </p>
          ) : (
            <ul className="chat-insight-list" data-testid="insight-list">
              {transcript.lines.map((line, index) => (
                <li
                  key={index}
                  className={`chat-insight-line ${line.kind}`}
                  data-kind={line.kind}
                  data-testid="insight-line"
                >
                  <span className="chat-insight-kind">{KIND_LABEL[line.kind]}</span>
                  <span className="chat-insight-text">{line.text}</span>
                </li>
              ))}
            </ul>
          )}
          {transcript.truncated && (
            <p className="hint" data-testid="insight-truncated">
              Ältere Zeilen fallen weg — die Kante liefert nur die letzten.
            </p>
          )}
        </>
      )}
    </aside>
  )
}
