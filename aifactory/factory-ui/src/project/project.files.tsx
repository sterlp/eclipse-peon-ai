import { useCallback, useEffect, useState } from 'react'

import { readFiles, type FileRoot, type FilesResponse } from './files.client'
import { errorMessage } from '../shared/api.client'
import type { Project } from '../shared/dto'

interface Props {
  project: Project
}

const ROOTS: { value: FileRoot; label: string }[] = [
  { value: 'checkout', label: 'Checkout' },
  { value: 'tmp', label: 'TMP' },
]

/**
 * The file area (R-WP-14, UC-WP-19): a read-only slide-over over the two roots the project may write
 * to — its checkout and the system TMP. It owns its state and its open button, so `chat` can carry it
 * as an opaque slot without knowing what a root is.
 *
 * Reading is one level per request; going deeper is another read. There is nothing here that could
 * write, upload or create, because the edge behind it has no such method (UC-WP-42).
 */
export function ProjectFiles({ project }: Props) {
  const [open, setOpen] = useState(false)
  const [root, setRoot] = useState<FileRoot>('checkout')
  const [path, setPath] = useState('')
  const [content, setContent] = useState<FilesResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) {
      return
    }
    let mounted = true
    readFiles(project.id, root, path)
      .then(loaded => {
        if (mounted) {
          setContent(loaded)
          setError(null)
        }
      })
      // a rejected path is a line of text where the content would have stood — no dialog, no retry
      .catch(reason => {
        if (mounted) {
          setContent(null)
          setError(errorMessage(reason))
        }
      })
    return () => {
      mounted = false
    }
  }, [open, project.id, root, path])

  const selectRoot = useCallback((next: FileRoot) => {
    // a path of one root says nothing about the other, so switching starts at the root itself
    setRoot(next)
    setPath('')
  }, [])

  function enter(name: string) {
    setPath(path === '' ? name : `${path}/${name}`)
  }

  function back() {
    setPath(path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : '')
  }

  return (
    <>
      <button type="button" className="chat-files-open" data-testid="files-open" onClick={() => setOpen(true)}>
        Dateien
      </button>
      {open && (
        <aside className="chat-files" aria-label="Dateien" data-testid="files-panel">
          <header className="chat-files-head">
            <span className="chat-files-project">{project.name}</span>
            <div className="chat-files-roots" role="group" aria-label="Wurzel">
              {ROOTS.map(option => (
                <button
                  key={option.value}
                  type="button"
                  className={root === option.value ? 'active' : ''}
                  aria-pressed={root === option.value}
                  data-testid={`files-root-${option.value}`}
                  onClick={() => selectRoot(option.value)}
                >
                  {option.label}
                </button>
              ))}
            </div>
            <button type="button" data-testid="files-close" onClick={() => setOpen(false)}>
              Schließen
            </button>
          </header>
          <p className="chat-files-path" data-testid="files-path">
            {root} · /{path}
          </p>
          {error !== null && (
            <p className="chat-files-error" role="alert" data-testid="files-error">
              {error}
            </p>
          )}
          {content === null && error === null && <p className="hint">Lädt…</p>}
          {content?.kind === 'directory' && (
            <ul className="chat-files-list" data-testid="files-list">
              {content.entries.map(entry => (
                <li key={entry.name}>
                  <button
                    type="button"
                    className={entry.directory ? 'directory' : ''}
                    data-testid="files-entry"
                    onClick={() => enter(entry.name)}
                  >
                    {entry.name}
                    {entry.directory ? '/' : ` (${entry.size} B)`}
                  </button>
                </li>
              ))}
              {content.entries.length === 0 && <li className="empty-state">Leerer Ordner</li>}
            </ul>
          )}
          {content?.kind === 'file' &&
            (content.binary ? (
              <p className="hint" data-testid="files-binary">
                Binäre Datei ({content.size} B) — Inhalt wird nicht angezeigt.
              </p>
            ) : (
              <pre className="chat-files-text" data-testid="files-text">
                {content.text}
              </pre>
            ))}
          {content?.truncated && (
            <p className="hint" data-testid="files-truncated">
              Abgeschnitten — die Wurzel ist größer als diese Kante liefert.
            </p>
          )}
          {path !== '' && (
            <button type="button" data-testid="files-back" onClick={back}>
              Zurück
            </button>
          )}
        </aside>
      )}
    </>
  )
}
