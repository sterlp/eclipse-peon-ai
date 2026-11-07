/**
 * The project this browser last opened (R-WP-1, UC-WP-51) — what the `/chat` address answers with when it
 * names no project, and the only view preference the factory keeps. The URL stays the authority for every
 * view (UC-WP-52): this entry answers "which project did I mean?" and nothing else, so a reload never reads
 * it.
 *
 * Browser, not server: there is no login (R-WP-17), so "last active" belongs to the viewer, and a field on
 * the project would be a second answer to a question the runtime already answers (R-WP-11).
 */
const STORAGE_KEY = 'aifactory.lastProject'

/** The stored segment of the last opened project, or `null` — none is a real state, not a missing one. */
export function readLastProject(): string | null {
  const stored = localStorage.getItem(STORAGE_KEY)?.trim() ?? ''
  return stored === '' ? null : stored
}

/**
 * Remember which project this browser was working on; a blank segment stores nothing. What is kept is a
 * project's name (`projectSegment`), so a bookmark and this answer speak one language (UC-WP-51). An older
 * browser can hold an id: it names no project, so `/chat` leads to the list instead of guessing one.
 */
export function writeLastProject(segment: string): void {
  if (segment.trim() === '') {
    return
  }
  localStorage.setItem(STORAGE_KEY, segment)
}
