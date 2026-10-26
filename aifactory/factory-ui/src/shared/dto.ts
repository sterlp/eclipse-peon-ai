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
