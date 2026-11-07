/**
 * Every `hr` a log draws that is **not** content of an answer (R-WP-29). A line the model wrote with `---`
 * inside a reply belongs to that reply; a separator belongs between two voices. One place decides which is
 * which, so a proof of "no line between two rows" cannot be satisfied by a selector that would count a
 * model-drawn rule as ours — or overlook one. UC-WP-55 GIVEN 2 asserts this very list to be empty: the
 * accents on the sides of the rows are what separate two voices (R-WP-29).
 */
export function separatorsOf(log: HTMLElement): HTMLElement[] {
  return [...log.querySelectorAll('hr')].filter(rule => rule.closest('.chat-markdown') === null)
}
