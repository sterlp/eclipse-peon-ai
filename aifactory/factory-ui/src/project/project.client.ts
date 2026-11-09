import { request } from '../shared/api.client'
import type { ProjectDto } from '../shared/server-api'

export async function listProjects(): Promise<ProjectDto[]> {
  return request<ProjectDto[]>('/api/projects')
}

export async function addProject(gitUrl: string): Promise<ProjectDto> {
  return request<ProjectDto>('/api/projects', { method: 'POST', body: JSON.stringify({ gitUrl }) })
}

export async function removeProject(id: string): Promise<void> {
  return request<void>(`/api/projects/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
