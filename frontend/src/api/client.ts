import type {
  AiStatus,
  AiTestResult,
  AiWorkflowDraft,
  AppSettings,
  Approval,
  DashboardSummary,
  DataExport,
  DemoSeedResult,
  DuplicateReport,
  HealthStatus,
  HistoryPage,
  OrganizationPlan,
  OrganizationPlanSummary,
  PlanDecision,
  RecordingView,
  RegisteredDirectory,
  ReportSummary,
  ReportView,
  RunEvent,
  RunSummary,
  ScanResult,
  SettingsUpdateRequest,
  SystemInfo,
  TimeSavingsView,
  WorkflowActionView,
  WorkflowDetail,
  WorkflowRun,
  WorkflowSummary,
  WorkflowValidation,
  WorkflowDefinition,
  Category,
} from './types'

const BASE = '/api/v1'
let memoryToken = ''

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly details?: Record<string, unknown>
  readonly requestId?: string

  constructor(message: string, status = 0, code = 'NETWORK_ERROR', details?: Record<string, unknown>, requestId?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.details = details
    this.requestId = requestId
  }
}

export function setApiToken(token: string): void {
  memoryToken = token.trim()
}

export function hasApiToken(): boolean {
  return memoryToken.length > 0
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body !== undefined && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  if (memoryToken) {
    headers.set('Authorization', `Bearer ${memoryToken}`)
  }

  let response: Response
  try {
    response = await fetch(`${BASE}${path}`, { ...init, headers })
  } catch {
    throw new ApiError(
      'GhostShift could not reach its local backend. Start the backend or check that it is running on the configured port.',
      0,
    )
  }

  if (!response.ok) {
    const requestId = response.headers.get('X-Request-Id') ?? undefined
    let code = `HTTP_${response.status}`
    let message = response.statusText || 'The request failed.'
    let details: Record<string, unknown> | undefined
    try {
      const body = (await response.json()) as {
        code?: string
        message?: string
        details?: Record<string, unknown>
        requestId?: string
      }
      code = body.code ?? code
      message = body.message ?? message
      details = body.details
      if (body.requestId) {
        return Promise.reject(new ApiError(message, response.status, code, details, body.requestId))
      }
    } catch {
      // Keep the status-based fallback when the body is not JSON.
    }
    throw new ApiError(message, response.status, code, details, requestId)
  }

  if (response.status === 204) {
    return undefined as T
  }
  const text = await response.text()
  if (!text) {
    return undefined as T
  }
  return JSON.parse(text) as T
}

function json(body: unknown): string {
  return JSON.stringify(body)
}

