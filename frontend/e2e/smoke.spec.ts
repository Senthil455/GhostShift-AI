import { expect, test } from '@playwright/test'
import { setOnboarding, setTheme } from './helpers'

test.describe('onboarding and navigation', () => {
  test.afterAll(async () => {
    await setOnboarding(true)
    await setTheme('dark')
  })

  test('first visit redirects to the welcome page and entering completes onboarding', async ({ page }) => {
    await setOnboarding(false)
    await page.goto('/overview')

    await expect(page).toHaveURL(/\/welcome$/)
    await expect(page.getByRole('heading', { name: "Your work doesn't need the cloud." })).toBeVisible()
    await expect(page.getByText('Files stay here')).toBeVisible()

    await page.getByRole('button', { name: 'Enter the workspace' }).click()
    await expect(page).toHaveURL(/\/overview$/)
    await expect(page.getByRole('navigation', { name: 'Primary' })).toBeVisible()
    await expect(page.locator('.topbar__title strong')).toHaveText('Overview')
  })

  test('all ten workspace sections load through the sidebar', async ({ page }) => {
    await setOnboarding(true)
    await page.goto('/overview')
    const nav = page.getByRole('navigation', { name: 'Primary' })
    await expect(nav).toBeVisible()

    const destinations = [
      { name: 'GhostSort', url: /\/sort$/, title: 'GhostSort' },
      { name: 'GhostDev', url: /\/dev$/, title: 'GhostDev' },
      { name: 'GhostCapture', url: /\/capture$/, title: 'GhostCapture' },
      { name: 'GhostReplay', url: /\/replay$/, title: 'GhostReplay' },
      { name: 'GhostBrain', url: /\/brain$/, title: 'GhostBrain' },
      { name: 'GhostPulse', url: /\/pulse$/, title: 'GhostPulse' },
      { name: 'Approvals', url: /\/approvals$/, title: 'Approvals' },
      { name: 'History', url: /\/history$/, title: 'History' },
      { name: 'Settings', url: /\/settings$/, title: 'Settings' },
      { name: 'Overview', url: /\/overview$/, title: 'Overview' },
    ]

    for (const destination of destinations) {
      await nav.getByRole('link', { name: new RegExp(`^${destination.name}`) }).click()
      await expect(page).toHaveURL(destination.url)
      await expect(page.locator('.topbar__title strong')).toHaveText(destination.title)
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    }
  })

  test('unknown routes keep a calm, useful 404 inside the workspace', async ({ page }) => {
    await setOnboarding(true)
    await page.goto('/this-route-does-not-exist')

    await expect(page.getByRole('heading', { name: 'This corridor goes nowhere.' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Return to overview' })).toBeVisible()
    await page.getByRole('link', { name: 'Return to overview' }).click()
    await expect(page).toHaveURL(/\/overview$/)
  })

  test('theme switching persists through the settings API', async ({ page }) => {
    await setOnboarding(true)
    await setTheme('dark')
    await page.goto('/settings')
    await expect(page.getByRole('heading', { name: 'Appearance' })).toBeVisible()

    await page.getByRole('button', { name: /Light/ }).click()
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')

    await page.getByRole('button', { name: /Dark/ }).click()
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  })

  test('an unsafe model endpoint is rejected by the backend with a visible error', async ({ page }) => {
    await setOnboarding(true)
    await page.goto('/settings')
    await expect(page.getByRole('heading', { name: 'GhostBrain model' })).toBeVisible()

    await page.getByLabel('Ollama endpoint').fill('file:///etc/passwd')
    await page.getByRole('button', { name: 'Save model settings' }).click()
    await expect(page.getByRole('alert').first()).toContainText('http')
  })
})
