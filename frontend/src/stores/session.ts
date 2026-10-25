import { defineStore } from 'pinia'
import { api, ApiError } from '@/api/client'
import type {
  AiStatus,
  AppSettings,
  Approval,
  DashboardSummary,
  HealthStatus,
  RegisteredDirectory,
  SettingsUpdateRequest,
  SystemInfo,
  WorkflowActionView,
} from '@/api/types'

type Theme = 'dark' | 'light' | 'system'

interface SessionState {
  loaded: boolean
  loading: boolean
  offline: boolean
  error: string
  settings: AppSettings | null
  ai: AiStatus | null
  dashboard: DashboardSummary | null
  approvals: Approval[]
  directories: RegisteredDirectory[]
  health: HealthStatus | null
  system: SystemInfo | null
  actions: WorkflowActionView[]
}

const THEME_KEY = 'ghostshift.theme'

export const useSessionStore = defineStore('session', {
  state: (): SessionState => ({
    loaded: false,
    loading: false,
    offline: false,
    error: '',
    settings: null,
    ai: null,
    dashboard: null,
    approvals: [],
    directories: [],
    health: null,
    system: null,
    actions: [],
  }),
  getters: {
    pendingApprovals: (state) => state.approvals.filter((item) => item.status === 'PENDING'),
    aiOnline: (state) => Boolean(state.ai?.reachable && state.ai?.modelAvailable),
    theme: (state): Theme => {
      const theme = state.settings?.theme
      return theme === 'light' || theme === 'system' ? theme : 'dark'
    },
  },
  actions: {
    async bootstrap() {
      if (this.loading) return
      this.loading = true
      this.error = ''
      try {
        const [settings, ai, dashboard, approvals, directories, health, system] = await Promise.all([
          api.settings().catch(() => null),
          api.aiStatus().catch(() => null),
          api.dashboard().catch(() => null),
          api.approvals().catch(() => [] as Approval[]),
          api.directories().catch(() => [] as RegisteredDirectory[]),
          api.health().catch(() => null),
          api.systemInfo().catch(() => null),
        ])
        this.settings = settings
        this.ai = ai
        this.dashboard = dashboard
        this.approvals = approvals
        this.directories = directories
        this.health = health
        this.system = system
        this.offline = !health
        const stored = localStorage.getItem(THEME_KEY) as Theme | null
        const chosen = stored ?? (settings?.theme as Theme | undefined) ?? 'dark'
        this.applyTheme(chosen)
      } catch (error) {
        this.offline = true
        this.error = error instanceof ApiError ? error.message : 'The local backend is unavailable.'
      } finally {
        this.loaded = true
        this.loading = false
      }
    },

    async refreshAi(refresh = false) {
      this.ai = await api.aiStatus(refresh)
      if (this.settings) {
        this.settings.model = { ...this.settings.model, endpoint: this.ai.endpoint, model: this.ai.model }
      }
    },

    async refreshDashboard() {
      this.dashboard = await api.dashboard()
    },

    async refreshApprovals() {
      this.approvals = await api.approvals()
    },

    async refreshDirectories() {
      this.directories = await api.directories()
    },

    async refreshSettings() {
      this.settings = await api.settings()
    },

    async loadActions() {
      if (this.actions.length === 0) {
        this.actions = await api.workflowActions()
      }
    },

    async saveSettings(patch: SettingsUpdateRequest) {
      this.settings = await api.updateSettings(patch)
      if (patch.theme) {
        this.applyTheme(patch.theme as Theme)
      }
      if (patch.onboardingCompleted !== undefined) {
        this.settings.onboardingCompleted = patch.onboardingCompleted
      }
      return this.settings
    },

    async setTheme(theme: Theme) {
      this.applyTheme(theme)
      localStorage.setItem(THEME_KEY, theme)
      if (this.settings) {
        this.settings.theme = theme
      }
      try {
        await api.updateSettings({ theme })
      } catch {
        // A local preference is still useful if the backend is offline.
      }
    },

    applyTheme(theme: Theme) {
      const resolved =
        theme === 'system'
          ? window.matchMedia('(prefers-color-scheme: light)').matches
            ? 'light'
            : 'dark'
          : theme
      document.documentElement.dataset.theme = resolved
      document.documentElement.style.colorScheme = resolved
      const meta = document.querySelector('meta[name="theme-color"]')
      if (meta) {
        meta.setAttribute('content', resolved === 'light' ? '#f4f1e8' : '#0e120f')
      }
    },
  },
})
