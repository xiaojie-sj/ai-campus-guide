import type {
  AdminUser,
  ApiEnvelope,
  BootstrapResponse,
  CameraPreset,
  ChatLog,
  DashboardResponse,
  ChatResponse,
  FaqItem,
  KnowledgeDocumentView,
  PageResult,
  PointView,
  StatsResponse,
  SystemConfig,
  TourRoute,
  TourStop,
} from '../types'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''
const USER_KEY = 'hbeutc-campus-guide-user'

/*
 * Auth bookkeeping.
 *
 * `sessionEstablished` flips to true only after the server has confirmed a
 * login. It lets us distinguish "the visitor never logged in" (normal, silent
 * 401 from the route guard) from "an existing session just died" (must be
 * surfaced exactly once). Without this, every concurrent admin request that
 * returned 401 re-dispatched the expiry event and the router kept re-verifying
 * the cookie, producing an infinite 401 storm.
 */
let sessionEstablished = false
let expiryNotified = false
let skipLoginVerify = false

/** Ask the next /admin/login guard evaluation to skip /api/auth/me. */
export function skipNextLoginVerification() {
  skipLoginVerify = true
}

/**
 * Consumed by the router guard. Returns true when the redirect to the login
 * page was initiated by us (logout/expiry), so calling /api/auth/me again
 * would only generate a predictable extra 401.
 */
export function consumeLoginVerifySkip(): boolean {
  if (!skipLoginVerify) return false
  skipLoginVerify = false
  return true
}

function markSessionEstablished() {
  sessionEstablished = true
  expiryNotified = false
}

function notifySessionExpired() {
  if (!sessionEstablished || expiryNotified) return
  expiryNotified = true
  sessionEstablished = false
  clearAdminSession()
  // The router's login guard must not re-check the cookie we already know is
  // invalid.
  skipNextLoginVerification()
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('admin-session-expired'))
  }
}

export class ApiError extends Error {
  readonly status: number
  readonly code: number

  constructor(
    message: string,
    status: number,
    code: number,
  ) {
    super(message)
    this.status = status
    this.code = code
  }
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  auth = false,
): Promise<T> {
  const headers = new Headers(init.headers)
  if (!(init.body instanceof FormData) && init.body != null) {
    headers.set('Content-Type', 'application/json')
  }
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers,
    // The admin token is an HttpOnly cookie. Public calls also use include so
    // a separately hosted API can opt into credentialed CORS explicitly.
    credentials: 'include',
  })
  let envelope: ApiEnvelope<T>
  try {
    envelope = await response.json()
  } catch {
    throw new ApiError(`请求失败（HTTP ${response.status}）`, response.status, response.status)
  }
  if (!response.ok || envelope.code !== 0) {
    if (response.status === 401 && auth) {
      notifySessionExpired()
    }
    throw new ApiError(envelope.message || '请求失败', response.status, envelope.code)
  }
  return envelope.data
}

function adminSession(): AdminUser | null {
  try {
    const raw = sessionStorage.getItem(USER_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<AdminUser>
    if (typeof parsed.userId !== 'number'
      || typeof parsed.username !== 'string'
      || typeof parsed.displayName !== 'string'
      || typeof parsed.role !== 'string') {
      clearAdminSession()
      return null
    }
    return parsed as AdminUser
  } catch {
    clearAdminSession()
    return null
  }
}

function saveAdminSession(user: AdminUser) {
  const safeUser: AdminUser = {
    userId: user.userId,
    username: user.username,
    displayName: user.displayName,
    role: user.role,
  }
  try {
    sessionStorage.setItem(USER_KEY, JSON.stringify(safeUser))
  } catch {
    // The server-side cookie remains the source of truth.
  }
}

function clearAdminSession() {
  try {
    sessionStorage.removeItem(USER_KEY)
  } catch {
    // Ignore storage restrictions in hardened/private browser modes.
  }
}

type RawPointView = Omit<PointView, 'aliases'> & { aliases?: string | string[] }

function normalizePoint(point: RawPointView): PointView {
  const aliases = Array.isArray(point.aliases)
    ? point.aliases
    : (point.aliases ?? '').split(/[,，、]/).map((value) => value.trim()).filter(Boolean)
  return { ...point, aliases }
}

export const publicApi = {
  bootstrap: () => request<BootstrapResponse>('/api/public/bootstrap'),
  chat: (body: {
    sessionId: string
    message: string
    currentPointId?: number
    inputType: 'TEXT' | 'VOICE'
  }) =>
    request<ChatResponse>('/api/public/chat', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  feedback: (chatLogId: number, feedback: string, sessionId: string) =>
    request<void>('/api/public/feedback', {
      method: 'POST',
      body: JSON.stringify({ chatLogId, feedback, sessionId }),
    }),
  transcribe: async (blob: Blob, durationSeconds: number) => {
    const form = new FormData()
    form.append('file', blob, 'speech.webm')
    form.append('durationSeconds', String(durationSeconds))
    return request<{ text: string; provider: string }>('/api/public/asr', {
      method: 'POST',
      body: form,
    })
  },
  tts: async (text: string, speed: number) => {
    const response = await fetch(`${API_BASE}/api/public/tts`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text, speed }),
    })
    if (!response.ok) throw new ApiError('在线语音合成不可用', response.status, response.status)
    return response.blob()
  },
}

