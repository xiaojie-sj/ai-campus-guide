import type {
  AdminUser,
  ApiEnvelope,
  BootstrapResponse,
  CameraPreset,
  ChatLog,
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
const TOKEN_KEY = 'hbeutc-campus-guide-token'
const USER_KEY = 'hbeutc-campus-guide-user'

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

function token() {
  return localStorage.getItem(TOKEN_KEY)
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
  if (auth && token()) {
    headers.set('Authorization', `Bearer ${token()}`)
  }
  const response = await fetch(`${API_BASE}${path}`, { ...init, headers })
  let envelope: ApiEnvelope<T>
  try {
    envelope = await response.json()
  } catch {
    throw new ApiError(`请求失败（HTTP ${response.status}）`, response.status, response.status)
  }
  if (!response.ok || envelope.code !== 0) {
    if (response.status === 401 && auth) {
      clearAdminSession()
    }
    throw new ApiError(envelope.message || '请求失败', response.status, envelope.code)
  }
  return envelope.data
}

function adminSession(): AdminUser | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AdminUser
  } catch {
    return null
  }
}

function clearAdminSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
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
  feedback: (chatLogId: number, feedback: string) =>
    request<void>('/api/public/feedback', {
      method: 'POST',
      body: JSON.stringify({ chatLogId, feedback }),
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

export const authApi = {
  login: async (username: string, password: string) => {
    const user = await request<AdminUser>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    })
    localStorage.setItem(TOKEN_KEY, user.token)
    localStorage.setItem(USER_KEY, JSON.stringify(user))
    return user
  },
  session: adminSession,
  logout: clearAdminSession,
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
  logs: (page = 1, size = 30) =>
    request<PageResult<ChatLog>>(`/api/admin/chat-logs?page=${page}&size=${size}`, {}, true),
}
