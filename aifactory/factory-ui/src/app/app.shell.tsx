import { useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Layout } from 'antd'

import { AppNav } from './app.nav'
import { AppUser } from './app.user'
import { chatPath, projectsPath, settingsPath, viewOfPathname, type ViewName } from './app.paths'
import type { AppContext } from './app.context'
import { content } from '../shared/content'
import { readLastProject } from '../shared/last.project'
import { readUserName } from '../shared/user.name'
import '../styles.css'

/**
 * The frame of the app (R-WP-1): title, the three entries, this browser's name, and the routed view. Which
 * entry shows is read from the URL and held nowhere else — an address survives a reload that a component's
 * state would not (UC-WP-43, UC-WP-52).
 *
 * "Chat" is the one entry that has no path of its own: it opens the chat of the project this browser last
 * opened, and with no such project it leads to the list (UC-WP-51). Both answers are a navigation, so the
 * URL names the result either way.
 *
 * Layout, Header and Content are the basis' own (R-WP-39): header, main and the column that carries them
 * are its geometry, and `shared/theme.ts` is where its surfaces meet our colours.
 */
export default function AppShell() {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  // the name is read once at startup and lives in the browser: identity is not per project and not a
  // login, so the shell only holds it while the page runs (R-WP-17)
  const [user, setUser] = useState<string | null>(readUserName)

  function open(view: ViewName): void {
    if (view === 'chat') {
      const last = readLastProject()
      navigate(last === null ? projectsPath : chatPath(last))
      return
    }
    navigate(view === 'settings' ? settingsPath : projectsPath)
  }

  return (
    <Layout className="app">
      <Layout.Header className="app-header">
        <span className="app-title">{content.brand}</span>
        <AppNav active={viewOfPathname(pathname)} onSelect={open} />
        <AppUser user={user} onChange={setUser} />
      </Layout.Header>
      <Layout.Content className="app-main">
        <Outlet context={{ user } satisfies AppContext} />
      </Layout.Content>
    </Layout>
  )
}
