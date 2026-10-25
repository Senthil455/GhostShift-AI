import { chromium } from '@playwright/test'
import { mkdir } from 'node:fs/promises'

const api = 'http://127.0.0.1:8081/api/v1'
const out = 'C:/Users/senth/AppData/Local/Temp/ghostshift-redesign'
await mkdir(out, { recursive: true })

async function patch(data) {
  const response = await fetch(`${api}/settings`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
  if (!response.ok) throw new Error(`settings patch failed: ${response.status} ${await response.text()}`)
}

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 })
const errors = []
page.on('console', (message) => {
  if (message.type() === 'error') errors.push(`console: ${message.text()}`)
})
page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`))

await patch({ onboardingCompleted: false, theme: 'dark' })
await page.goto('http://127.0.0.1:5173/overview', { waitUntil: 'networkidle' })
await page.waitForTimeout(2600)
await page.screenshot({ path: `${out}/welcome-dark-1440.png`, fullPage: true })

await patch({ onboardingCompleted: true, theme: 'dark' })
await page.goto('http://127.0.0.1:5173/overview', { waitUntil: 'networkidle' })
await page.waitForTimeout(2600)
await page.screenshot({ path: `${out}/overview-dark-1440.png`, fullPage: true })

for (const route of ['sort', 'brain', 'settings', 'history', 'approvals']) {
  await page.goto(`http://127.0.0.1:5173/${route}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(700)
  await page.screenshot({ path: `${out}/${route}-dark-1440.png`, fullPage: true })
}

await page.goto('http://127.0.0.1:5173/settings', { waitUntil: 'networkidle' })
await page.waitForTimeout(600)
await page.getByRole('button', { name: /Light/ }).click()
await page.waitForTimeout(900)
await page.goto('http://127.0.0.1:5173/overview', { waitUntil: 'networkidle' })
await page.waitForTimeout(2200)
await page.screenshot({ path: `${out}/overview-light-1440.png`, fullPage: true })

await patch({ onboardingCompleted: true, theme: 'dark' })
await page.setViewportSize({ width: 390, height: 844 })
await page.goto('http://127.0.0.1:5173/overview', { waitUntil: 'networkidle' })
await page.waitForTimeout(2200)
await page.screenshot({ path: `${out}/overview-dark-390.png`, fullPage: true })

await patch({ onboardingCompleted: false, theme: 'dark' })
await page.goto('http://127.0.0.1:5173/overview', { waitUntil: 'networkidle' })
await page.waitForTimeout(2200)
await page.screenshot({ path: `${out}/welcome-dark-390.png`, fullPage: true })

await patch({ onboardingCompleted: true, theme: 'dark' })
await page.goto('http://127.0.0.1:5173/overview', { waitUntil: 'networkidle' })
await page.waitForTimeout(800)
const metrics = await page.evaluate(() => ({
  scrollWidth: document.documentElement.scrollWidth,
  clientWidth: document.documentElement.clientWidth,
  bodyHeight: document.body.scrollHeight,
  canvas: Array.from(document.querySelectorAll('canvas')).map((canvas) => ({
    width: canvas.width,
    height: canvas.height,
    rect: canvas.getBoundingClientRect().toJSON(),
  })),
}))
console.log(JSON.stringify({ errors, metrics }, null, 2))
await browser.close()
