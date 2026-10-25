import { request, type APIRequestContext } from '@playwright/test'
import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { tmpdir } from 'node:os'

export const API_URL = process.env.E2E_API_URL ?? 'http://127.0.0.1:8081'

export async function api(): Promise<APIRequestContext> {
  const token = process.env.E2E_API_TOKEN
  return request.newContext({
    baseURL: API_URL,
    extraHTTPHeaders: token ? { Authorization: `Bearer ${token}` } : {},
  })
}

export interface TestFolder {
  id: number
  displayName: string
  path: string
}

let folderCounter = 0

export async function createFolder(
  files: Record<string, string>,
  options: { allowOrganize?: boolean; label?: string } = {},
): Promise<TestFolder> {
  const context = await api()
  try {
    const root = await mkdtemp(join(tmpdir(), 'ghostshift-e2e-'))
    for (const [name, content] of Object.entries(files)) {
      const target = join(root, name)
      await mkdir(dirname(target), { recursive: true })
      await writeFile(target, content, 'utf8')
    }
    const displayName = `${options.label ?? 'E2E folder'} ${Date.now()}-${++folderCounter}`
    const response = await context.post('/api/v1/directories', {
      data: {
        path: root,
        displayName,
        directoryType: 'GENERAL',
        allowOrganize: options.allowOrganize ?? false,
      },
    })
    if (!response.ok()) {
      throw new Error(`Could not register E2E folder: HTTP ${response.status()} ${await response.text()}`)
    }
    const directory = (await response.json()) as { id: number }
    return { id: directory.id, displayName, path: root }
  } finally {
    await context.dispose()
  }
}

export async function removeFolder(folder: TestFolder): Promise<void> {
  const context = await api()
  try {
    await context.delete(`/api/v1/directories/${folder.id}`)
  } finally {
    await context.dispose()
  }
  await rm(folder.path, { recursive: true, force: true }).catch(() => undefined)
}

export async function setOnboarding(completed: boolean): Promise<void> {
  const context = await api()
  try {
    const response = await context.patch('/api/v1/settings', { data: { onboardingCompleted: completed } })
    if (!response.ok()) {
      throw new Error(`Could not update onboarding: HTTP ${response.status()}`)
    }
  } finally {
    await context.dispose()
  }
}

export async function setTheme(theme: 'dark' | 'light'): Promise<void> {
  const context = await api()
  try {
    await context.patch('/api/v1/settings', { data: { theme } })
  } finally {
    await context.dispose()
  }
}

export async function waitForRun(
  runId: string,
  status: string,
  timeoutMs = 25_000,
): Promise<Record<string, unknown>> {
  const context = await api()
  try {
    const deadline = Date.now() + timeoutMs
    let last: Record<string, unknown> = {}
    while (Date.now() < deadline) {
      const response = await context.get(`/api/v1/runs/${runId}`)
      if (!response.ok()) throw new Error(`Run lookup failed: HTTP ${response.status()}`)
      last = (await response.json()) as Record<string, unknown>
      if (last.status === status) return last
      await new Promise((resolve) => setTimeout(resolve, 300))
    }
    throw new Error(`Run ${runId} did not reach ${status}; last status was ${String(last.status)}`)
  } finally {
    await context.dispose()
  }
}

export async function waitForApproval(
  runId: string,
  timeoutMs = 25_000,
): Promise<{ id: string; title: string }> {
  const context = await api()
  try {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      const response = await context.get('/api/v1/approvals')
      if (response.ok()) {
        const approvals = (await response.json()) as Array<{ id: string; runId: string; title: string }>
        const match = approvals.find((approval) => approval.runId === runId)
        if (match) return match
      }
      await new Promise((resolve) => setTimeout(resolve, 300))
    }
    throw new Error(`Run ${runId} never produced a pending approval`)
  } finally {
    await context.dispose()
  }
}

/** The same safe workflow shape used by the backend integration test. */
export function safeOrganizeDefinition(): Record<string, unknown> {
  const step = (id: string, type: string, parameters: Record<string, unknown>) => ({
    id,
    type,
    description: `${type} step`,
    parameters,
    enabled: true,
  })
  return {
    schemaVersion: 1,
    name: 'E2E safe organize',
    description: 'List, classify, preview, approve, then move.',
    steps: [
      step('list', 'LIST_FILES', { maxFiles: 20 }),
      step('classify', 'CLASSIFY_FILES', { useAi: false }),
      step('propose', 'PROPOSE_DESTINATION', {}),
      step('preview', 'PREVIEW_FILE_MOVE', {}),
      step('approval', 'REQUEST_USER_APPROVAL', {
        title: 'Approve E2E moves',
        explanation: 'The browser test will approve this gate.',
      }),
      step('move', 'MOVE_FILE', { maxFiles: 20 }),
    ],
    inputs: [
      {
        name: 'directoryId',
        label: 'Folder',
        type: 'DIRECTORY',
        required: true,
        description: 'Folder to organize',
      },
    ],
    requiresApproval: true,
    failurePolicy: 'STOP',
  }
}
