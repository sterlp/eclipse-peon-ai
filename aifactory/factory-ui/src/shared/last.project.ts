/**
 * The project this browser last opened (R-WP-1, UC-WP-51) — the one address the menu entry "Chat" needs,
 * and the only view preference the factory keeps. The URL stays the authority for every view (UC-WP-52):
 * this entry answers "which project did I mean?" and nothing else, so a reload never reads it.
 *
 * Browser, not server: there is no login (R-WP-17), so "last active" belongs to the viewer, and a field on
 * the project would be a second answer to a question the runtime already answers (R-WP-11).
 */
const STORAGE_KEY = 'aifactory.lastProject'

/** The id of the last opened project, or `null` — none is a real state, not a missing one. */
export function readLastProject(): string | null {
  const stored = localStorage.getItem(STORAGE_KEY)?.trim() ?? ''
  return stored === '' ? null : stored
}

/** Remember which project this browser was working on; a blank id is no project and stores nothing. */
export function writeLastProject(id: string): void {
  if (id.trim() === '') {
    return
  }
  localStorage.setItem(STORAGE_KEY, id)
}
