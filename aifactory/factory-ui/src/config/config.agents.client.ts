import { request } from '../shared/api.client'
import { trimBody, type ConfigPatch } from './config.client'

/**
 * One agent slot as the server reads it from its file (R-WP-23). `overridden` is no stored flag but the
 * fact that the file carries at least one config value; the four inheritable fields are `null` when the
 * slot inherits, and the key is a status only — never a value (R-WP-16).
 */
export interface AgentSettings {
  id: string
  name: string
  overridden: boolean
  model: string | null
  url: string | null
  think: string | null
  temperature: string | null
  keySet: boolean
}

/**
 * One save of one slot, on the three states of R-WP-16: the four visible fields always travel as text, so
 * an empty one deletes that field (UC-WP-46) while an absent one would mean "unchanged" — a state the
 * page never intends for a visible field. `overridden: false` is the clear of the whole slot and needs no
 * field at all (UC-WP-45).
 */
export interface AgentPatch extends ConfigPatch {
  overridden: boolean
  think?: string
  temperature?: string
}

export async function listAgents(): Promise<AgentSettings[]> {
  return request<AgentSettings[]>('/api/settings/agents')
}

/** One slot per request: the default level stays a small payload, and a refusal stays nameable (D-12). */
export async function updateAgent(id: string, patch: AgentPatch): Promise<AgentSettings> {
  return request<AgentSettings>('/api/settings/agents/' + encodeURIComponent(id), {
    method: 'PUT',
    body: JSON.stringify(trimBody(patch)),
  })
}
