import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, downloadReport, hasApiToken, setApiToken } from './client'
import type { ReportSummary } from './types'

const fetchMock = vi.fn()

function jsonResponse(body: unknown, status = 200, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}

describe('API client', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    setApiToken('')
  })

  afterEach(() => {
    setApiToken('')
    vi.unstubAllGlobals()
  })

  it('sends JSON requests under the versioned API prefix', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ status: 'OK' }))

    const result = await api.health()

    expect(result).toEqual({ status: 'OK' })
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/health')
    expect(init.method).toBeUndefined()
    expect((init.headers as Headers).get('Accept')).toBe('application/json')
  })

  it('attaches the in-memory token only after it is set', async () => {
    fetchMock.mockImplementation(async () => jsonResponse({ status: 'OK' }))
    await api.health()
    expect((fetchMock.mock.calls[0][1].headers as Headers).has('Authorization')).toBe(false)

    setApiToken('  local-secret  ')
    expect(hasApiToken()).toBe(true)
    await api.health()
    expect((fetchMock.mock.calls[1][1].headers as Headers).get('Authorization')).toBe('Bearer local-secret')
  })

  it('sends a JSON content type and serialized body for mutations', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ theme: 'light' }))

    await api.updateSettings({ theme: 'light' })

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/settings')
    expect(init.method).toBe('PATCH')
    expect(init.body).toBe('{"theme":"light"}')
    expect((init.headers as Headers).get('Content-Type')).toBe('application/json')
  })

  it('returns undefined for no-content responses', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
    await expect(api.removeDirectory(7)).resolves.toBeUndefined()
  })

  it('maps structured backend errors to ApiError', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(
        {
          code: 'VALIDATION_ERROR',
          message: 'The request was invalid.',
          details: { errors: ['Path must be absolute'] },
          requestId: 'req-123',
        },
        400,
        { 'X-Request-Id': 'header-id' },
      ),
    )

    const error = await api.registerDirectory({ path: 'relative' }).catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as ApiError
    expect(apiError.status).toBe(400)
    expect(apiError.code).toBe('VALIDATION_ERROR')
    expect(apiError.message).toBe('The request was invalid.')
    expect(apiError.details).toEqual({ errors: ['Path must be absolute'] })
    expect(apiError.requestId).toBe('req-123')
  })

  it('falls back to the header request id when the body has none', async () => {
    fetchMock.mockResolvedValue(
      new Response('not json', { status: 500, headers: { 'X-Request-Id': 'header-id' } }),
    )

    const error = (await api.health().catch((caught: unknown) => caught)) as ApiError
    expect(error.code).toBe('HTTP_500')
    expect(error.requestId).toBe('header-id')
  })

  it('reports a clear message when the backend is unreachable', async () => {
    fetchMock.mockRejectedValue(new TypeError('fetch failed'))

    const error = (await api.health().catch((caught: unknown) => caught)) as ApiError
    expect(error.code).toBe('NETWORK_ERROR')
    expect(error.message).toContain('could not reach its local backend')
  })

  it('uses provided markdown content for report downloads', async () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    const createObjectURL = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:ghostshift-test')
    const report = { id: 'r1', title: 'Weekly/Review' } as ReportSummary

    await downloadReport(report, '# Report')

    expect(fetchMock).not.toHaveBeenCalled()
    expect(click).toHaveBeenCalledTimes(1)
    expect(createObjectURL).toHaveBeenCalled()
    click.mockRestore()
    createObjectURL.mockRestore()
  })
})
