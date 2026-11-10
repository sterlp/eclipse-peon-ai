import { request } from '../shared/api.client'
import type {
  AgentSettingsUpdateRequest,
  ModelListRequest,
  ProviderDefaultsDto,
  SettingsDto,
  SettingsUpdateRequest,
  ThinkForm,
} from '../shared/server-api'

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
export function thinkInputOf(provider: ProviderDefaultsDto | undefined): ThinkInput {
  return { form: provider?.thinkForm ?? 'UNKNOWN', values: provider?.thinkValues ?? [] }
}

/**
 * The masked config (R-WP-16): no field carries a key, so the UI cannot display one by accident. `think` is
 * the Dev slot's value read at the base level (R-WP-49), not a copy of it.
 */
export async function getSettings(): Promise<SettingsDto> {
  return request<SettingsDto>('/api/settings')
}

/**
 * The providers the page offers — the core's own list, so a new provider needs no change here (D-10). None
 * names a model: the core knows no model default, so the page clears the field instead of offering a value
 * that belongs to another provider (Q2). `thinkSupported` is the yes/no answer the default area disables its
 * switch from, `thinkForm` and `thinkValues` the same answer undimmed for the agent field (UC-WP-67).
 */
export async function listProviders(): Promise<ProviderDefaultsDto[]> {
  return request<ProviderDefaultsDto[]>('/api/settings/providers')
}

/**
 * The models of one connection (UC-WP-86). A POST although it only reads: the endpoint and the slot belong in
 * a body and not in a query string or an access log, and the key belongs in neither — the server reads it from
 * the config. A field the form holds empty travels empty: the list is meant to predict the connection a save
 * would build. A connection that cannot answer is answered as an empty list, so the caller has nothing to
 * report and the model field simply stays a text field.
 */
export async function listModels(query: ModelListRequest): Promise<string[]> {
  const ids = await request<string[]>('/api/settings/models', {
    method: 'POST',
    body: JSON.stringify(query),
  })
  // no body and an empty list are the same answer for the field
  return ids ?? []
}

export async function updateSettings(patch: SettingsUpdateRequest): Promise<SettingsDto> {
  return request<SettingsDto>('/api/settings', {
    method: 'PUT',
    body: JSON.stringify(trimBody(patch)),
  })
}

/**
 * Trims what travels and throws nothing away: an empty string is a field the user cleared, so dropping
 * blanks would make that state unreachable (UC-WP-46). Only the key field is left out when empty — for
 * it, empty is not a value but a request to leave the stored key alone. The agent section saves by the
 * same rule, which is why this is the one body-shaper of the component (R-WP-16).
 *
 * Typed on the two request shapes rather than on `object`: a field the server does not know must fail the
 * build, not travel as a typo that silently changes nothing.
 */
export function trimBody(
  patch: SettingsUpdateRequest | AgentSettingsUpdateRequest,
): Record<string, unknown> {
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
