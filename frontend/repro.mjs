import { chromium, request } from '@playwright/test'

const api = await request.newContext({ baseURL: 'http://127.0.0.1:8081' })
await api.patch('/api/v1/settings', { data: { onboardingCompleted: false } })

const browser = await chromium.launch()
const context = await browser.newContext({ baseURL: 'http://127.0.0.1:5173', viewport: { width: 1440, height: 1000 } })
const page = await context.newPage()
page.on('console', (msg) => {
  if (msg.type() === 'error') console.log('console error:', msg.text().slice(0, 200))
})
page.on('pageerror', (err) => console.log('page error:', err.message))

let start = Date.now()
await page.goto('/overview')
await page.waitForURL(/welcome/, { timeout: 30000 })
console.log('redirect', Date.now() - start, 'ms')
await page.getByRole('heading', { name: "Your work doesn't need the cloud." }).waitFor()
console.log('heading', Date.now() - start, 'ms')

start = Date.now()
await page.getByRole('button', { name: 'Enter the workspace' }).click({ timeout: 30000 })
console.log('click', Date.now() - start, 'ms')
await page.waitForURL(/overview/, { timeout: 30000 })
console.log('url', Date.now() - start, 'ms')
await page.getByRole('navigation', { name: 'Primary' }).waitFor({ state: 'visible', timeout: 30000 })
console.log('nav', Date.now() - start, 'ms')
console.log('title', await page.locator('.topbar__title strong').textContent())

await browser.close()
await api.patch('/api/v1/settings', { data: { onboardingCompleted: true } })
await api.dispose()
