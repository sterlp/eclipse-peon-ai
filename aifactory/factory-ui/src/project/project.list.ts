import { useCallback, useEffect, useState } from 'react'

import { listProjects } from './project.client'
import { errorMessage } from '../shared/api.client'
import type { Project } from '../shared/dto'

/** The registry as the frame holds it: what it knows, why it might know nothing, how to ask again. */
export interface ProjectList {
  projects: Project[]
  error: string | null
  /**
   * An answer has arrived — **also** a refused one. A route that waits for the list has to know the read is
   * over, or a failing registry would leave it on its loading line forever.
   */
  loaded: boolean
  reload: () => Promise<void>
}

/**
 * The one owner of the project list (R-WP-38): every view of the frame reads it from here, so tree and page
 * cannot disagree about a project that was just added or removed. Not a store — the registry on the server
 * is the authority, this is only its picture while the frame runs.
 */
export function useProjectList(): ProjectList {
  const [projects, setProjects] = useState<Project[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loaded, setLoaded] = useState(false)

  // the update lands after the await: the registry is an external system and never answers synchronously
  const reload = useCallback(
    () =>
      listProjects()
        .then(answers => {
          setProjects(answers)
          setError(null)
          setLoaded(true)
        })
        .catch(reason => {
          // an unreadable registry says so once, at the page that can act on it — the sider stays silent
          setError(errorMessage(reason))
          setLoaded(true)
        }),
    [],
  )

  useEffect(() => {
    // mount = synchronize with the registry
    void reload()
  }, [reload])

  return { projects, error, loaded, reload }
}
