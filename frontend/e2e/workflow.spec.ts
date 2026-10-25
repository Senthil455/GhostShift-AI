import { expect, test } from '@playwright/test'
import {
  api,
  createFolder,
  removeFolder,
  safeOrganizeDefinition,
  setOnboarding,
  waitForApproval,
  waitForRun,
} from './helpers'

test('a live workflow pauses at the human gate, resumes from the UI, and records its history', async ({ page }) => {
  await setOnboarding(true)
  const folder = await createFolder(
    {
      'report.txt': 'A synthetic report that will move only after approval.\n',
      'screenshot.png': 'not-a-real-image',
    },
    { allowOrganize: true, label: 'E2E workflow folder' },
  )

  const context = await api()
  let workflowId = ''
  try {
    const workflowName = `E2E approval workflow ${Date.now()}`
    const created = await context.post('/api/v1/workflows', {
      data: {
        name: workflowName,
        description: 'Created by the browser end-to-end test.',
        category: 'TEST',
        definition: safeOrganizeDefinition(),
      },
    })
    expect(created.ok()).toBeTruthy()
    const workflow = (await created.json()) as {
      id: string
      validation: { valid: boolean; errors: string[] }
    }
    expect(workflow.validation.valid, (workflow.validation.errors ?? []).join('; ')).toBe(true)
    workflowId = workflow.id

    const approved = await context.patch(`/api/v1/workflows/${workflowId}/status`, {
      data: { status: 'APPROVED' },
    })
    expect(approved.ok()).toBeTruthy()

    const started = await context.post(`/api/v1/workflows/${workflowId}/runs`, {
      data: { inputs: { directoryId: folder.id }, dryRun: false },
    })
    expect(started.ok()).toBeTruthy()
    const run = (await started.json()) as { id: string; status: string }

    const approval = await waitForApproval(run.id)
    expect(approval.title).toBe('Approve E2E moves')

    await page.goto('/approvals')
    await expect(page.getByRole('heading', { name: 'Nothing crosses a gate without you.' })).toBeVisible()
    const card = page.locator('.approval-card').filter({ hasText: 'Approve E2E moves' })
    await expect(card).toBeVisible()
    await card.getByRole('button', { name: 'Approve', exact: true }).click()

    const dialog = page.getByRole('dialog', { name: 'Approve this action?' })
    await expect(dialog).toBeVisible()
    await dialog.getByLabel(/Decision note/).fill('Approved by the Playwright end-to-end test.')
    await dialog.getByRole('button', { name: 'Approve action' }).click()

    const completed = await waitForRun(run.id, 'COMPLETED')
    expect(completed.status).toBe('COMPLETED')

    await page.goto(`/history/runs/${run.id}`)
    await expect(page.getByRole('heading', { name: workflowName })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Run events' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Approvals' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Safety record' })).toBeVisible()
    await expect(page.locator('.event-list .event').first()).toBeVisible()
  } finally {
    if (workflowId) {
      await context.delete(`/api/v1/workflows/${workflowId}`).catch(() => undefined)
    }
    await context.dispose()
    await removeFolder(folder)
  }
})
