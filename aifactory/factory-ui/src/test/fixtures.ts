import type { Project } from '../api/projects'

export function project(overrides: Partial<Project> = {}): Project {
  return {
    id: 'demo-1a2b3c4',
    name: 'demo',
    gitUrl: 'https://git.example.com/acme/demo.git',
    localPath: '/home/me/.aifactory/projects/demo',
    state: 'READY',
    ...overrides,
  }
}
