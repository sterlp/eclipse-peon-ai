import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Card, Input, Popconfirm } from 'antd'

import { addProject, removeProject } from './project.client'
import { openProjectStatusStream } from './project.status.client'
import { errorMessage } from '../shared/api.client'
import { content } from '../shared/content'
import type { Project, ProjectWork } from '../shared/dto'

/** The same pause the conversation uses: long enough not to hammer, short enough to be noticed. */
const RECONNECT_MS = 500

interface Props {
  /** The registry as the frame read it — this page owns no copy of it (D1, R-WP-38). */
  projects: Project[]
  /** Why the tree beside this page is empty, when the registry could not be read. */
  listError?: string | null
  onOpen: (project: Project) => void
  /** Asks the frame to read the registry again: what this page added or removed has to show in the tree. */
  onReload: () => Promise<void>
}

export function ProjectPage({ projects, listError, onOpen, onReload }: Props) {
  const [work, setWork] = useState<Record<string, ProjectWork>>({})
  const [gitUrl, setGitUrl] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  const showError = useCallback((error: unknown) => {
    // the reason belongs next to the input; a swallowed error would read as "nothing happened"
    setMessage(errorMessage(error))
  }, [])

  useEffect(() => {
    // what the server is working on arrives by itself (R-WP-24): the snapshot of a new connection
    // replaces what this view holds, every change afterwards merges into it, and nothing of it is kept
    // past the row it belongs to — the states are a picture of the runtimes and never a store
    const controller = new AbortController()
    let reconnect: ReturnType<typeof setTimeout> | undefined
    let mounted = true

    const connect = () => {
      void openProjectStatusStream(
        {
          onEvent: event => {
            if (event.kind === 'snapshot') {
              setWork(Object.fromEntries(event.work.map(state => [state.projectId, state])))
              return
            }
            setWork(current => ({ ...current, [event.work.projectId]: event.work }))
          },
          // a stream that ended is re-opened, and the new connection is answered with a fresh snapshot —
          // so a viewer that lost the wire is back on the current state without reading the list again
          onEnd: retry => {
            if (mounted && retry) {
              reconnect = setTimeout(connect, RECONNECT_MS)
            }
          },
        },
        controller.signal,
      )
    }

    connect()
    return () => {
      mounted = false
      controller.abort()
      if (reconnect !== undefined) {
        clearTimeout(reconnect)
      }
    }
  }, [])

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (busy || gitUrl.trim() === '') {
      return
    }
    setBusy(true)
    setMessage(null)
    try {
      await addProject(gitUrl.trim())
      setGitUrl('')
      await onReload()
    } catch (error) {
      // the list stays unchanged, the reason lands at the input (UC-WP-4)
      showError(error)
    } finally {
      setBusy(false)
    }
  }

  async function remove(project: Project) {
    setMessage(null)
    try {
      await removeProject(project.id)
      await onReload()
    } catch (error) {
      showError(error)
    }
  }

  // this page's own refusal reads before the frame's: the viewer just caused it (UC-WP-4)
  const problem = message ?? listError

  return (
    <section className="view" aria-label={content.projects.title}>
      <h1>{content.projects.title}</h1>
      <form className="add-project" onSubmit={event => void submit(event)}>
        <Input
          type="url"
          placeholder={content.projects.gitUrlSample}
          aria-label={content.projects.gitUrl}
          value={gitUrl}
          onChange={event => setGitUrl(event.target.value)}
        />
        <Button type="primary" htmlType="submit" disabled={busy}>
          {busy ? content.projects.cloning : content.projects.add}
        </Button>
      </form>
      {problem && <Alert type="error" title={problem} />}
      {projects.length === 0 ? (
        <p className="empty-state">{content.projects.empty}</p>
      ) : (
        <ul className="project-list">
          {projects.map(project => {
            // a project the server has told nothing about is free — that is what a factory holds no
            // runtime for means, and it is the same answer a restart gives (R-WP-24)
            const state = work[project.id]
            const working = state?.working ?? false
            const queued = state?.queued ?? 0
            return (
              <li key={project.id}>
                {/* the card of the basis names the row (R-WP-39) */}
                <Card size="small">
                  <div className="project-row">
                    <Button type="link" className="project-name" onClick={() => onOpen(project)}>
                      {project.name}
                    </Button>
                    <span className="project-work" data-working={working}>
                      {working ? content.projects.working : content.projects.free}
                      {working && queued > 0 && (
                        <span className="project-queued">{content.projects.queued(queued)}</span>
                      )}
                    </span>
                    <span className="project-url">{project.gitUrl}</span>
                    {/* the removal asks before it deletes: the clone folder goes with the row (UC-WP-28) */}
                    <Popconfirm
                      title={content.projects.removeQuestion(project.name)}
                      okText={content.common.yes}
                      cancelText={content.common.no}
                      onConfirm={() => void remove(project)}
                    >
                      <Button size="small">{content.projects.remove}</Button>
                    </Popconfirm>
                  </div>
                </Card>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
