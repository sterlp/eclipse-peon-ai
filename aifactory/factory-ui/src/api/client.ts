/** The single error body of the factory API (`ApiError` on the server side). */
export interface ApiError {
  status: number
  error: string
  message: string
}

/**
 * Thin fetch wrapper — a non-2xx answer always throws with the server's `message`, because that is
 * the text the UI prints next to the input (git output for a failed clone).
 */
export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    headers: { 'content-type': 'application/json' },
    ...init,
  })
  if (!response.ok) {
    throw new ApiRequestError(`request to ${path} failed (${response.status})`, await readError(response))
  }
  if (response.status === 204 || response.headers.get('content-length') === '0') {
    return undefined as T
  }
  return (await response.json()) as T
}

export class ApiRequestError extends Error {
  readonly apiError?: ApiError

  constructor(message: string, apiError?: ApiError) {
    super(message)
    this.apiError = apiError
  }
}

async function readError(response: Response): Promise<ApiError | undefined> {
  try {
    const body = (await response.json()) as ApiError
    if (body && typeof body.message === 'string') {
      return body
    }
    return undefined
  } catch {
    return undefined
  }
}