let verifyPromise: Promise<AdminUser | null> | null = null

export const authApi = {
  login: async (username: string, password: string) => {
    const user = await request<AdminUser>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    })
    markSessionEstablished()
    saveAdminSession(user)
    return user
  },
  session: adminSession,
  /**
   * Server-side session check. A localStorage/sessionStorage value alone is
   * never treated as proof of login; the backend validates the HttpOnly cookie.
   */
  verify: async (): Promise<AdminUser | null> => {
    if (verifyPromise) return verifyPromise
    verifyPromise = request<AdminUser>('/api/auth/me', {}, true)
      .then((user) => {
        markSessionEstablished()
        saveAdminSession(user)
        return user
      })
      .catch(() => {
        clearAdminSession()
        return null
      })
      .finally(() => {
        verifyPromise = null
      })
    return verifyPromise
  },
  clear: clearAdminSession,
  logout: async () => {
    try {
      await request<void>('/api/auth/logout', { method: 'POST' }, true)
    } catch {
      // Clearing local state is still the correct outcome if the token expired.
    } finally {
      sessionEstablished = false
      expiryNotified = false
      clearAdminSession()
      // The caller is about to navigate to /admin/login, where the route guard
      // would otherwise call /api/auth/me once more (guaranteed 401).
      skipNextLoginVerification()
    }
  },
}

export const adminApi = {
  points: async () => (await request<RawPointView[]>('/api/admin/points', {}, true)).map(normalizePoint),
  createPoint: async (body: unknown) =>
    normalizePoint(await request<RawPointView>(
      '/api/admin/points', { method: 'POST', body: JSON.stringify(body) }, true)),
  updatePoint: async (id: number, body: unknown) =>
    normalizePoint(await request<RawPointView>(
      `/api/admin/points/${id}`, { method: 'PUT', body: JSON.stringify(body) }, true)),
  deletePoint: (id: number) =>
    request<void>(`/api/admin/points/${id}`, { method: 'DELETE' }, true),

  cameras: () => request<CameraPreset[]>('/api/admin/cameras', {}, true),
  createCamera: (body: Partial<CameraPreset>) =>
    request<CameraPreset>('/api/admin/cameras', { method: 'POST', body: JSON.stringify(body) }, true),
  updateCamera: (id: number, body: Partial<CameraPreset>) =>
    request<CameraPreset>(`/api/admin/cameras/${id}`, { method: 'PUT', body: JSON.stringify(body) }, true),

  tours: () => request<TourRoute[]>('/api/admin/tours', {}, true),
  stops: (id: number) => request<TourStop[]>(`/api/admin/tours/${id}/stops`, {}, true),
  createTour: (body: unknown) =>
    request<TourRoute>('/api/admin/tours', { method: 'POST', body: JSON.stringify(body) }, true),
  updateTour: (id: number, body: unknown) =>
    request<TourRoute>(`/api/admin/tours/${id}`, { method: 'PUT', body: JSON.stringify(body) }, true),

  faqs: () => request<FaqItem[]>('/api/admin/faqs', {}, true),
  createFaq: (body: Partial<FaqItem>) =>
    request<FaqItem>('/api/admin/faqs', { method: 'POST', body: JSON.stringify(body) }, true),
  updateFaq: (id: number, body: Partial<FaqItem>) =>
    request<FaqItem>(`/api/admin/faqs/${id}`, { method: 'PUT', body: JSON.stringify(body) }, true),
  deleteFaq: (id: number) => request<void>(`/api/admin/faqs/${id}`, { method: 'DELETE' }, true),

  knowledge: () => request<KnowledgeDocumentView[]>('/api/admin/knowledge', {}, true),
  uploadKnowledge: async (form: FormData) =>
    request<KnowledgeDocumentView>('/api/admin/knowledge', { method: 'POST', body: form }, true),
  publishKnowledge: (id: number, publish: boolean) =>
    request<KnowledgeDocumentView>(
      `/api/admin/knowledge/${id}/publish?publish=${publish}`,
      { method: 'PUT' },
      true,
    ),
  reindexKnowledge: (id: number) =>
    request<KnowledgeDocumentView>(`/api/admin/knowledge/${id}/reindex`, { method: 'POST' }, true),
  deleteKnowledge: (id: number) =>
    request<void>(`/api/admin/knowledge/${id}`, { method: 'DELETE' }, true),

  configs: () => request<SystemConfig[]>('/api/admin/configs', {}, true),
  updateConfig: (key: string, value: string) =>
    request<SystemConfig>(`/api/admin/configs/${key}`, {
      method: 'PUT',
      body: JSON.stringify({ value }),
    }, true),
  stats: () => request<StatsResponse>('/api/admin/stats', {}, true),
  dashboard: () => request<DashboardResponse>('/api/admin/dashboard', {}, true),
  logs: (page = 1, size = 30) =>
    request<PageResult<ChatLog>>(`/api/admin/chat-logs?page=${page}&size=${size}`, {}, true),
}





