import { request } from '../shared/api.client'

/** The two read roots of the file area, in the server's own words (R-WP-14). */
export type FileRoot = 'checkout' | 'tmp'

export interface FileEntry {
  name: string
  directory: boolean
  size: number
}

export interface DirectoryResponse {
  kind: 'directory'
  root: FileRoot
  /** Relative to its root; empty means the root itself. Never an absolute path. */
  path: string
  entries: FileEntry[]
  truncated: boolean
}

export interface FileResponse {
  kind: 'file'
  root: FileRoot
  path: string
  /** `null` when the server named the file binary — the hint stands in for the content. */
  text: string | null
  size: number
  binary: boolean
  truncated: boolean
}

/** Exactly one of two shapes, told apart by `kind` — so no caller guesses from optional fields. */
export type FilesResponse = DirectoryResponse | FileResponse

/**
 * The read edge of the file area: one directory level or one file, relative to one root. It is the
 * only edge there is — writing has no client because it has no endpoint (R-WP-14). A rejected path
 * (400) throws with the server's own message; nothing here retries, a repeated attempt would be
 * rejected again.
 */
export async function readFiles(projectId: string, root: FileRoot, path = ''): Promise<FilesResponse> {
  // per segment, not per path: `URLSearchParams` would encode every `/` to `%2F` and a proxy that
  // objects to an encoded separator would then be a second, invisible rule on top of the server's guard
  const query = `root=${root}&path=${path.split('/').map(encodeURIComponent).join('/')}`
  return request<FilesResponse>(`/api/projects/${encodeURIComponent(projectId)}/files?${query}`)
}
