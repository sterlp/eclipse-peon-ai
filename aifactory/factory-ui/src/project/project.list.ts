import { useCallback, useEffect, useState } from 'react'

import { listProjects } from './project.client'
import { errorMessage } from '../shared/api.client'
import type { Project } from '../shared/dto'

/** The registry as the frame holds it: what it knows, why it might know nothing, how to ask again. */
export interface ProjectList {
  projects: Project[]
  error: string | null
  reload: () => Promise<void>
}

/**
 * The one owner of the project list (R-WP-38): the sider draws an entry from it and the list page draws its
 * rows from the same read, so the two cannot disagree about a project that was just added or removed.
 *
 * Not a store: the registry on the server is the authority and this is its picture, held only while the
 * frame runs and asked again after every add and remove.
 */
export function useProjectList(): ProjectList {
  const [projects, setProjects] = useState<Project[]>([])
  const [error, setError] = useState<string | null>(null)

  // the update lands after the await: the registry is an external system and never answers synchronously
  const reload = useCallback(
    () =>
      listProjects()
        .then(loaded => {
          setProjects(loaded)
          setError(null)
        })
        .catch(reason => {
          // an unreadable registry says so once, at the page that can act on it — the sider stays silent
          setError(errorMessage(reason))
        }),
    [],
  )

  useEffect(() => {
    // mount = synchronize with the registry
    void reload()
  }, [reload])

  return { projects, error, reload }
}
