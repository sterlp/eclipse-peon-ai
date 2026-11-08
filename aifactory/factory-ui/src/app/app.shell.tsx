import { useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Layout } from 'antd'

import { AppSider } from './app.sider'
import { AppSiderToggle } from './app.sider.toggle'
import { AppThemeToggle } from './app.theme.toggle'
import { AppTop } from './app.top'
import { AppUser } from './app.user'
import {
  chatFilesPath,
  chatPath,
  navKeyOfPathname,
  pageKeyOfPathname,
  projectSegment,
} from './app.paths'
import type { AppContext } from './app.context'
import { content } from '../shared/content'
import { useProjectList } from '../project/project.list'
import { writeLastProject } from '../shared/last.project'
import { readUserName } from '../shared/user.name'
import type { Project } from '../shared/dto'
import '../styles.css'

/**
 * The frame of the app (R-WP-1, R-WP-38, R-WP-43): the header with the brand, the three pages, the colour
 * switch and this browser's name; the tree of projects in the sider beside the view. Both menus are a
 * reading of the URL and hold nothing of their own — an address survives a reload that a component's state
 * would not (UC-WP-43, UC-WP-52), and the two readers of that address live in {@link ./app.paths} together.
 *
 * The brand is a word and not a link, and the header names no project: the header answers **which page**,
 * the tree answers **which project** — one question each, so nothing has to be kept in step between them.
 *
 * Layout, Header and Content are the basis' own (R-WP-39), and so is the sider's responsive behaviour —
 * this file only says which breakpoint and which collapsed width, never how wide that is in pixels. The band
 * carries the **one** handle of the tree (R-WP-44): the tree is folded from the band and not from its own
 * edge, which is why `collapsed` is held here.
 */
export default function AppShell() {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  // the name is read once at startup and lives in the browser: identity is not per project and not a
  // login, so the shell only holds it while the page runs (R-WP-17)
  const [user, setUser] = useState<string | null>(readUserName)
  // one read for the tree and for the list page (D1): a project added on the page appears in the tree
  const { projects, error, loaded, reload } = useProjectList()
  // whether the tree is folded away (R-WP-44): the frame holds the one answer, because two ask for it — the
  // band's handle and the breakpoint listener of the basis, which answers `onCollapse(…, 'responsive')`
  // beside a controlled value (measured in `layout/Sider.js`)
  const [collapsed, setCollapsed] = useState(false)

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
    loaded,
    reloadProjects: reload,
    openProject,
  } satisfies AppContext

  return (
    <Layout className="app">
      <Layout.Header className="app-header">
        <span className="app-title">{content.brand}</span>
        <AppTop active={pageKeyOfPathname(pathname)} onNavigate={navigate} />
        <AppThemeToggle />
        <AppSiderToggle collapsed={collapsed} onToggle={() => setCollapsed(value => !value)} />
        <AppUser user={user} onChange={setUser} />
      </Layout.Header>
      <Layout className="app-body">
        <AppSider
          projects={projects}
          activeKey={navKeyOfPathname(pathname)}
          collapsed={collapsed}
          onCollapse={setCollapsed}
          onOpen={openProject}
          onFiles={openFiles}
        />
        <Layout.Content className="app-main">
          <Outlet context={context} />
        </Layout.Content>
      </Layout>
    </Layout>
  )
}
