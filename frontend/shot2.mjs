import { chromium } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const width = Number(process.env.W || 1440)
const height = Number(process.env.H || 900)
const suffix = process.env.S || ''
const routes = process.argv.slice(2)
mkdirSync('.shots', { recursive: true })

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: 1 })
for (const route of routes.length ? routes : ['/overview']) {
  await page.goto(`http://127.0.0.1:5173${route}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1600)
  await page.screenshot({ path: `.shots/${route.replaceAll('/', '_')}${suffix}.png` })
  console.log('shot', route, width)
}
await browser.close()
