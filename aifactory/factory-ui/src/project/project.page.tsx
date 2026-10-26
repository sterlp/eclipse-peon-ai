import { useCallback, useEffect, useState } from 'react'

import { addProject, listProjects, removeProject } from './project.client'
import { errorMessage } from '../shared/api.client'
import type { Project } from '../shared/dto'

interface Props {
  onOpen: (project: Project) => void
}

export function ProjectPage({ onOpen }: Props) {
  const [projects, setProjects] = useState<Project[]>([])
  const [gitUrl, setGitUrl] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  const reload = useCallback(async () => {
    const loaded = await listProjects()
    setProjects(loaded)
  }, [])

  const showError = useCallback((error: unknown) => {
    // the reason belongs next to the input; a swallowed error would read as "nothing happened"
    setMessage(errorMessage(error))
  }, [])

  useEffect(() => {
    // mount = synchronize with the registry, an external system; the update lands after the await
    listProjects().then(setProjects).catch(showError)
  }, [showError])

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
      await reload()
    } catch (error) {
      // the list stays unchanged, the reason lands at the input (UC-WP-4)
      showError(error)
    } finally {
      setBusy(false)
    }
  }

  async function remove(project: Project) {
    if (!window.confirm(`${project.name} entfernen? Der Clone-Ordner wird gelöscht.`)) {
      return
    }
    setMessage(null)
    try {
      await removeProject(project.id)
      await reload()
    } catch (error) {
      showError(error)
    }
  }

  return (
    <section className="view" aria-label="Projekte">
      <h1>Projekte</h1>
      <form className="add-project" onSubmit={event => void submit(event)}>
        <input
          type="url"
          placeholder="https://git.example.com/org/repo.git"
          aria-label="Git-URL"
          value={gitUrl}
          onChange={event => setGitUrl(event.target.value)}
        />
        <button type="submit" disabled={busy}>
          {busy ? 'Klone…' : 'Hinzufügen'}
        </button>
      </form>
      {message && (
        <p className="form-error" role="alert">
          {message}
        </p>
      )}
      {projects.length === 0 ? (
        <p className="empty-state">Kein Projekt</p>
      ) : (
        <ul className="project-list">
          {projects.map(project => (
            <li key={project.id}>
              <button type="button" className="project-name" onClick={() => onOpen(project)}>
                {project.name}
              </button>
              <span className="project-url">{project.gitUrl}</span>
              <button type="button" className="remove" onClick={() => void remove(project)}>
                Entfernen
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
