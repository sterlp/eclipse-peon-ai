import { useState } from 'react'

import { AppTabs, type ViewName } from './app.tabs'
import { ChatPage } from '../chat/chat.page'
import { ConfigPage } from '../config/config.page'
import { ProjectFiles } from '../project/project.files'
import { ProjectPage } from '../project/project.page'
import type { Project } from '../shared/dto'
import '../styles.css'

export default function AppShell() {
  const [view, setView] = useState<ViewName>('projects')
  const [selected, setSelected] = useState<Project | null>(null)

  return (
    <div className="app">
      <header className="app-header">
        <span className="app-title">Open AI Factory</span>
        <AppTabs active={view} onSelect={setView} />
      </header>
      <main>
        {view === 'projects' && (
          <ProjectPage
            // opening a project opens its chat — the conversation is the project's, not the tab's
            onOpen={opened => {
              setSelected(opened)
              setView('chat')
            }}
          />
        )}
        {view === 'chat' && (
          <ChatPage
            project={selected}
            // keyed: another project is another checkout, so the file area starts closed at its own root
            files={selected !== null ? <ProjectFiles key={selected.id} project={selected} /> : undefined}
          />
        )}
        {view === 'settings' && <ConfigPage />}
      </main>
    </div>
  )
}
