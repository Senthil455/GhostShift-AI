import { expect, test } from '@playwright/test'
import { api, createFolder, removeFolder, setOnboarding } from './helpers'

test('GhostCapture records allowlisted file steps and saves a reviewable draft', async ({ page }) => {
  await setOnboarding(true)
  const folder = await createFolder(
    { 'capture-me.txt': 'Synthetic capture test.\n' },
    { label: 'E2E capture folder' },
  )
  const recordingName = `E2E recording ${Date.now()}`
  const context = await api()

  try {
    await page.goto('/capture')
    await expect(page.getByRole('heading', { name: 'Show the shift once. Replay it later.' })).toBeVisible()
    await expect(page.getByText('This version records file actions, not your browser screen.')).toBeVisible()

    await page.locator('#recording-name').fill(recordingName)
    const picker = page.getByLabel('Working folder (optional)')
    await expect(picker.locator(`option:has-text("${folder.displayName}")`)).toHaveCount(1)
    await picker.selectOption({ label: folder.displayName })
    await page.getByRole('button', { name: 'Start recording' }).click()

    await expect(page.locator('.recording-head h2')).toHaveText(recordingName)
    await expect(page.getByText('Recording is active. Add steps below when you are ready.')).toBeVisible()

    await page.locator('#step-description').fill('List the files in this folder')
    await page.locator('#step-parameters').fill('{"recursive":false,"maxFiles":100}')
    await page.getByRole('button', { name: 'Add step' }).click()
    await expect(page.locator('.capture-table')).toContainText('List files')
    await expect(page.locator('.capture-table')).toContainText('List the files in this folder')

    await page.getByRole('button', { name: 'Stop' }).click()
    await expect(page.getByText('Stopped. Review and edit the steps, then save a workflow.')).toBeVisible()

    await page.locator('#save-description').fill('A recording created by the end-to-end browser test.')
    await page.getByRole('button', { name: 'Save draft workflow' }).click()
    await expect(page.getByText('This recording was saved as a workflow.')).toBeVisible({ timeout: 20_000 })

    const recordings = await context.get('/api/v1/recordings')
    const list = (await recordings.json()) as Array<{ id: string; name: string; workflowId?: string }>
    const saved = list.find((recording) => recording.name === recordingName)
    expect(saved?.workflowId).toBeTruthy()

    if (saved?.workflowId) {
      await context.delete(`/api/v1/workflows/${saved.workflowId}`)
    }
    if (saved?.id) {
      await context.delete(`/api/v1/recordings/${saved.id}`)
    }
  } finally {
    await context.dispose()
    await removeFolder(folder)
  }
})
