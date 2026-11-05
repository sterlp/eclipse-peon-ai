/**
 * The key of this browser's own name — the whole identity of the prototype (R-WP-17). Documented as the
 * wire's contract, so a test can pin it: renaming the key would silently empty every existing browser.
 */
const STORAGE_KEY = 'aifactory.user.name'

/**
 * The name this browser typed, or `null`. `null` is a real state and not a missing one: without a name the
 * chat sends no prefix and shows its role label (R-WP-25). Trimmed here, so a stored blank reads as none.
 */
export function readUserName(): string | null {
  const stored = localStorage.getItem(STORAGE_KEY)
  const name = stored?.trim() ?? ''
  return name === '' ? null : name
}

/**
 * Keep the name for the next start of this browser, or forget it: nothing left after trimming is no
 * identity, and a blank entry would read back as a name somewhere else. No login, no server (R-WP-17).
 */
export function writeUserName(name: string): void {
  const trimmed = name.trim()
  if (trimmed === '') {
    localStorage.removeItem(STORAGE_KEY)
    return
  }
  localStorage.setItem(STORAGE_KEY, trimmed)
}
