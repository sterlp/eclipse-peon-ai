import { useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Button, Layout } from 'antd'

import { AppSider } from './app.sider'
import { AppUser } from './app.user'
import {
  chatFilesPath,
  chatPath,
  navKeyOfPathname,
  projectSegment,
  projectsPath,
  settingsPath,
} from './app.paths'
import type { AppContext } from './app.context'
import { content } from '../shared/content'
import { useProjectList } from '../project/project.list'
import { writeLastProject } from '../shared/last.project'
import { readUserName } from '../shared/user.name'
import type { Project } from '../shared/dto'
import '../styles.css'

/**
 * The frame of the app (R-WP-1, R-WP-38): the header with the brand, this browser's name and Settings; the
 * tree of projects in the sider beside the view. Which entry of that tree shows is read from the URL and
 * held nowhere else — an address survives a reload that a component's state would not (UC-WP-43, UC-WP-52).
 *
 * The brand is a word and not a link, and the header carries no project entry: the tree says where the
 * viewer is, and a second way to the same page would be a second answer to keep in step.
 *
 * Layout, Header and Content are the basis' own (R-WP-39), and so is the sider's responsive behaviour —
 * this file only says which breakpoint and which collapsed width, never how wide that is in pixels.
 */
export default function AppShell() {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  // the name is read once at startup and lives in the browser: identity is not per project and not a
  // login, so the shell only holds it while the page runs (R-WP-17)
  const [user, setUser] = useState<string | null>(readUserName)
  // one read for the tree and for the list page (D1): a project added on the page appears in the tree
  const { projects, error, reload } = useProjectList()

  function openProject(project: Project): void {
    const segment = projectSegment(project)
    // the address is the answer, but `/chat` on its own needs to know what this browser opened (UC-WP-51)
    writeLastProject(segment)
    navigate(chatPath(segment))
  }

  /**
   * The second handle to the same place: the file area is an address of its own (R-WP-38), and `/chat` on
   * its own still answers with the chat — the area is a chosen address, not a place to return to.
   */
  function openFiles(project: Project): void {
    const segment = projectSegment(project)
    writeLastProject(segment)
    navigate(chatFilesPath(segment))
  }

  const context = {
    user,
    projects,
    listError: error,
    reloadProjects: reload,
    openProject,
  } satisfies AppContext

  return (
    <Layout className="app">
      <Layout.Header className="app-header">
        <span className="app-title">{content.brand}</span>
        <AppUser user={user} onChange={setUser} />
        {/* outside the tree on purpose: Settings is a page, the tree answers which project (R-WP-38) */}
        <Button
          className="app-settings"
          type="text"
          aria-current={pathname.startsWith(settingsPath) ? 'page' : undefined}
          onClick={() => navigate(settingsPath)}
        >
          {content.nav.settings}
        </Button>
      </Layout.Header>
      <Layout className="app-body">
        <AppSider
          projects={projects}
          activeKey={navKeyOfPathname(pathname)}
          onOpen={openProject}
          onProjects={() => navigate(projectsPath)}
          onFiles={openFiles}
        />
        <Layout.Content className="app-main">
          <Outlet context={context} />
        </Layout.Content>
      </Layout>
    </Layout>
  )
}
