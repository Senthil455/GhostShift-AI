import { expect, test } from '@playwright/test'
import { createFolder, removeFolder, setOnboarding } from './helpers'

test('GhostDev analyzes a real log deterministically and GhostPulse reports exact duplicates', async ({ page }) => {
  await setOnboarding(true)
  const folder = await createFolder(
    {
      'app.log': [
        '2026-03-10T09:15:01Z INFO  demo-app started',
        '2026-03-10T09:15:02Z ERROR demo-sync failed: connection refused',
        '2026-03-10T09:15:03Z WARN  demo-sync retry scheduled',
        '2026-03-10T09:15:09Z ERROR request timed out after 2000 ms',
        '',
      ].join('\n'),
      'notes.txt': 'The same synthetic content.\n',
      'nested/notes-copy.txt': 'The same synthetic content.\n',
    },
    { label: 'E2E insights folder' },
  )

  try {
    await page.goto('/dev')
    await expect(page.getByRole('heading', { name: 'Read the evidence before the theory.' })).toBeVisible()

    const logPicker = page.getByLabel('Folder containing the log')
    await expect(logPicker.locator(`option:has-text("${folder.displayName}")`)).toHaveCount(1)
    await logPicker.selectOption(String(folder.id))
    await page.getByRole('button', { name: 'Scan folder' }).click()

    const fileSelect = page.locator('#dev-file')
    await expect(fileSelect).toBeVisible()
    await fileSelect.selectOption('app.log')
    await page.getByRole('button', { name: 'Analyze log' }).click()

    await expect(page.getByRole('heading', { name: /app\.log/ })).toBeVisible({ timeout: 25_000 })
    await expect(page.getByRole('heading', { name: 'Observed evidence' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Grouped signatures' })).toBeVisible()
    await expect(page.locator('.dev-stats')).toContainText('Errors')
    await expect(page.locator('.dev-report')).toContainText('Deterministic only')
    await expect(page.getByText('Full Markdown report')).toBeVisible()

    await page.goto('/pulse')
    await expect(page.getByRole('heading', { name: 'Measure the shift, honestly.' })).toBeVisible()
    const duplicatePicker = page.getByLabel('Folder to inspect')
    await expect(duplicatePicker.locator(`option:has-text("${folder.displayName}")`)).toHaveCount(1)
    await duplicatePicker.selectOption(String(folder.id))
    await page.getByRole('button', { name: 'Scan for duplicates' }).click()

    const report = page.locator('.duplicate-report')
    await expect(report).toBeVisible()
    await expect(report.locator('.duplicate-report__summary')).toContainText('Files hashed')
    await expect(report.getByText('Identical content (SHA-256)')).toBeVisible()
    await expect(report).toContainText('notes.txt')
    await expect(report).toContainText('nested/notes-copy.txt')
    await expect(report).toContainText('read-only')
    await expect(report).toContainText('Not proof of identical content')
  } finally {
    await removeFolder(folder)
  }
})
