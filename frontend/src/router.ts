import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/overview',
  },
  {
    path: '/overview',
    name: 'overview',
    component: () => import('./views/OverviewView.vue'),
    meta: { title: 'Overview', group: 'Studio', icon: 'grid' },
  },
  {
    path: '/sort',
    name: 'ghost-sort',
    component: () => import('./views/GhostSortView.vue'),
    meta: { title: 'GhostSort', group: 'Studio', icon: 'sort' },
  },
  {
    path: '/dev',
    name: 'ghost-dev',
    component: () => import('./views/GhostDevView.vue'),
    meta: { title: 'GhostDev', group: 'Studio', icon: 'bug' },
  },
  {
    path: '/capture',
    name: 'ghost-capture',
    component: () => import('./views/GhostCaptureView.vue'),
    meta: { title: 'GhostCapture', group: 'Studio', icon: 'record' },
  },
  {
    path: '/replay',
    name: 'ghost-replay',
    component: () => import('./views/GhostReplayView.vue'),
    meta: { title: 'GhostReplay', group: 'Studio', icon: 'play' },
  },
  {
    path: '/brain',
    name: 'ghost-brain',
    component: () => import('./views/GhostBrainView.vue'),
    meta: { title: 'GhostBrain', group: 'Intelligence', icon: 'brain' },
  },
  {
    path: '/pulse',
    name: 'ghost-pulse',
    component: () => import('./views/GhostPulseView.vue'),
    meta: { title: 'GhostPulse', group: 'Intelligence', icon: 'pulse' },
  },
  {
    path: '/approvals',
    name: 'approvals',
    component: () => import('./views/ApprovalsView.vue'),
    meta: { title: 'Approvals', group: 'Intelligence', icon: 'shield' },
  },
  {
    path: '/history',
    name: 'history',
    component: () => import('./views/HistoryView.vue'),
    meta: { title: 'History', group: 'Intelligence', icon: 'clock' },
  },
  {
    path: '/history/runs/:runId',
    name: 'run-detail',
    component: () => import('./views/RunDetailView.vue'),
    meta: { title: 'Run record', group: 'Intelligence', icon: 'clock', parent: 'history' },
  },
  {
    path: '/settings',
    name: 'settings',
    component: () => import('./views/SettingsView.vue'),
    meta: { title: 'Settings', group: 'System', icon: 'settings' },
  },
  {
    path: '/welcome',
    name: 'welcome',
    component: () => import('./views/WelcomeView.vue'),
    meta: { title: 'Welcome', bare: true },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('./views/NotFoundView.vue'),
    meta: { title: 'Not found' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(_to, _from, savedPosition) {
    return savedPosition ?? { top: 0 }
  },
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : 'GhostShift AI'
  document.title = to.name === 'overview' ? 'GhostShift AI' : `${title} · GhostShift AI`
})

export default router
