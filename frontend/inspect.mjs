import { chromium } from '@playwright/test'

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 })
await page.goto('http://127.0.0.1:5173/brain', { waitUntil: 'networkidle' })
await page.waitForTimeout(800)
const info = await page.evaluate(() => {
  const label = document.querySelector('.brain-hero__stats span')
  const title = document.querySelector('.brain-hero h2')
  const style = label ? getComputedStyle(label) : null
  return {
    labelText: label?.textContent,
    labelTransform: style?.textTransform,
    labelFont: style?.fontFamily,
    labelSize: style?.fontSize,
    titleWeight: title ? getComputedStyle(title).fontWeight : null,
    css: Array.from(document.styleSheets).flatMap((sheet) => {
      try {
        return Array.from(sheet.cssRules)
          .filter((rule) => rule.cssText.includes('brain-hero__stats span'))
          .map((rule) => rule.cssText)
      } catch {
        return []
      }
    }),
  }
})
console.log(JSON.stringify(info, null, 2))
await page.locator('.brain-hero').screenshot({ path: 'C:/Users/senth/AppData/Local/Temp/ghostshift-redesign/brain-hero-390.png' })
await browser.close()
