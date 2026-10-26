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

/** The provider names the server accepts — the nine values of its own provider enum. */
export const PROVIDERS = [
  'OLLAMA',
  'OPEN_AI',
  'OPEN_AI_OFFICIAL',
  'LM_STUDIO',
  'GOOGLE_GEMINI',
  'MISTRAL',
  'ANTHROPIC',
  'GITHUB_MODELS',
  'GITHUB_COPILOT',
] as const

export async function getSettings(): Promise<Settings> {
  return request<Settings>('/api/settings')
}

/**
 * A save. Every field is optional because an empty input means <em>unchanged</em> (R-WP-16
 * "leeres Feld = unverändert"): what the server never sees cannot be overwritten, so a value — the key
 * above all — can be set here and never cleared.
 */
export interface SettingsPatch {
  provider?: string
  model?: string
  url?: string
  apiKey?: string
  tokenWindow?: number
  timeoutSeconds?: number
  sendThinking?: boolean
}

export async function updateSettings(patch: SettingsPatch): Promise<Settings> {
  return request<Settings>('/api/settings', { method: 'PUT', body: JSON.stringify(withoutBlanks(patch)) })
}

/** Only fields that carry a value travel — the trim mirrors the server, which stores trimmed too. */
function withoutBlanks(patch: SettingsPatch): SettingsPatch {
  const sent: Record<string, unknown> = {}
  for (const [field, value] of Object.entries(patch)) {
    const blank = value === undefined || value === null || (typeof value === 'string' && value.trim() === '')
    if (!blank) {
      sent[field] = typeof value === 'string' ? value.trim() : value
    }
  }
  return sent as SettingsPatch
}
