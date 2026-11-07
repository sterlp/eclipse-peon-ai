import { useOutletContext } from 'react-router-dom'

import type { Project } from '../shared/dto'

/**
 * What the frame hands to the view inside it: this browser's name (R-WP-17) and the project list the frame
 * already read. The list arrives from here because the sider draws its entries from the same read — a page
 * that fetched its own copy would answer a different question than the tree beside it (R-WP-38).
 */
export interface AppContext {
  user: string | null
  projects: Project[]
  /** Why the tree is empty, when it is empty because the registry could not be read. */
  listError: string | null
  /** The frame's read is over — an answer, refused or not. Until then a view knows nothing (R-WP-38). */
  loaded: boolean
  reloadProjects: () => Promise<void>
  /** Remembers the project as this browser's last one and opens its chat (UC-WP-51, UC-WP-52). */
  openProject: (project: Project) => void
}

/** The frame's state as the view reads it. */
export function useAppContext(): AppContext {
  return useOutletContext<AppContext>()
}
