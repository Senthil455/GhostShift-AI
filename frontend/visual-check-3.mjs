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
const errors = []
page.on('console', (message) => {
  if (message.type() === 'error') errors.push(`console: ${message.text()}`)
})
page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`))

await patch({ onboardingCompleted: true, theme: 'dark' })
await page.goto(`${app}/overview`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)
await page.screenshot({ path: `${out}/overview-dark-1440-new.png`, fullPage: true })
const desktopMetrics = await page.evaluate(() => {
  const ledger = document.querySelector('.ledger')
  const stage = document.querySelector('.ledger__stage')
  const bottom = document.querySelector('.ledger__bottom')
  const index = document.querySelector('.ledger__index')
  return {
    ledger: ledger?.getBoundingClientRect().toJSON(),
    stage: stage?.getBoundingClientRect().toJSON(),
    bottom: bottom?.getBoundingClientRect().toJSON(),
    index: index?.getBoundingClientRect().toJSON(),
  }
})

await patch({ onboardingCompleted: false, theme: 'dark' })
await page.goto(`${app}/overview`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1600)
await page.screenshot({ path: `${out}/welcome-dark-1440-new.png`, fullPage: true })
const welcomeMetrics = await page.evaluate(() => {
  const ledger = document.querySelector('.ledger')
  const stage = document.querySelector('.ledger__stage')
  const bottom = document.querySelector('.ledger__bottom')
  return {
    ledger: ledger?.getBoundingClientRect().toJSON(),
    stage: stage?.getBoundingClientRect().toJSON(),
    bottom: bottom?.getBoundingClientRect().toJSON(),
  }
})

await patch({ onboardingCompleted: true, theme: 'dark' })
await page.setViewportSize({ width: 390, height: 844 })
await page.goto(`${app}/overview`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)
await page.screenshot({ path: `${out}/overview-dark-390-new.png`, fullPage: true })
const mobileMetrics = await page.evaluate(() => {
  const ledger = document.querySelector('.ledger')
  const stage = document.querySelector('.ledger__stage')
  const bottom = document.querySelector('.ledger__bottom')
  const index = document.querySelector('.ledger__index')
  return {
    ledger: ledger?.getBoundingClientRect().toJSON(),
    stage: stage?.getBoundingClientRect().toJSON(),
    bottom: bottom?.getBoundingClientRect().toJSON(),
    index: index?.getBoundingClientRect().toJSON(),
  }
})

const fallbackPage = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 })
const fallbackErrors = []
fallbackPage.on('console', (message) => {
  if (message.type() === 'error') fallbackErrors.push(`console: ${message.text()}`)
})
fallbackPage.on('pageerror', (error) => fallbackErrors.push(`pageerror: ${error.message}`))
await fallbackPage.addInitScript(() => {
  const original = HTMLCanvasElement.prototype.getContext
  HTMLCanvasElement.prototype.getContext = function (type, ...args) {
    if (type === 'webgl' || type === 'webgl2' || type === 'experimental-webgl') return null
    return original.call(this, type, ...args)
  }
})
await fallbackPage.goto(`${app}/overview`, { waitUntil: 'domcontentloaded' })
await fallbackPage.waitForTimeout(5000)
const fallback = await fallbackPage.evaluate(() => {
  const node = document.querySelector('.ledger__fallback')
  return {
    body: document.body.innerText.slice(0, 160),
    count: document.querySelectorAll('.ledger__fallback').length,
    visible: node ? getComputedStyle(node).display !== 'none' && node.getBoundingClientRect().height > 0 : false,
    boot: Boolean(document.querySelector('.boot')),
    ledger: Boolean(document.querySelector('.ledger')),
  }
})
await fallbackPage.screenshot({ path: `${out}/overview-webgl-fallback-1440-new.png`, fullPage: true })

console.log(JSON.stringify({ desktopMetrics, welcomeMetrics, mobileMetrics, fallback, errors, fallbackErrors }, null, 2))
await browser.close()
