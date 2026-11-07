import type { Project } from '../shared/dto'

/** The address of the project list. */
export const projectsPath = '/projects'

/** The address of the settings. */
export const settingsPath = '/settings'

/** The chat on its own: it answers with the project this browser last opened (UC-WP-51, R-WP-1). */
export const chatRoot = '/chat'

/** The key of the sider's first entry — the list itself, not a fourth page (R-WP-38). */
export const siderRootKey = 'root'

/** The address of one project's Jon-Chat (UC-WP-52). */
export function chatPath(segment: string): string {
  return `${chatRoot}/${segment}`
}

/**
 * The one producer of the part of the address that names a project (R-WP-38): the **name**, so the address
 * reads as what the tree shows (C-3). The backend keeps a name single — a second folder of that name is
 * refused case-insensitively (`ProjectService.add`) — and its `SAFE_NAME` keeps it free of `/` and spaces, so
 * a name is always a legal path segment. The sider's keys, the navigation and what this browser remembers as
 * its last project all read it here — the alternative is three places that have to agree about one string.
 */
export function projectSegment(project: Project): string {
  return project.name
}

/**
 * The project an address segment names: the name first, the id second. The second reading is what keeps a
 * bookmark written before the address spoke names working; it is dropped once no such bookmark is expected.
 * `null` is a project the registry does not hold — a gone project, which is where the address leads to the
 * list rather than to a fourth page (R-WP-1).
 */
export function projectOfSegment(projects: Project[], segment: string): Project | null {
  return (
    projects.find(entry => projectSegment(entry) === segment) ??
    projects.find(entry => entry.id === segment) ??
    null
  )
}

/** The sider key of one project — prefixed, because a project is free to be called `root` or `projects`. */
export function projectKeyOf(segment: string): string {
  return `project:${segment}`
}

/**
 * Which entry of the sider an address names (UC-WP-52, R-WP-1): a reading of the path and nothing else, so
 * the mark survives a reload. An address that names no entry — `/settings`, the bare `/chat` — marks
 * nothing, which is the honest answer rather than a guess at the first entry.
 */
export function navKeyOfPathname(pathname: string): string | null {
  if (pathname === projectsPath) {
    return siderRootKey
  }
  if (!pathname.startsWith(`${chatRoot}/`)) {
    return null
  }
  const [segment = ''] = pathname.slice(chatRoot.length + 1).split('/')
  return segment === '' ? null : projectKeyOf(segment)
}
