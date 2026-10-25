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
const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 })
const errors = []
page.on('console', (message) => {
  if (message.type() === 'error') errors.push(`console: ${message.text()}`)
})
page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`))

await patch({ onboardingCompleted: true, theme: 'dark' })
await page.goto(`${app}/overview`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)
await page.screenshot({ path: `${out}/overview-390-full.png`, fullPage: true })
const stageMetrics = await page.evaluate(() => {
  const stage = document.querySelector('.ledger__stage')
  const index = document.querySelector('.ledger__index')
  const bottom = document.querySelector('.ledger__bottom')
  const canvas = document.querySelector('.ledger__canvas')
  const ledger = document.querySelector('.ledger')
  return {
    ledger: ledger?.getBoundingClientRect().toJSON(),
    stage: stage?.getBoundingClientRect().toJSON(),
    index: index?.getBoundingClientRect().toJSON(),
    bottom: bottom?.getBoundingClientRect().toJSON(),
    canvas: canvas?.getBoundingClientRect().toJSON(),
  }
})
console.log(JSON.stringify({ stageMetrics }, null, 2))

await patch({ onboardingCompleted: false, theme: 'dark' })
await page.goto(`${app}/overview`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1600)
await page.screenshot({ path: `${out}/welcome-dark-390.png`, fullPage: true })

await patch({ onboardingCompleted: true, theme: 'dark' })
await page.setViewportSize({ width: 1440, height: 1000 })
await page.emulateMedia({ reducedMotion: 'reduce' })
await page.goto(`${app}/overview`, { waitUntil: 'networkidle' })
await page.waitForTimeout(800)
await page.screenshot({ path: `${out}/overview-reduced-motion-1440.png`, fullPage: true })
await page.emulateMedia({ reducedMotion: 'no-preference' })

const fallbackPage = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 })
await fallbackPage.addInitScript(() => {
  const original = HTMLCanvasElement.prototype.getContext
  HTMLCanvasElement.prototype.getContext = function (type, ...args) {
    if (type === 'webgl' || type === 'webgl2' || type === 'experimental-webgl') return null
    return original.call(this, type, ...args)
  }
})
await fallbackPage.goto(`${app}/overview`, { waitUntil: 'networkidle' })
await fallbackPage.waitForTimeout(1000)
const fallbackVisible = await fallbackPage.locator('.ledger__fallback').isVisible().catch(() => false)
await fallbackPage.screenshot({ path: `${out}/overview-webgl-fallback-1440.png`, fullPage: true })
console.log(JSON.stringify({ fallbackVisible, errors }, null, 2))
await browser.close()
