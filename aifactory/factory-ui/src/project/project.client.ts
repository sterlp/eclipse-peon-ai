import { request } from '../shared/api.client'
import type { Project } from '../shared/dto'

export async function listProjects(): Promise<Project[]> {
  return request<Project[]>('/api/projects')
}

export async function addProject(gitUrl: string): Promise<Project> {
  return request<Project>('/api/projects', { method: 'POST', body: JSON.stringify({ gitUrl }) })
}

export async function removeProject(id: string): Promise<void> {
  return request<void>(`/api/projects/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