export const api = {
  health: () => request<HealthStatus>('/health'),
  systemInfo: () => request<SystemInfo>('/system/info'),

  settings: () => request<AppSettings>('/settings'),
  updateSettings: (patch: SettingsUpdateRequest) =>
    request<AppSettings>('/settings', { method: 'PATCH', body: json(patch) }),
  testModel: (payload?: { endpoint?: string; model?: string }) =>
    request<AiTestResult>('/settings/model/test', {
      method: 'POST',
      body: json(payload ?? {}),
    }),
  aiStatus: (refresh = false) => request<AiStatus>(`/ai/status?refresh=${refresh}`),

  directories: () => request<RegisteredDirectory[]>('/directories'),
  registerDirectory: (payload: {
    path: string
    displayName?: string
    directoryType?: string
    allowOrganize?: boolean
  }) => request<RegisteredDirectory>('/directories', { method: 'POST', body: json(payload) }),
  removeDirectory: (id: number) => request<void>(`/directories/${id}`, { method: 'DELETE' }),
  scan: (id: number, recursive = false, useAi = false) =>
    request<ScanResult>(`/directories/${id}/scan`, {
      method: 'POST',
      body: json({ recursive, useAi }),
    }),

  plans: (limit = 25) => request<OrganizationPlanSummary[]>(`/organization/plans?limit=${limit}`),
  plan: (id: string) => request<OrganizationPlan>(`/organization/plans/${id}`),
  createPlan: (payload: {
    directoryId: number
    useAi?: boolean
    recursive?: boolean
    maxFiles?: number
    recordingId?: string
  }) => request<OrganizationPlan>('/organization/plans', { method: 'POST', body: json(payload) }),
  patchPlanAction: (
    planId: string,
    actionId: string,
    payload: { category?: Category; status?: string; remember?: boolean },
  ) =>
    request<OrganizationPlan>(`/organization/plans/${planId}/actions/${actionId}`, {
      method: 'PATCH',
      body: json(payload),
    }),
  decidePlan: (planId: string, decisions: PlanDecision[], rejectUnreviewed = false) =>
    request<OrganizationPlan>(`/organization/plans/${planId}/decisions`, {
      method: 'POST',
      body: json({ decisions, rejectUnreviewed }),
    }),
  executePlan: (planId: string) =>
    request<OrganizationPlan>(`/organization/plans/${planId}/execute`, { method: 'POST', body: '{}' }),
  cancelPlan: (planId: string) =>
    request<OrganizationPlan>(`/organization/plans/${planId}/cancel`, { method: 'POST', body: '{}' }),
  undoPlan: (planId: string) =>
    request<OrganizationPlan>(`/organization/plans/${planId}/undo`, { method: 'POST', body: '{}' }),

  workflows: (includeArchived = false) =>
    request<WorkflowSummary[]>(`/workflows?includeArchived=${includeArchived}`),
  workflow: (id: string) => request<WorkflowDetail>(`/workflows/${id}`),
  createWorkflow: (payload: {
    name: string
    description: string
    category?: string
    definition: WorkflowDefinition
  }) => request<WorkflowDetail>('/workflows', { method: 'POST', body: json(payload) }),
  updateWorkflow: (
    id: string,
    payload: { name: string; description: string; category?: string; definition: WorkflowDefinition },
  ) => request<WorkflowDetail>(`/workflows/${id}`, { method: 'PUT', body: json(payload) }),
  archiveWorkflow: (id: string) => request<WorkflowDetail>(`/workflows/${id}`, { method: 'DELETE' }),
  restoreWorkflow: (id: string) =>
    request<WorkflowDetail>(`/workflows/${id}/restore`, { method: 'POST', body: '{}' }),
  setWorkflowStatus: (id: string, status: string) =>
    request<WorkflowDetail>(`/workflows/${id}/status`, { method: 'PATCH', body: json({ status }) }),
  validateWorkflow: (definition: WorkflowDefinition) =>
    request<WorkflowValidation>('/workflows/validation', { method: 'POST', body: json(definition) }),
  draftWorkflow: (prompt: string, directoryId?: number) =>
    request<AiWorkflowDraft>('/workflows/draft', {
      method: 'POST',
      body: json({ prompt, directoryId }),
    }),
  workflowActions: () => request<WorkflowActionView[]>('/workflows/actions'),
  runWorkflow: (id: string, payload: { inputs: Record<string, unknown>; dryRun?: boolean }) =>
    request<WorkflowRun>(`/workflows/${id}/runs`, { method: 'POST', body: json(payload) }),

  runs: (limit = 25) => request<RunSummary[]>(`/runs?limit=${limit}`),
  run: (id: string) => request<WorkflowRun>(`/runs/${id}`),
  runEvents: (id: string, after = 0) => request<RunEvent[]>(`/runs/${id}/events?after=${after}`),
  cancelRun: (id: string) => request<WorkflowRun>(`/runs/${id}/cancel`, { method: 'POST', body: '{}' }),

  approvals: () => request<Approval[]>('/approvals'),
  approve: (id: string, note?: string) =>
    request<WorkflowRun>(`/approvals/${id}/approve`, { method: 'POST', body: json({ note: note ?? '' }) }),
  reject: (id: string, note?: string) =>
    request<WorkflowRun>(`/approvals/${id}/reject`, { method: 'POST', body: json({ note: note ?? '' }) }),

  recordings: () => request<RecordingView[]>('/recordings'),
  recording: (id: string) => request<RecordingView>(`/recordings/${id}`),
  startRecording: (payload: { name: string; mode?: string; directoryId?: number }) =>
    request<RecordingView>('/recordings', { method: 'POST', body: json(payload) }),
  pauseRecording: (id: string) =>
    request<RecordingView>(`/recordings/${id}/pause`, { method: 'POST', body: '{}' }),
  resumeRecording: (id: string) =>
    request<RecordingView>(`/recordings/${id}/resume`, { method: 'POST', body: '{}' }),
  stopRecording: (id: string) =>
    request<RecordingView>(`/recordings/${id}/stop`, { method: 'POST', body: '{}' }),
  deleteRecording: (id: string) => request<void>(`/recordings/${id}`, { method: 'DELETE' }),
  addRecordedStep: (
    id: string,
    payload: { actionType: string; description: string; parameters: Record<string, unknown> },
  ) => request<RecordingView>(`/recordings/${id}/steps`, { method: 'POST', body: json(payload) }),
  updateRecordedStep: (
    id: string,
    stepId: string,
    payload: { description?: string; order?: number; parameters?: Record<string, unknown> },
  ) =>
    request<RecordingView>(`/recordings/${id}/steps/${stepId}`, {
      method: 'PATCH',
      body: json(payload),
    }),
  deleteRecordedStep: (id: string, stepId: string) =>
    request<RecordingView>(`/recordings/${id}/steps/${stepId}`, { method: 'DELETE' }),
  saveRecording: (id: string, payload: { name?: string; description?: string; category?: string }) =>
    request<WorkflowDetail>(`/recordings/${id}/save`, { method: 'POST', body: json(payload) }),

  analyzeDeveloper: (payload: { directoryId: number; relativePath: string; useAi?: boolean }) =>
    request<ReportView>('/developer/analyze', { method: 'POST', body: json(payload) }),
  reports: (limit = 50) => request<ReportSummary[]>(`/reports?limit=${limit}`),
  report: (id: string) => request<ReportView>(`/reports/${id}`),

  dashboard: () => request<DashboardSummary>('/insights/dashboard'),
  timeSavings: () => request<TimeSavingsView>('/insights/time-savings'),
  duplicates: (directoryId: number, recursive = false) =>
    request<DuplicateReport>(`/insights/duplicates?directoryId=${directoryId}&recursive=${recursive}`),
  activity: (page = 1, pageSize = 25, kind = '') =>
    request<HistoryPage>(`/activity?page=${page}&pageSize=${pageSize}&kind=${encodeURIComponent(kind)}`),

  seedDemo: () => request<DemoSeedResult>('/demo/seed', { method: 'POST', body: '{}' }),
  exportData: () => request<DataExport>('/data/export'),
  resetData: (scope: string, confirmation: string) =>
    request<Record<string, unknown>>('/data/reset', {
      method: 'POST',
      body: json({ scope, confirmation }),
    }),
}

export async function downloadReport(report: ReportSummary, fallbackContent?: string): Promise<void> {
  if (fallbackContent) {
    downloadBlob(new Blob([fallbackContent], { type: 'text/markdown' }), `${safeName(report.title)}.md`)
    return
  }
  const headers = new Headers()
  if (memoryToken) {
    headers.set('Authorization', `Bearer ${memoryToken}`)
  }
  const response = await fetch(`${BASE}/reports/${report.id}/export`, { headers })
  if (!response.ok) {
    throw new ApiError('The report could not be exported.', response.status)
  }
  downloadBlob(await response.blob(), `${safeName(report.title)}.md`)
}

export async function downloadJson(data: unknown, fileName: string): Promise<void> {
  downloadBlob(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }), fileName)
}

function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

function safeName(value: string): string {
  return value.replace(/[\\/:*?"<>|]/g, '-').slice(0, 100) || 'ghostshift-report'
}
