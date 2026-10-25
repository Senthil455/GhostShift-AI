import { chromium, request } from '@playwright/test'
import { mkdir } from 'node:fs/promises'

const base = 'http://127.0.0.1:5173'
const api = 'http://127.0.0.1:8081'
const out = 'C:/Users/senth/AppData/Local/Temp/opencode/ghostshift-screens'
await mkdir(out, { recursive: true })

const context = await request.newContext({ baseURL: api })
await context.patch('/api/v1/settings', { data: { onboardingCompleted: true, theme: 'dark' } })
const runs = (await (await context.get('/api/v1/runs?limit=1')).json())
const runId = runs[0]?.id
await context.dispose()

const routes = [
  ['overview', '/overview'],
  ['sort', '/sort'],
  ['dev', '/dev'],
  ['capture', '/capture'],
  ['replay', '/replay'],
  ['brain', '/brain'],
  ['pulse', '/pulse'],
  ['approvals', '/approvals'],
  ['history', '/history'],
  ['settings', '/settings'],
  ['welcome', '/welcome'],
  ...(runId ? [['run-detail', `/history/runs/${runId}`]] : []),
]

const browser = await chromium.launch()
const errors = []
for (const [viewportName, viewport] of [
  ['desktop', { width: 1440, height: 1000 }],
  ['mobile', { width: 390, height: 844 }],
]) {
  const page = await browser.newPage({ viewport })
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(`${viewportName} console: ${message.text()}`)
  })
  page.on('pageerror', (error) => errors.push(`${viewportName} pageerror: ${error.message}`))
  for (const [name, path] of routes) {
    if (viewportName === 'mobile' && !['overview', 'sort', 'replay', 'settings', 'welcome'].includes(name)) continue
    await page.goto(`${base}${path}`, { waitUntil: 'networkidle' })
    await page.waitForTimeout(350)
    await page.screenshot({ path: `${out}/${viewportName}-${name}.png`, fullPage: true })
  }
  await page.close()
}
await browser.close()
console.log(out)
if (errors.length) console.log(errors.join('\n'))
