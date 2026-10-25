import { chromium } from '@playwright/test'

const api = 'http://127.0.0.1:8081/api/v1'
const app = 'http://127.0.0.1:5173'

async function patch(data) {
  const response = await fetch(`${api}/settings`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
  if (!response.ok) throw new Error(`settings patch failed: ${response.status} ${await response.text()}`)
}

async function openOverview(page) {
  await page.goto(`${app}/overview`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.ledger__canvas')
  await page.waitForTimeout(1200)
}

async function findHoverTarget(page) {
  const canvas = page.locator('.ledger__canvas')
  const box = await canvas.boundingBox()
  if (!box) throw new Error('canvas has no box')
  const readout = page.locator('.ledger__readout strong')
  const samples = []
  const points = []
  for (const y of [0.35, 0.45, 0.55, 0.65]) {
    for (const x of [0.28, 0.38, 0.48, 0.58, 0.68]) {
      points.push({ x: box.x + box.width * x, y: box.y + box.height * y })
    }
  }
  for (const point of points) {
    await page.mouse.move(point.x, point.y)
    await page.waitForTimeout(100)
    const label = (await readout.innerText()).trim()
    samples.push({ x: Math.round(point.x), y: Math.round(point.y), label })
    if (label !== 'GhostShift archive') return { box, samples, target: { ...point, label } }
  }
  return { box, samples, target: null }
}

await patch({ onboardingCompleted: true, theme: 'dark' })

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 })
const errors = []
page.on('console', (message) => {
  if (message.type() === 'error') errors.push(`console: ${message.text()}`)
})
page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`))

await openOverview(page)
const hover = await findHoverTarget(page)

let clickedUrl = null
if (hover.target) {
  await page.mouse.move(hover.target.x, hover.target.y)
  await page.waitForTimeout(100)
  await page.mouse.click(hover.target.x, hover.target.y)
  await page.waitForTimeout(700)
  clickedUrl = page.url()
}

if (clickedUrl && !clickedUrl.endsWith('/overview')) {
  await openOverview(page)
}
const dragHover = await findHoverTarget(page)

let draggedClass = null
let dragReleasedClass = null
if (dragHover.target) {
  await page.mouse.move(dragHover.target.x, dragHover.target.y)
  await page.mouse.down()
  await page.mouse.move(dragHover.target.x + 90, dragHover.target.y + 24, { steps: 5 })
  draggedClass = await page.locator('.ledger').evaluate((node) => node.classList.contains('ledger--dragging'))
  await page.mouse.up()
  dragReleasedClass = await page.locator('.ledger').evaluate((node) => node.classList.contains('ledger--dragging'))
}

const overflow = await page.evaluate(() => ({
  scrollWidth: document.documentElement.scrollWidth,
  innerWidth: window.innerWidth,
  hasHorizontalOverflow: document.documentElement.scrollWidth > window.innerWidth + 1,
}))

await patch({ onboardingCompleted: true, theme: 'dark' })
console.log(
  JSON.stringify(
    {
      box: hover.box,
      hoverSamples: hover.samples,
      target: hover.target,
      clickedUrl,
      dragSamples: dragHover.samples,
      dragTarget: dragHover.target,
      draggedClass,
      dragReleasedClass,
      overflow,
      errors,
    },
    null,
    2,
  ),
)
await browser.close()
