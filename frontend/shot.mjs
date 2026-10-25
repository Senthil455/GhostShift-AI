import { chromium } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const routes = process.argv.slice(2)
const dir = '.shots'
mkdirSync(dir, { recursive: true })

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
for (const route of routes.length ? routes : ['/welcome']) {
  await page.goto(`http://127.0.0.1:5173${route}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1800)
  const name = route.replaceAll('/', '_') || '_root'
  await page.screenshot({ path: `${dir}/${name}.png`, fullPage: false })
  console.log('shot', route)
}
await browser.close()
