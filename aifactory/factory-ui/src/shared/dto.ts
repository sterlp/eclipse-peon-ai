/**
 * A project of the registry, as the server delivers it. Several components need it — `app` to carry
 * the opened project, `chat` to address its conversation, `project` to list them — so it lives in
 * `shared` and not in one component's client.
 */
export interface Project {
  id: string
  name: string
  gitUrl: string
  localPath: string
  state: string
}

/**
 * What one project is doing (R-WP-24): a turn runs, and the inputs waiting behind it. It carries its id
 * because the states of all projects share one connection, and it never carries a state of its own for
 * "waiting" — an input only waits while a turn runs.
 */
export interface ProjectWork {
  projectId: string
  working: boolean
  queued: number
}
