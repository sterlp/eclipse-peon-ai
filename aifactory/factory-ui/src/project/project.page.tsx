import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Card, Form, Input, Popconfirm } from 'antd'

import { addProject, removeProject } from './project.client'
import { openProjectStatusStream } from './project.status.client'
import { fieldErrorsOf } from '../shared/api.client'
import { ApiField, ApiFormErrors } from '../shared/api.error'
import { content } from '../shared/content'
import type { ProjectDto, ProjectWorkDto } from '../shared/server-api'
import { sseBackoff } from '../shared/sse.client'

/**
 * The fields this form wraps with `ApiField` — a name the answer carries besides these still reaches the
 * screen as a line, so a rule added on the server can never be lost between the two display paths (R8).
 */
const ADD_ERROR_FIELDS = ['gitUrl']

interface Props {
  /** The registry as the frame read it — this page owns no copy of it (D1, R-WP-38). */
  projects: ProjectDto[]
  /** Why the tree beside this page is empty, when the registry could not be read. */
  listError?: string | null
  onOpen: (project: ProjectDto) => void
  /** Asks the frame to read the registry again: what this page added or removed has to show in the tree. */
  onReload: () => Promise<void>
}

/**
 * The registry list and its add form. No page heading: the top menu already says which page this is, and the
 * named `view` region keeps that name for the screen (R-WP-43).
 */
export function ProjectPage({ projects, listError, onOpen, onReload }: Props) {
  const [work, setWork] = useState<Record<string, ProjectWorkDto>>({})
  const [gitUrl, setGitUrl] = useState('')
  const [busy, setBusy] = useState(false)
  const [saveError, setSaveError] = useState<unknown>(null)

  const showError = useCallback((error: unknown) => {
    // the reason belongs next to the input; a swallowed error would read as "nothing happened"
    setSaveError(error)
  }, [])

  useEffect(() => {
    // what the server is working on arrives by itself (R-WP-24): the snapshot of a new connection
    // replaces what this view holds, every change afterwards merges into it, and nothing of it is kept
    // past the row it belongs to — the states are a picture of the runtimes and never a store
    const controller = new AbortController()
    const backoff = sseBackoff()
    let reconnect: ReturnType<typeof setTimeout> | undefined
    let mounted = true

    const connect = () => {
      void openProjectStatusStream(
        {
          onEvent: event => {
            // delivered events are what ends the waiting (R-WP-58) — keep-alive comments never reach here
            backoff.reset()
            if (event.kind === 'snapshot') {
              setWork(Object.fromEntries(event.work.map(state => [state.projectId, state])))
              return
            }
            setWork(current => ({ ...current, [event.work.projectId]: event.work }))
          },
          // a stream that ended is re-opened — waiting longer per attempt (R-WP-58) — and the new connection
          // is answered with a fresh snapshot, so a viewer that lost the wire is back on the current state
          // without reading the list again
          onEnd: retry => {
            if (mounted && retry) {
              reconnect = setTimeout(connect, backoff.next())
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

  async function submit() {
    if (busy || gitUrl.trim() === '') {
      return
    }
    setBusy(true)
    setSaveError(null)
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

  async function remove(project: ProjectDto) {
    setSaveError(null)
    try {
      await removeProject(project.id)
      await onReload()
    } catch (error) {
      showError(error)
    }
  }

  // what the widget cannot put on a field, and nothing else — the frame's own read of the registry is the
  // other line, and this page's refusal reads before it (UC-WP-4)
  const errors = fieldErrorsOf(saveError)

  return (
    <section className="view" aria-label={content.projects.title}>
      <Form className="add-project" layout="inline" onFinish={() => void submit()}>
        <ApiField field="gitUrl" errors={errors}>
          <Input
            type="url"
            placeholder={content.projects.gitUrlSample}
            aria-label={content.projects.gitUrl}
            value={gitUrl}
            onChange={event => setGitUrl(event.target.value)}
          />
        </ApiField>
        <Button type="primary" htmlType="submit" disabled={busy}>
          {busy ? content.projects.cloning : content.projects.add}
        </Button>
      </Form>
      <ApiFormErrors error={saveError} fields={ADD_ERROR_FIELDS} />
      {saveError === null && listError && <Alert type="error" title={listError} />}
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
