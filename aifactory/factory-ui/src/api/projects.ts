import { ApiRequestError, request } from './client'

export interface Project {
  id: string
  name: string
  gitUrl: string
  localPath: string
  state: string
}

export async function listProjects(): Promise<Project[]> {
  return request<Project[]>('/api/projects')
}

export async function addProject(gitUrl: string): Promise<Project> {
  return request<Project>('/api/projects', { method: 'POST', body: JSON.stringify({ gitUrl }) })
}

export async function removeProject(id: string): Promise<void> {
  return request<void>(`/api/projects/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

/**
 * What the user gets to read when adding fails: the git output the server sent, or the transport
 * error. A false negative ("added") would be the expensive bug here, so nothing is swallowed.
 */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    return error.apiError?.message ?? error.message
  }
  return error instanceof Error ? error.message : String(error)
}
