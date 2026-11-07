import { useEffect } from 'react'
import {
  BrowserRouter,
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
  useParams,
} from 'react-router-dom'

import AppShell from './app.shell'
import { useAppContext } from './app.context'
import {
  chatFilesPath,
  chatPath,
  filesKeyOf,
  navKeyOfPathname,
  projectOfSegment,
  projectSegment,
  projectsPath,
} from './app.paths'
import { ChatPage } from '../chat/chat.page'
import { ConfigPage } from '../config/config.page'
import { ProjectFiles } from '../project/project.files'
import { ProjectPage } from '../project/project.page'
import { content } from '../shared/content'
import { readLastProject, writeLastProject } from '../shared/last.project'

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
 * The file area is a child of its chat address (`chat/:project/files`) and not a fourth view: the child
 * route renders nothing and the page reads `open` from the path (R-WP-38, UC-WP-70 G2). The team tiles are
 * no address at all, and the insight opens and closes without ever changing it (R-WP-1).
 *
 * `/chat` on its own is the one address that is a question and not a view: it answers with the project this
 * browser last opened, or with the list (R-WP-1, UC-WP-51). It is a redirect and not a fourth page.
 */
export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<Navigate to={projectsPath} replace />} />
        <Route path="projects" element={<ProjectsRoute />} />
        <Route path="chat" element={<LastChatRoute />} />
        <Route path="chat/:project" element={<ChatRoute />}>
          {/* the unit is a sub-path of the chat address, so its route hangs under this one and renders
              nothing: `open` is one reading of the pathname (R-WP-38, UC-WP-70 G2). That a running turn
              survives the switch is pinned by an enforced remount in `aRunningTurnSurvivesTheUnitClick`,
              never by this shape — see docs/aifactory-architektur.md, „Rahmen, Adresse, Einheit" */}
          <Route path="files" element={<></>} />
        </Route>
        <Route path="settings" element={<ConfigPage />} />
        <Route path="*" element={<Navigate to={projectsPath} replace />} />
      </Route>
    </Routes>
  )
}

/** The list as the frame reads it — the tree beside it shows the same projects for the same reason (D1). */
function ProjectsRoute() {
  const { projects, listError, reloadProjects, openProject } = useAppContext()

  return (
    <ProjectPage
      projects={projects}
      listError={listError}
      onOpen={openProject}
      onReload={reloadProjects}
    />
  )
}

/**
 * The chat this browser was working on (UC-WP-51): an address, a bookmark, a redirect all land on it. With
 * nothing stored the address leads to the list, where a project can be opened — guessing one would be a
 * second answer beside the URL (R-WP-1).
 */
function LastChatRoute() {
  const last = readLastProject()
  return <Navigate to={last === null ? projectsPath : chatPath(last)} replace />
}

/**
 * The chat of the project the address names (UC-WP-43). A name becomes a project through the list the frame
 * already read — there is no `GET /api/projects/{name}`, and none is added here (R-WP-38). While that read is
 * still open the view says so instead of guessing an empty chat.
 *
 * A segment the loaded list does not hold is a project that is gone: the address is replaced by the list,
 * where a missing project can be added again — a fourth view for that case is what R-WP-1 forbids.
 */
function ChatRoute() {
  const { project: segment } = useParams<{ project: string }>()
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const { user, projects, loaded } = useAppContext()
  const project = segment === undefined ? null : projectOfSegment(projects, segment)

  /**
   * Openness of the file area is a reading of the address — the same reader the sider uses, so the panel
   * keeps no state of its own and a reload or a bookmark shows the very same thing (R-WP-38).
   */
  const filesOpen = segment !== undefined && navKeyOfPathname(pathname) === filesKeyOf(segment)

  /** A push in both directions, so the browser's back closes the area again; never a replace. */
  function toggleFiles(): void {
    if (segment === undefined) {
      return
    }
    navigate(filesOpen ? chatPath(segment) : chatFilesPath(segment))
  }

  // a deep link opens a chat nobody clicked, and the bare `/chat` address needs to know it was here (UC-WP-51)
  useEffect(() => {
    if (project !== null) {
      writeLastProject(projectSegment(project))
    }
  }, [project])

  if (project === null) {
    // only a read registry can call a project gone; with no answer yet there is nothing to conclude (R-WP-1)
    return loaded ? (
      <Navigate to={projectsPath} replace />
    ) : (
      <p className="empty-state">{content.common.loading}</p>
    )
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
        // keyed: another project is another checkout, so no tree of the previous one is on the screen
        files={
          <ProjectFiles
            key={project.id}
            project={project}
            open={filesOpen}
            onToggle={toggleFiles}
          />
        }
      />
    </div>
  )
}
