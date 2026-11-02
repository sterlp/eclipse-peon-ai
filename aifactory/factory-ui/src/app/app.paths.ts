/** The views the frame offers (R-WP-1) — the three entries of the menu, and nothing else. */
export type ViewName = 'projects' | 'chat' | 'settings'

/** The address of the project list. */
export const projectsPath = '/projects'

/** The address of the settings. */
export const settingsPath = '/settings'

/** The address of one project's Jon-Chat (UC-WP-52). */
export function chatPath(projectId: string): string {
  return `/chat/${projectId}`
}

/**
 * Which entry shows for a path (UC-WP-52): the URL is the only state of the frame, so this is a pure
 * function of it and not a second memory. Paths the app does not answer itself (the redirect target of an
 * unknown address) read as the project list, which is what the router sends them to.
 */
export function viewOfPathname(pathname: string): ViewName {
  if (pathname.startsWith(settingsPath)) {
    return 'settings'
  }
  if (pathname.startsWith('/chat')) {
    return 'chat'
  }
  return 'projects'
}
