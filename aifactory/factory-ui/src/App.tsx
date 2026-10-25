import { useState } from 'react'

import { ViewTabs, type ViewName } from './components/ViewTabs'
import type { Project } from './api/projects'
import { ChatView } from './views/ChatView'
import { ProjectsView } from './views/ProjectsView'
import { SettingsView } from './views/SettingsView'
import './styles.css'

export default function App() {
  const [view, setView] = useState<ViewName>('projects')
  const [selected, setSelected] = useState<Project | null>(null)

  return (
    <div className="app">
      <header className="app-header">
        <span className="app-title">Open AI Factory</span>
        <ViewTabs active={view} onSelect={setView} />
      </header>
      <main>
        {view === 'projects' && <ProjectsView onOpen={setSelected} />}
        {view === 'chat' && <ChatView project={selected} />}
        {view === 'settings' && <SettingsView />}
      </main>
    </div>
  )
}
