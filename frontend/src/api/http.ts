const TOKEN_KEY = 'ftcrm_token'
const USER_KEY = 'ftcrm_user'

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const token = sessionStorage.getItem(TOKEN_KEY)
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const base = import.meta.env.VITE_API_BASE ?? ''
  let response: Response
  try {
    response = await fetch(`${base}${path}`, { ...options, headers })
  } catch {
    throw new ApiError(0, '无法连接服务器')
  }
  const text = await response.text()
  let data: { message?: string } | null = null
  if (text) {
    try {
      data = JSON.parse(text) as { message?: string }
    } catch {
      throw new ApiError(response.status, '服务器返回异常')
    }
  }
  if (response.status === 401 && !path.startsWith('/api/auth/')) {
    sessionStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(USER_KEY)
    if (!window.location.pathname.startsWith('/login')) {
      window.location.assign('/login')
    }
  }
  if (!response.ok) {
    throw new ApiError(response.status, data?.message || '请求失败')
  }
  return data as T
}
