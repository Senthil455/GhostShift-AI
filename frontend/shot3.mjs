import { chromium } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const width = Number(process.env.W || 1440)
const suffix = process.env.S || ''
const full = process.env.FULL === '1'
const routes = process.argv.slice(2)
mkdirSync('.shots', { recursive: true })

const browser = await chromium.launch()
const context = await browser.newContext({ baseURL: 'http://127.0.0.1:5173', viewport: { width, height: 900 } })
const page = await context.newPage()
for (const route of routes.length ? routes : ['/overview']) {
  await page.goto(route, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1600)
  await page.screenshot({ path: `.shots/${route.replaceAll('/', '_')}${suffix}${full ? '_full' : ''}.png`, fullPage: full })
  console.log('shot', route)
}
await browser.close()
