import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const apiMocks = vi.hoisted(() => ({
  settings: vi.fn(),
  aiStatus: vi.fn(),
  dashboard: vi.fn(),
  approvals: vi.fn(),
  directories: vi.fn(),
  health: vi.fn(),
  systemInfo: vi.fn(),
  updateSettings: vi.fn(),
  workflowActions: vi.fn(),
}))

vi.mock('@/api/client', () => ({
  api: apiMocks,
  ApiError: class ApiError extends Error {},
}))

import { useSessionStore } from './session'

const settings = {
  model: {
    provider: 'ollama',
    endpoint: 'http://127.0.0.1:11434',
    model: 'gemma2:2b',
    timeoutSeconds: 120,
    maxInputChars: 12000,
    lastLatencyMs: null,
  },
  approvals: {
    fileMovesRequireApproval: true,
    workflowMovesRequireApproval: true,
    logAnalysisRequiresApproval: true,
  },
  timeSavings: {
    fileReviewMinutesPerFile: 1.5,
    logAnalysisMinutes: 20,
    workflowReviewMinutes: 5,
  },
  theme: 'dark',
  onboardingCompleted: true,
  dataDir: 'C:\\data',
  databasePath: 'C:\\data\\ghostshift.db',
  categoryFolders: { DOCUMENTS: 'documents' },
}

const ai = {
  reachable: true,
  modelAvailable: true,
  endpoint: 'http://127.0.0.1:11434',
  model: 'gemma2:2b',
  status: 'READY',
  message: 'Ready',
  latencyMs: 42,
  models: [],
}

const pendingApproval = {
  id: 'a1',
  runId: 'r1',
  title: 'Approve move',
  explanation: 'A file will move.',
  risk: 'MODERATE',
  status: 'PENDING',
  createdAt: '2026-01-01T00:00:00Z',
}

const resolvedApproval = { ...pendingApproval, id: 'a2', status: 'APPROVED' }

describe('session store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()

    apiMocks.settings.mockResolvedValue({ ...settings })
    apiMocks.aiStatus.mockResolvedValue(ai)
    apiMocks.dashboard.mockResolvedValue({ counts: {} })
    apiMocks.approvals.mockResolvedValue([pendingApproval, resolvedApproval])
    apiMocks.directories.mockResolvedValue([])
    apiMocks.health.mockResolvedValue({ status: 'ok', database: 'OK', version: '1.0.0' })
    apiMocks.systemInfo.mockResolvedValue({ name: 'GhostShift AI', version: '1.0.0' })
    apiMocks.updateSettings.mockImplementation(async (patch) => ({ ...settings, ...patch }))
    apiMocks.workflowActions.mockResolvedValue([{ type: 'LIST_FILES', risk: 'LOW' }])
    document.documentElement.dataset.theme = ''
  })

  it('exposes pending approvals and AI availability through getters', () => {
    const store = useSessionStore()
    store.approvals = [pendingApproval, resolvedApproval] as never
    store.ai = ai as never

    expect(store.pendingApprovals).toHaveLength(1)
    expect(store.aiOnline).toBe(true)
    expect(store.theme).toBe('dark')

    store.ai = { ...ai, modelAvailable: false }
    expect(store.aiOnline).toBe(false)
  })

  it('bootstraps all session data and applies the backend theme', async () => {
    const store = useSessionStore()
    apiMocks.settings.mockResolvedValue({ ...settings, theme: 'light' })

    await store.bootstrap()

    expect(store.loaded).toBe(true)
    expect(store.loading).toBe(false)
    expect(store.offline).toBe(false)
    expect(store.settings?.model.model).toBe('gemma2:2b')
    expect(store.pendingApprovals).toHaveLength(1)
    expect(store.health?.status).toBe('ok')
    expect(document.documentElement.dataset.theme).toBe('light')
  })

  it('prefers a locally stored theme over the backend theme', async () => {
    localStorage.setItem('ghostshift.theme', 'light')
    const store = useSessionStore()
    apiMocks.settings.mockResolvedValue({ ...settings, theme: 'dark' })

    await store.bootstrap()

    expect(document.documentElement.dataset.theme).toBe('light')
  })

  it('degrades to offline without failing when bootstrap calls reject', async () => {
    const store = useSessionStore()
    apiMocks.settings.mockRejectedValue(new Error('offline'))
    apiMocks.aiStatus.mockRejectedValue(new Error('offline'))
    apiMocks.dashboard.mockRejectedValue(new Error('offline'))
    apiMocks.approvals.mockRejectedValue(new Error('offline'))
    apiMocks.directories.mockRejectedValue(new Error('offline'))
    apiMocks.health.mockRejectedValue(new Error('offline'))
    apiMocks.systemInfo.mockRejectedValue(new Error('offline'))

    await store.bootstrap()

    expect(store.loaded).toBe(true)
    expect(store.offline).toBe(true)
    expect(store.settings).toBeNull()
    expect(store.approvals).toEqual([])
  })

  it('saves settings, applies a theme, and reflects onboarding updates', async () => {
    const store = useSessionStore()
    await store.bootstrap()

    await store.saveSettings({ theme: 'light', onboardingCompleted: false })

    expect(apiMocks.updateSettings).toHaveBeenCalledWith({ theme: 'light', onboardingCompleted: false })
    expect(store.settings?.theme).toBe('light')
    expect(store.settings?.onboardingCompleted).toBe(false)
    expect(document.documentElement.dataset.theme).toBe('light')
  })

  it('stores local theme choices even when the backend save fails', async () => {
    const store = useSessionStore()
    await store.bootstrap()
    apiMocks.updateSettings.mockRejectedValue(new Error('offline'))

    await store.setTheme('light')

    expect(localStorage.getItem('ghostshift.theme')).toBe('light')
    expect(store.settings?.theme).toBe('light')
    expect(document.documentElement.dataset.theme).toBe('light')
  })

  it('loads the action catalog once', async () => {
    const store = useSessionStore()

    await store.loadActions()
    await store.loadActions()

    expect(apiMocks.workflowActions).toHaveBeenCalledTimes(1)
    expect(store.actions).toHaveLength(1)
  })
})
