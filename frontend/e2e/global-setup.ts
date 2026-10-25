import { request } from '@playwright/test'

/**
 * Fail early with a useful message if the API the UI proxy targets is not running.
 * Playwright starts Vite, but the separate backend is intentionally owned by the
 * operator so E2E can run against an isolated data directory.
 */
export default async function globalSetup(): Promise<void> {
  const apiURL = process.env.E2E_API_URL ?? 'http://127.0.0.1:8081'
  const context = await request.newContext({ baseURL: apiURL })
  try {
    const response = await context.get('/api/v1/health', { timeout: 10_000 })
    if (!response.ok()) {
      throw new Error(`GhostShift API health check failed with HTTP ${response.status()} at ${apiURL}`)
    }
    const body = (await response.json()) as { database?: string; status?: string }
    if (body.database !== 'OK') {
      throw new Error(`GhostShift API is running but SQLite is not healthy at ${apiURL}`)
    }
  } catch (cause) {
    throw new Error(
      `GhostShift E2E needs a running backend at ${apiURL}. ` +
        `Start it with GHOSTSHIFT_PORT=8081, then run npm run e2e. ` +
        `Original error: ${(cause as Error).message}`,
    )
  } finally {
    await context.dispose()
  }
}
