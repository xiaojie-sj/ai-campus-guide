import { createRouter, createWebHistory } from 'vue-router'
import { authApi } from './api/client'

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
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !authApi.session()) {
    return { name: 'admin-login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'admin-login' && authApi.session()) {
    return { name: 'admin' }
  }
  return true
})
