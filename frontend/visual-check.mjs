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

await patch({ onboardingCompleted: true, theme: 'dark' })
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 })
const errors = []
page.on('console', (message) => {
  if (message.type() === 'error') errors.push(`console: ${message.text()}`)
})
page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`))

for (const route of ['dev', 'capture', 'replay', 'pulse', 'history/runs/1', 'not-a-real-page']) {
  await page.goto(`http://127.0.0.1:5173/${route}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(900)
  await page.screenshot({ path: `${out}/${route.replaceAll('/', '-')}-dark-1440.png`, fullPage: true })
}

await page.setViewportSize({ width: 390, height: 844 })
for (const route of ['sort', 'brain', 'settings']) {
  await page.goto(`http://127.0.0.1:5173/${route}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(700)
  await page.screenshot({ path: `${out}/${route}-dark-390.png`, fullPage: true })
  const overflow = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }))
  console.log(route, overflow)
}

console.log(JSON.stringify({ errors }, null, 2))
await browser.close()
