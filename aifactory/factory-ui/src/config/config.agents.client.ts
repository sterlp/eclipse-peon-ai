import { request } from '../shared/api.client'
import { trimBody } from './config.client'
import type { AgentSettingsDto, AgentSettingsUpdateRequest } from '../shared/server-api'

/**
 * One save of one slot, on the three states of R-WP-16: the visible fields always travel as text, so an
 * empty one deletes that field (UC-WP-46) while an absent one would mean "unchanged" — a state the page
 * never intends for a visible field. `overridden: false` is the clear of the whole slot and needs no field
 * at all (UC-WP-45); the wire reads an absent flag as "not overriding", so it is optional there and stated
 * by the page on every save here.
 */
export type AgentPatch = AgentSettingsUpdateRequest & { overridden: boolean }

export async function listAgents(): Promise<AgentSettingsDto[]> {
  return request<AgentSettingsDto[]>('/api/settings/agents')
}

/** One slot per request: the default level stays a small payload, and a refusal stays nameable (D-12). */
export async function updateAgent(id: string, patch: AgentPatch): Promise<AgentSettingsDto> {
  return request<AgentSettingsDto>('/api/settings/agents/' + encodeURIComponent(id), {
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
