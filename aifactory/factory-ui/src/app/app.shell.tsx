import { useState } from 'react'

import { AppNav, type ViewName } from './app.nav'
import { AppUser } from './app.user'
import { ChatPage } from '../chat/chat.page'
import { ConfigPage } from '../config/config.page'
import { ProjectFiles } from '../project/project.files'
import { ProjectPage } from '../project/project.page'
import type { Project } from '../shared/dto'
import { readUserName } from '../shared/user.name'
import '../styles.css'

export default function AppShell() {
  const [view, setView] = useState<ViewName>('projects')
  const [selected, setSelected] = useState<Project | null>(null)
  // the name is read once at startup and lives in the browser: identity is not per project and not a
  // login, so the shell only holds it while the page runs (R-WP-17)
  const [user, setUser] = useState<string | null>(readUserName)

  return (
    <div className="app">
      <header className="app-header">
        <span className="app-title">Open AI Factory</span>
        <AppNav active={view} onSelect={setView} />
        <AppUser user={user} onChange={setUser} />
      </header>
      <main className="app-main">
        {view === 'projects' && (
          <ProjectPage
            // opening a project opens its chat — the conversation is the project's, not the view's
            onOpen={opened => {
              setSelected(opened)
              setView('chat')
            }}
          />
        )}
        {view === 'chat' && (
          <ChatPage
            project={selected}
            user={user}
            // keyed: another project is another checkout, so the file area starts closed at its own root
            files={selected !== null ? <ProjectFiles key={selected.id} project={selected} /> : undefined}
          />
        )}
        {view === 'settings' && <ConfigPage />}
      </main>
    </div>
  )
}
