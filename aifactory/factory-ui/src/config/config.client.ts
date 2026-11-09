import { request } from '../shared/api.client'

/** Where the effective value came from — the file wins, the environment is the start value (R-WP-9). */
export type KeySource = 'FILE' | 'ENV' | 'NONE'

/**
 * The masked config as the server delivers it: the documented fields plus the key <em>status</em> — there is
 * no field carrying a key, so the UI cannot display one by accident. `think` is the Dev slot's value read at
 * the base level (R-WP-49), not a copy of it.
 */
export interface Settings {
  provider: string
  model: string | null
  think: string | null
  url: string | null
  tokenWindow: number
  timeoutSeconds: number
  sendThinking: boolean
  /** R-WP-51: the advanced trio. `maxTokens` answers as a number and is sent as text — only text can say "empty". */
  maxTokens: number | null
  queryParams: string | null
  headerParams: string | null
  keySet: boolean
  keySource: KeySource
}

/**
 * One provider as the core knows it (UC-WP-62): the base URL it falls back to and what it can do with a
 * think input. No model — the core knows no model default, so the page clears the field instead of
 * offering a value that belongs to another provider (Q2).
 *
 * `thinkSupported` is the yes/no answer the default area needs to disable its switch; `thinkForm` and
 * `thinkValues` are the same answer undimmed, which is what the agent think field is built from
 * (R-WP-23, UC-WP-67).
 */
export interface Provider {
  name: string
  defaultUrl: string | null
  thinkSupported: boolean
  thinkForm: ThinkForm
  thinkValues: string[]
}

/**
 * The form of a think input (R-WP-23): the core's sealed `ThinkSupport` as the wire names it.
 * `UNKNOWN` is the core's honest "not described yet" and is not the same claim as `NONE`.
 */
export type ThinkForm = 'TOGGLE' | 'VALUES' | 'FREE' | 'NONE' | 'UNKNOWN'

/** What a think field needs to build itself: the form and, for `VALUES`, the list it offers. */
export interface ThinkInput {
  form: ThinkForm
  values: string[]
}

/**
 * The think form of one provider answer. Without a provider — the list has not arrived, or the file names
 * one the core does not offer — the form is `UNKNOWN`, which is a text field: the page keeps showing
 * what the file holds instead of narrowing it to a list it cannot justify.
 */
export function thinkInputOf(provider: Provider | undefined): ThinkInput {
  return { form: provider?.thinkForm ?? 'UNKNOWN', values: provider?.thinkValues ?? [] }
}

export async function getSettings(): Promise<Settings> {
  return request<Settings>('/api/settings')
}

/** The providers the page offers — the core's own list, so a new provider needs no change here (D-10). */
export async function listProviders(): Promise<Provider[]> {
  return request<Provider[]>('/api/settings/providers')
}

/**
 * The field names both config levels may send. `trimBody` is typed on this rather than on `object`: a
 * field the server does not know must fail the build, not travel as a typo that silently changes nothing.
 */
export interface ConfigPatch {
  model?: string
  url?: string
  apiKey?: string
  removeApiKey?: boolean
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
export interface SettingsPatch extends ConfigPatch {
  provider?: string
  tokenWindow?: string
  timeoutSeconds?: string
  sendThinking?: string
  /** R-WP-49: text like every other field — empty deletes the Dev slot's think value from its file. */
  think?: string
  /** R-WP-51: `maxTokens` is text for the same reason; the server parses it and refuses garbage by name. */
  maxTokens?: string
  queryParams?: string
  headerParams?: string
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
export function trimBody(patch: ConfigPatch): Record<string, unknown> {
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
