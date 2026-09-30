const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

const TOKEN_KEY = 'compras.token'

export function readToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function storeToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    // Sin almacenamiento disponible la sesion dura solo lo que dure la pestana.
  }
}

export interface ApiErrorBody {
  type: string
  code: string
  message: string
  param?: string | null
}

export class ApiError extends Error {
  readonly status: number
  readonly type: string
  readonly code: string
  readonly param: string | null

  constructor(status: number, body: ApiErrorBody) {
    super(body.message)
    this.status = status
    this.type = body.type
    this.code = body.code
    this.param = body.param ?? null
  }
}

let onUnauthorized: (() => void) | null = null

/** Permite que la sesion se cierre sola cuando el backend responde 401 (token vencido o invalido). */
export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler
}

type Query = Record<string, string | number | boolean | string[] | undefined | null>

function buildUrl(path: string, query?: Query) {
  const url = new URL(`${API_URL}/api${path}`)
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value === undefined || value === null || value === '') continue
    for (const item of Array.isArray(value) ? value : [value]) url.searchParams.append(key, String(item))
  }
  return url
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const json = await response.json()
    if (json?.error) return new ApiError(response.status, json.error)
  } catch {
    // Cuerpo no JSON: se cae al error generico de abajo.
  }
  return new ApiError(response.status, {
    type: 'api_error',
    code: 'respuesta_invalida',
    message: `El servidor respondio con un error inesperado (${response.status}).`,
  })
}

interface RequestOptions {
  query?: Query
  body?: unknown
  formData?: FormData
  idempotent?: boolean
}

async function send(method: string, path: string, options: RequestOptions = {}): Promise<Response> {
  const headers: Record<string, string> = {}
  const token = readToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (options.idempotent) headers['Idempotency-Key'] = crypto.randomUUID()
  let body: BodyInit | undefined
  if (options.formData) {
    body = options.formData
  } else if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.body)
  }

  const response = await fetch(buildUrl(path, options.query), { method, headers, body })
  if (!response.ok) {
    if (response.status === 401 && token) onUnauthorized?.()
    throw await toApiError(response)
  }
  return response
}

async function json<T>(method: string, path: string, options?: RequestOptions): Promise<T> {
  const response = await send(method, path, options)
  return (await response.json()) as T
}

export const api = {
  get: <T>(path: string, query?: Query) => json<T>('GET', path, { query }),
  post: <T>(path: string, body?: unknown, options?: { idempotent?: boolean }) =>
    json<T>('POST', path, { body, idempotent: options?.idempotent ?? true }),
  put: <T>(path: string, body: unknown) => json<T>('PUT', path, { body }),
  patch: <T>(path: string, body?: unknown) => json<T>('PATCH', path, { body, idempotent: true }),
  upload: <T>(path: string, formData: FormData) => json<T>('POST', path, { formData, idempotent: true }),
  /** Descarga un archivo (PDF/Excel) con el token de sesion y lo entrega al navegador. */
  async download(path: string, query: Query, fallbackName: string) {
    const response = await send('GET', path, { query })
    const disposition = response.headers.get('Content-Disposition') ?? ''
    const name = /filename="?([^";]+)"?/i.exec(disposition)?.[1] ?? fallbackName
    const blob = await response.blob()
    const href = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = href
    link.download = name
    link.click()
    URL.revokeObjectURL(href)
  },
}
