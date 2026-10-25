import { chromium } from '@playwright/test'
const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 390, height: 844 } })
for (const path of ['/overview','/sort','/dev','/capture','/replay','/brain','/pulse','/approvals','/history','/settings','/welcome']) {
  await page.goto('http://127.0.0.1:5173' + path, { waitUntil: 'networkidle' })
  await page.waitForTimeout(250)
  const info = await page.evaluate(() => ({ doc: document.documentElement.scrollWidth, body: document.body.scrollWidth, vw: innerWidth }))
  console.log(path, JSON.stringify(info))
}
await page.goto('http://127.0.0.1:5173/sort', { waitUntil: 'networkidle' })
await page.waitForTimeout(300)
await page.screenshot({ path: 'C:/Users/senth/AppData/Local/Temp/opencode/ghostshift-screens/mobile-sort-fixed.png', fullPage: true })
await page.goto('http://127.0.0.1:5173/capture', { waitUntil: 'networkidle' })
await page.waitForTimeout(300)
await page.screenshot({ path: 'C:/Users/senth/AppData/Local/Temp/opencode/ghostshift-screens/desktop-capture-fixed.png', fullPage: true })
await browser.close()
