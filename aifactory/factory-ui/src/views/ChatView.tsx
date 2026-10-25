import type { Project } from '../api/projects'

interface Props {
  project: Project | null
}

/**
 * Chat placeholder — the runtime and the shared chat arrive with the next increment. What is fixed
 * here: there is exactly one agent per project and no agent switcher.
 */
export function ChatView({ project }: Props) {
  return (
    <section className="view" aria-label="Chat">
      <h1>Chat</h1>
      {project === null ? (
        <p className="empty-state">Kein Projekt geöffnet</p>
      ) : (
        <>
          <p className="hint">Projekt: {project.name}</p>
          <p className="empty-state">Noch kein Agent — Chat folgt im nächsten Inkrement.</p>
        </>
      )}
    </section>
  )
}
