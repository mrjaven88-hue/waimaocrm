import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import AppShell from '../components/AppShell.vue'
import LeadListView from '../views/LeadListView.vue'
import LeadDetailView from '../views/LeadDetailView.vue'
import AuditView from '../views/AuditView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView },
    {
      path: '/',
      component: AppShell,
      children: [
        { path: '', redirect: '/leads' },
        { path: 'leads', component: LeadListView },
        { path: 'leads/:id', component: LeadDetailView },
        { path: 'audit', component: AuditView, meta: { manager: true } },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path !== '/login' && !auth.token) return '/login'
  if (to.path === '/login' && auth.token) return '/leads'
  if (to.meta.manager === true && auth.user?.role !== 'MANAGER') return '/leads'
  return true
})

export default router
