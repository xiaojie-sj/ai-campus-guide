const LOGIN_PATH = '/admin/login'

/**
 * Only allow post-login navigation inside the admin area. This blocks
 * open-redirects such as /admin/login?redirect=https://evil.example.
 */
export function safeAdminRedirect(raw: unknown): string {
  const value = Array.isArray(raw) ? raw[0] : raw
  if (typeof value !== 'string' || value.length === 0 || value.length > 2048) {
    return '/admin'
  }
  if (!value.startsWith('/') || value.startsWith('//') || value.includes('\\')
    || value.includes('..') || /%2e/i.test(value) || /[\u0000-\u001f]/.test(value)) {
    return '/admin'
  }
  if (value === LOGIN_PATH || value.startsWith(`${LOGIN_PATH}?`) || value.startsWith(`${LOGIN_PATH}/`)) {
    return '/admin'
  }
  if (value !== '/admin' && !value.startsWith('/admin/')) {
    return '/admin'
  }
  return value
}

export function isAdminPath(path: string): boolean {
  return path === '/admin' || path.startsWith('/admin/')
}

