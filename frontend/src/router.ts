import { createRouter, createWebHistory } from 'vue-router'
import { authApi, consumeLoginVerifySkip, skipNextLoginVerification } from './api/client'
import { safeAdminRedirect } from './auth'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'guide', component: () => import('./views/PublicGuide.vue') },
    {
      path: '/admin/login',
      name: 'admin-login',
      component: () => import('./views/AdminLogin.vue'),
    },
    {
      path: '/admin',
      name: 'admin',
      component: () => import('./views/AdminDashboard.vue'),
      meta: { requiresAuth: true },
    },
    // Any unknown /admin/* URL must still pass the auth gate before it can be
    // collapsed into the dashboard. The guard runs before this redirect.
    {
      path: '/admin/:pathMatch(.*)*',
      redirect: '/admin',
      meta: { requiresAuth: true },
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)

  if (requiresAuth) {
    // localStorage/sessionStorage is only a display cache. The HttpOnly cookie
    // is verified by /api/auth/me on every protected navigation.
    const user = await authApi.verify()
    if (!user) {
      // The cookie was already rejected by /api/auth/me; do not repeat the
      // doomed check when the login route guard runs next.
      skipNextLoginVerification()
      return { name: 'admin-login', query: { redirect: to.fullPath } }
    }
    const requiredRoles = to.meta.roles as string[] | undefined
    if (requiredRoles?.length && !requiredRoles.includes(user.role)) {
      return { name: 'admin' }
    }
    return true
  }

  if (to.name === 'admin-login') {
    // Logout / session-expiry already proved the cookie is gone. Re-checking it
    // here would add a guaranteed 401 and, before this guard existed, fed the
    // expiry listener into an endless redirect loop.
    if (consumeLoginVerifySkip()) {
      return true
    }
    // A valid cookie from another tab should skip the login form even when
    // this tab has no sessionStorage cache yet.
    const user = await authApi.verify()
    if (user) {
      return safeAdminRedirect(to.query.redirect)
    }
  }
  return true
})

let handlingExpiry = false

if (typeof window !== 'undefined') {
  window.addEventListener('admin-session-expired', () => {
    const current = router.currentRoute.value
    if (handlingExpiry || !current.meta.requiresAuth) return
    handlingExpiry = true
    void router
      .replace({
        name: 'admin-login',
        query: { redirect: current.fullPath },
      })
      .finally(() => {
        handlingExpiry = false
      })
  })
}

