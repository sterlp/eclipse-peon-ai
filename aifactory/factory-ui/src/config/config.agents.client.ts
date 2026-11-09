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
  /** R-WP-52: the slot's own `extra_body` and tool allowlist — `null` when the slot inherits. */
  extraBody: string | null
  tools: string | null
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
  /** R-WP-52: text on the three states like every other field — empty deletes the key from the file. */
  extraBody?: string
  tools?: string
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

/**
 * The tools that would run with the typed allowlist (R-WP-52, UC-WP-88). A query read with no body: it
 * changes nothing, so there is nothing to protect in a body — and an empty entry asks nothing for, the
 * server answers the whole set. The core is the one that interprets the entry (D9).
 */
export async function previewTools(id: string, allowlist: string): Promise<string[]> {
  const asked = allowlist.trim() === '' ? '' : '?allowlist=' + encodeURIComponent(allowlist.trim())
  const tools = await request<string[]>(
    '/api/settings/agents/' + encodeURIComponent(id) + '/tools' + asked,
  )
  // no body and an empty list are the same answer for the preview
  return tools ?? []
}
