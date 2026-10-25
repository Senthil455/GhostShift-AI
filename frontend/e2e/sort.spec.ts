import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { expect, test } from '@playwright/test'
import { createFolder, removeFolder, setOnboarding } from './helpers'

test('GhostSort previews a plan, executes only after approval, and undoes verified moves', async ({ page }) => {
  await setOnboarding(true)
  const folder = await createFolder(
    {
      'meeting-notes.txt': 'Synthetic notes for the browser test.\n',
      'data.csv': 'name,value\nalpha,1\n',
      'photo.png': 'not-a-real-image',
    },
    { allowOrganize: true, label: 'E2E sort folder' },
  )

  try {
    await page.goto('/sort')
    await expect(page.getByRole('heading', { name: 'Put a folder in order.' })).toBeVisible()

    const picker = page.getByLabel('Folder to inspect')
    await expect(picker.locator(`option:has-text("${folder.displayName}")`)).toHaveCount(1)
    await picker.selectOption({ label: folder.displayName })
    await page.getByRole('button', { name: 'Build plan' }).click()

    await expect(page.locator('.plan__identity h2')).toHaveText(folder.displayName)
    await expect(page.locator('.plan .pill').filter({ hasText: 'Ready for review' }).first()).toBeVisible()
    expect(existsSync(join(folder.path, 'meeting-notes.txt'))).toBe(true)

    await page.getByRole('button', { name: /Approve proposed/ }).click()
    await expect(page.getByRole('button', { name: /^Execute 3$/ })).toBeVisible()
    await page.getByRole('button', { name: /^Execute 3$/ }).click()

    const dialog = page.getByRole('dialog', { name: 'Move the approved files?' })
    await expect(dialog).toBeVisible()
    await dialog.getByRole('button', { name: 'Execute approved moves' }).click()

    await expect(page.locator('.plan .pill').filter({ hasText: 'Completed' }).first()).toBeVisible({
      timeout: 25_000,
    })
    expect(existsSync(join(folder.path, 'meeting-notes.txt'))).toBe(false)
    expect(existsSync(join(folder.path, 'GhostShift Organizer'))).toBe(true)

    await page.getByRole('button', { name: 'Undo', exact: true }).click()
    const undoDialog = page.getByRole('dialog', { name: 'Undo this organization plan?' })
    await undoDialog.getByRole('button', { name: 'Undo verified moves' }).click()

    await expect(page.locator('.plan .pill').filter({ hasText: 'Undone' }).first()).toBeVisible()
    expect(existsSync(join(folder.path, 'meeting-notes.txt'))).toBe(true)
    expect(existsSync(join(folder.path, 'data.csv'))).toBe(true)
  } finally {
    await removeFolder(folder)
  }
})
