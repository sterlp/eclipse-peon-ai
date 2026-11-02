import { request } from '../shared/api.client'

/** Where the effective value came from — the file wins, the environment is the start value (R-WP-9). */
export type KeySource = 'FILE' | 'ENV' | 'NONE'

/**
 * The masked config as the server delivers it: the six documented fields plus the key
 * <em>status</em> — there is no field carrying a key, so the UI cannot display one by accident.
 */
export interface Settings {
  provider: string
  model: string | null
  url: string | null
  tokenWindow: number
  timeoutSeconds: number
  sendThinking: boolean
  keySet: boolean
  keySource: KeySource
}

/**
 * One provider as the core knows it (UC-WP-62): the base URL it falls back to and whether it can carry a
 * think input. No model — the core knows no model default, so the page clears the field instead of
 * offering a value that belongs to another provider (Q2).
 */
export interface Provider {
  name: string
  defaultUrl: string | null
  thinkSupported: boolean
}

export async function getSettings(): Promise<Settings> {
  return request<Settings>('/api/settings')
}

/** The providers the page offers — the core's own list, so a new provider needs no change here (D-10). */
export async function listProviders(): Promise<Provider[]> {
  return request<Provider[]>('/api/settings/providers')
}

/**
 * A save. Every visible field is text because the wire carries *three* states (R-WP-16, UC-WP-46):
 * absent leaves the file alone, empty *deletes* the value, a value sets it. A number or a boolean
 * has no empty state, which is why token window, timeout and think are text here and parsed by the
 * server — an unparsable value comes back as a 400 naming the field.
 *
 * The key is the documented exception: write-only and never prefilled, so empty means unchanged and
 * only `removeApiKey` deletes it (UC-WP-24 GIVEN 2, UC-WP-63).
 */
export interface SettingsPatch {
  provider?: string
  model?: string
  url?: string
  apiKey?: string
  tokenWindow?: string
  timeoutSeconds?: string
  sendThinking?: string
  removeApiKey?: boolean
}

export async function updateSettings(patch: SettingsPatch): Promise<Settings> {
  return request<Settings>('/api/settings', {
    method: 'PUT',
    body: JSON.stringify(trimBody(patch)),
  })
}

/**
 * Trims what travels and throws nothing away: an empty string is a field the user cleared, so dropping
 * blanks would make that state unreachable (UC-WP-46). Only the key field is left out when empty — for
 * it, empty is not a value but a request to leave the stored key alone. The agent section saves by the
 * same rule, which is why this is the one body-shaper of the component (R-WP-16).
 */
export function trimBody(patch: object): Record<string, unknown> {
  const sent: Record<string, unknown> = {}
  for (const [field, value] of Object.entries(patch)) {
    if (value === undefined || value === null) {
      continue
    }
    if (field === 'apiKey' && value === '') {
      continue
    }
    sent[field] = typeof value === 'string' ? value.trim() : value
  }
  return sent
}
