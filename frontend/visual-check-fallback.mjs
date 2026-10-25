import { chromium } from '@playwright/test'

const app = 'http://127.0.0.1:5173'
const out = 'C:/Users/senth/AppData/Local/Temp/ghostshift-redesign'

const browser = await chromium.launch({ headless: true, args: ['--disable-webgl', '--disable-webgl2'] })
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 })
const errors = []
page.on('console', (message) => {
  if (message.type() === 'error') errors.push(`console: ${message.text()}`)
})
page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`))
await page.goto(`${app}/overview`, { waitUntil: 'networkidle' })
await page.waitForTimeout(3000)
const state = await page.evaluate(() => {
  const node = document.querySelector('.ledger__fallback')
  return {
    body: document.body.innerText.slice(0, 200),
    boot: Boolean(document.querySelector('.boot')),
    ledger: Boolean(document.querySelector('.ledger')),
    fallbackCount: document.querySelectorAll('.ledger__fallback').length,
    fallbackDisplay: node ? getComputedStyle(node).display : null,
    fallbackRect: node?.getBoundingClientRect().toJSON() ?? null,
    canvasCount: document.querySelectorAll('.ledger__canvas').length,
  }
})
await page.screenshot({ path: `${out}/overview-webgl-disabled-1440.png`, fullPage: true })
console.log(JSON.stringify({ state, errors }, null, 2))
await browser.close()
