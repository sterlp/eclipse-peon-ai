import { useCallback, useEffect, useState } from 'react'

import { readFiles, type FileEntry, type FileRoot, type FilesResponse } from './files.client'
import { errorMessage } from '../shared/api.client'
import { content } from '../shared/content'
import type { Project } from '../shared/dto'

interface Props {
  /**
   * The addressed project. The area owns its root and path state and resets neither of them on its own,
   * so the caller must key this component per project (`key={project.id}`) — unkeyed, the second project
   * would open with the path of the one before it.
   */
  project: Project
}

const ROOTS: { value: FileRoot; label: string }[] = [
  { value: 'checkout', label: content.files.roots.checkout },
  { value: 'tmp', label: content.files.roots.tmp },
]

/**
 * The suffix behind a name. A symlink is a name and nothing else — the server sends no metadata of its
 * target, so `link` is the honest label and `0 B` would read as an empty file (UC-WP-42).
 */
function suffixOf(entry: FileEntry): string {
  if (entry.directory) {
    return '/'
  }
  return entry.symlink ? content.files.linkSuffix : ` (${entry.size} B)`
}

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
  /** The one read of `root` + `path`: a directory listing or a file, never both. */
  const [listing, setListing] = useState<FilesResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) {
      return
    }
    let mounted = true
    readFiles(project.id, root, path)
      .then(loaded => {
        if (mounted) {
          setListing(loaded)
          setError(null)
        }
      })
      // a rejected path is a line of text where the content would have stood — no dialog, no retry
      .catch(reason => {
        if (mounted) {
          setListing(null)
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
        {content.files.open}
      </button>
      {open && (
        <aside className="chat-files" aria-label={content.files.title} data-testid="files-panel">
          <header className="chat-files-head">
            <span className="chat-files-project">{project.name}</span>
            <div className="chat-files-roots" role="group" aria-label={content.files.root}>
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
              {content.common.close}
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
          {listing === null && error === null && <p className="hint">{content.common.loading}</p>}
          {listing?.kind === 'directory' && (
            <ul className="chat-files-list" data-testid="files-list">
              {listing.entries.map(entry => (
                <li key={entry.name}>
                  <button
                    type="button"
                    className={entry.directory ? 'directory' : ''}
                    data-testid="files-entry"
                    onClick={() => enter(entry.name)}
                  >
                    {entry.name}
                    {suffixOf(entry)}
                  </button>
                </li>
              ))}
              {listing.entries.length === 0 && <li className="empty-state">{content.files.emptyFolder}</li>}
            </ul>
          )}
          {listing?.kind === 'file' &&
            (listing.binary ? (
              <p className="hint" data-testid="files-binary">
                {content.files.binary(listing.size)}
              </p>
            ) : (
              <pre className="chat-files-text" data-testid="files-text">
                {listing.text}
              </pre>
            ))}
          {listing?.truncated && (
            <p className="hint" data-testid="files-truncated">
              {content.files.truncated}
            </p>
          )}
          {path !== '' && (
            <button type="button" data-testid="files-back" onClick={back}>
              {content.common.back}
            </button>
          )}
        </aside>
      )}
    </>
  )
}
