/**
 * Which name of a refusal which half of the screen answers (R-WP-56, R-WP-62). The lists live in one module
 * beside the two forms because a list that travels between them may not be written twice: what a page frames
 * with a mark at its control is exactly what its panel has to leave alone, and a name neither list holds is
 * the panel's to say — a rule added on the server can then never be lost between the two display paths (R8).
 */

/** The settings form's own fields — the ones the server answers with a rule of its own (`Wire*`, R-WP-55). */
export const SETTINGS_ERROR_FIELDS = [
  'provider',
  'tokenWindow',
  'timeoutSeconds',
  'sendThinking',
  'maxTokens',
  'queryParams',
  'headerParams',
  'apiKey',
]

/** The fields an agent slot marks on its own card. */
export const SLOT_ERROR_FIELDS = ['temperature', 'extraBody', 'apiKey']

/**
 * Everything the settings page frames — its own form and the slots within it. `apiKey` is asked on both
 * sides and answered once. A refusal that names only names from this list keeps the page's panel closed
 * (UC-WP-99 GIVEN 3): every part of it already stands at its field.
 */
export const PAGE_ERROR_FIELDS = [...SETTINGS_ERROR_FIELDS, ...SLOT_ERROR_FIELDS]
