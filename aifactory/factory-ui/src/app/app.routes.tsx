import { useEffect, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useNavigate, useParams } from 'react-router-dom'

import AppShell from './app.shell'
import { useAppContext } from './app.context'
import { chatPath, projectsPath } from './app.paths'
import { ChatPage } from '../chat/chat.page'
import { ConfigPage } from '../config/config.page'
import { ProjectFiles } from '../project/project.files'
import { ProjectPage } from '../project/project.page'
import { listProjects } from '../project/project.client'
import { content } from '../shared/content'
import { writeLastProject } from '../shared/last.project'
import type { Project } from '../shared/dto'

/** The address the app is built under (R-WP-22) — every route below is relative to it. */
export const BASENAME = '/ui'

/** The app as the browser runs it: real paths under {@link BASENAME}, so the address names the view. */
export function AppBrowser() {
  return (
    <BrowserRouter basename={BASENAME}>
      <AppRoutes />
    </BrowserRouter>
  )
}

/**
 * The three views of the MVP and nothing else (UC-WP-1): the project list, one project's Jon-Chat, the
 * settings. Every navigation is a path, so a reload and a shared address show the same thing (UC-WP-43,
 * UC-WP-52); an address that names no view is sent to the list rather than given a page of its own — the
 * server already answered it with this app ({@code SpaFallbackResolver}).
 *
 * The file area and the team tiles are not routes: they live inside the chat (R-WP-1), and the insight
 * opens and closes without ever changing the address.
 */
export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<Navigate to={projectsPath} replace />} />
        <Route path="projects" element={<ProjectsRoute />} />
        <Route path="chat/:projectId" element={<ChatRoute />} />
        <Route path="settings" element={<ConfigPage />} />
        <Route path="*" element={<Navigate to={projectsPath} replace />} />
      </Route>
    </Routes>
  )
}

/** The list, wired to the URL: opening a project is a navigation, not a state of the frame (UC-WP-52). */
function ProjectsRoute() {
  const navigate = useNavigate()

  return (
    <ProjectPage
      onOpen={project => {
        writeLastProject(project.id)
        navigate(chatPath(project.id))
      }}
    />
  )
}

/**
 * The chat of the project the address names (UC-WP-43). The list is read because it is the only edge that
 * knows a project's name — there is no `GET /api/projects/{id}` and this increment adds none. While it is
 * read the view says so instead of guessing an empty chat.
 *
 * An id the list does not hold is a project that is gone: the address is replaced by the list, which is
 * where a missing project can be added again. A fourth view for that case is what R-WP-1 forbids.
 */
function ChatRoute() {
  const { projectId } = useParams<{ projectId: string }>()
  const { user } = useAppContext()
  const [project, setProject] = useState<Project | null>(null)
  const [unknown, setUnknown] = useState(false)

  useEffect(() => {
    if (projectId === undefined) {
      return
    }
    // the id came from the address, so only this effect may say what it found (no write after unmount)
    let mine = true
    listProjects()
      .then(projects => {
        if (!mine) {
          return
        }
        const found = projects.find(entry => entry.id === projectId) ?? null
        setProject(found)
        setUnknown(found === null)
        if (found !== null) {
          // opening a chat makes it the active project of this browser — the menu's "Chat" needs it (UC-WP-51)
          writeLastProject(found.id)
        }
      })
      .catch(() => {
        if (mine) {
          setUnknown(true)
        }
      })
    return () => {
      mine = false
    }
  }, [projectId])

  if (unknown) {
    return <Navigate to={projectsPath} replace />
  }
  if (project === null) {
    return <p className="empty-state">{content.common.loading}</p>
  }

  return (
    // one row: the file panel takes a column of its own beside the chat instead of covering it, so
    // reading a file never hides the conversation it belongs to (R-WP-33)
    <div className="chat-workspace">
      <ChatPage
        // keyed like the file area: another project is another conversation, so nothing of the previous one
        // — not its history, not its live lines — is carried into the chat that opens (R-WP-11)
        key={project.id}
        project={project}
        user={user}
        // keyed: another project is another checkout, so the file area starts closed at its own root
        files={<ProjectFiles key={project.id} project={project} />}
      />
    </div>
  )
}
