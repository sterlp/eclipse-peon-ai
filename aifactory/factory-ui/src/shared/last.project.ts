/**
 * The project this browser last opened (R-WP-1, UC-WP-51) — what the bare `/chat` address answers with, and
 * the only view preference the factory keeps. The URL stays the authority for every view (UC-WP-52), so a
 * reload never reads this. Browser and not server: there is no login (R-WP-17), and a field on the project
 * would be a second answer to a question the runtime already answers (R-WP-11).
 */
const STORAGE_KEY = 'aifactory.lastProject'

/** The stored segment of the last opened project, or `null` — none is a real state, not a missing one. */
export function readLastProject(): string | null {
  const stored = localStorage.getItem(STORAGE_KEY)?.trim() ?? ''
  return stored === '' ? null : stored
}

/**
 * Remember which project this browser was working on; a blank segment stores nothing. Kept is the project's
 * name (`projectSegment`) — the one language a bookmark also speaks (UC-WP-51), so a browser still holding an
 * id names no project and `/chat` leads to the list instead of guessing one.
 */
export function writeLastProject(segment: string): void {
  if (segment.trim() === '') {
    return
  }
  localStorage.setItem(STORAGE_KEY, segment)
}
