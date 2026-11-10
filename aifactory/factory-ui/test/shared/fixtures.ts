import type { ProjectDto } from '../../src/shared/server-api'

export function project(overrides: Partial<ProjectDto> = {}): ProjectDto {
  return {
    id: 'demo-1a2b3c4',
    name: 'demo',
    gitUrl: 'https://git.example.com/acme/demo.git',
    localPath: '/home/me/.aifactory/projects/demo',
    state: 'READY',
    ...overrides,
  }
}
