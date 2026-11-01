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
 * The insight into one agent's own conversation (R-WP-26, UC-WP-49): a slide-over beside the chat that
 * shows what exactly this agent's memory holds — the task it was given, its own tool lines, its answer.
 * What is drawn is what the edge sends: no input, no send, no fourth page, because the edge is none of
 * those (UC-WP-40 stays sharp — Jon's chat stays free of a member's internals).
 *
 * The reads belong to the component and hang on project and name: another tile reads again, and after a
 * restart the insight is empty, because a member's memory is RAM and nothing else (R-WP-11). It is an
 * area of the chat view and no fourth tab (R-WP-1), and it knows only the name its tile carries — the
 * tile's capability set decides whether it can be opened at all (D-14).
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
      // a refused name is one line of text where the conversation would stand — no dialog, no retry
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
