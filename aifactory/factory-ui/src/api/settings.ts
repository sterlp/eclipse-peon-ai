import { request } from './client'

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

export async function getSettings(): Promise<Settings> {
  return request<Settings>('/api/settings')
}
