import { chromium } from '@playwright/test'

const api = 'http://127.0.0.1:8081/api/v1'
const app = 'http://127.0.0.1:5173'

async function setOnboarding(value) {
  const response = await fetch(`${api}/settings`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ onboardingCompleted: value }),
  })
  if (!response.ok) throw new Error(`settings patch failed: ${response.status}`)
}

async function run(label, options = {}) {
  const browser = await chromium.launch({ headless: true, ...(options.launch ?? {}) })
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 })
  if (options.reducedMotion) await page.emulateMedia({ reducedMotion: 'reduce' })
  const errors = []
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text())
  })
  page.on('pageerror', (error) => errors.push(error.message))
  const t0 = Date.now()
  await page.goto(`${app}/welcome`, { waitUntil: 'load' })
  const gotoMs = Date.now() - t0
  const dom = await page.evaluate(() => ({
    readyState: document.readyState,
    principles: document.querySelector('.welcome__principles')?.getBoundingClientRect().toJSON() ?? null,
    text: document.body.innerText.includes('Files stay here'),
    ledger: document.querySelector('.ledger')?.getBoundingClientRect().toJSON() ?? null,
  }))
  const t1 = Date.now()
  let waitError = null
  try {
    await page.getByText('Files stay here').waitFor({ state: 'visible', timeout: 45000 })
  } catch (cause) {
    waitError = String(cause).slice(0, 200)
  }
  const waitMs = Date.now() - t1
  console.log(JSON.stringify({ label, gotoMs, waitMs, dom, waitError, errors }, null, 2))
  await browser.close()
}

await setOnboarding(false)
await run('normal')
await run('reduced-motion', { reducedMotion: true })
await run('no-webgl', { launch: { args: ['--disable-webgl', '--disable-webgl2'] } })
await setOnboarding(true)
