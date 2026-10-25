import { expect, test } from '@playwright/test'
import {
  api,
  createFolder,
  removeFolder,
  safeOrganizeDefinition,
  setOnboarding,
  waitForRun,
} from './helpers'

test('GhostReplay runs a reviewed workflow in dry-run mode and opens its record', async ({ page }) => {
  await setOnboarding(true)
  const folder = await createFolder(
    {
      'notes.txt': 'A synthetic note for the dry-run test.\n',
      'report.csv': 'name,value\nbeta,2\n',
    },
    { allowOrganize: true, label: 'E2E replay folder' },
  )

  const context = await api()
  let workflowId = ''
  try {
    const workflowName = `E2E replay workflow ${Date.now()}`
    const created = await context.post('/api/v1/workflows', {
      data: {
        name: workflowName,
        description: 'Created by the GhostReplay end-to-end test.',
        category: 'TEST',
        definition: { ...safeOrganizeDefinition(), name: workflowName },
      },
    })
    expect(created.ok()).toBeTruthy()
    const workflow = (await created.json()) as { id: string; validation?: { valid?: boolean } }
    expect(workflow.validation?.valid).toBe(true)
    workflowId = workflow.id

    await page.goto(`/replay?workflow=${workflowId}`)
    await expect(page.getByRole('heading', { name: workflowName })).toBeVisible()

    const folderSelect = page.locator('#directory-folder')
    await expect(folderSelect).toBeVisible()
    await folderSelect.selectOption(String(folder.id))
    await page.getByRole('button', { name: 'Start dry run' }).click()

    await expect(page).toHaveURL(/\/history\/runs\/[0-9a-f-]+$/)
    const runId = page.url().split('/').pop() ?? ''
    await waitForRun(runId, 'COMPLETED')
    await expect(page.getByRole('heading', { name: workflowName })).toBeVisible()
    await expect(page.locator('.run-meta__status')).toContainText('Completed')
    await expect(page.locator('.run-meta__facts')).toContainText('Dry run')
    await expect(page.getByRole('heading', { name: 'Steps' })).toBeVisible()
  } finally {
    if (workflowId) {
      await context.delete(`/api/v1/workflows/${workflowId}`).catch(() => undefined)
    }
    await context.dispose()
    await removeFolder(folder)
  }
})
