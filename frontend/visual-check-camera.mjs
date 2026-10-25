import { chromium } from '@playwright/test'

const api = 'http://127.0.0.1:8081/api/v1'
const app = 'http://127.0.0.1:5173'
const out = 'C:/Users/senth/AppData/Local/Temp/ghostshift-redesign'

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
await patch({ onboardingCompleted: true, theme: 'dark' })
await page.goto(`${app}/overview`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)
await page.screenshot({ path: `${out}/overview-camera-1440.png`, fullPage: true })
await page.setViewportSize({ width: 390, height: 844 })
await page.goto(`${app}/overview`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)
await page.screenshot({ path: `${out}/overview-camera-390.png`, fullPage: true })
await patch({ onboardingCompleted: true, theme: 'light' })
await page.goto(`${app}/overview`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)
await page.screenshot({ path: `${out}/overview-light-390.png`, fullPage: true })
await browser.close()
