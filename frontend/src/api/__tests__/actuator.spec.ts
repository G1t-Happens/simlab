import { describe, expect, it, vi } from 'vitest'

import { fetchHealthStatus } from '../actuator'

function stubFetch(response: Response) {
  const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('fetchHealthStatus', () => {
  it('requests the health endpoint as JSON, past every cache', async () => {
    const fetchMock = stubFetch(Response.json({ status: 'UP' }))
    const { signal } = new AbortController()

    await fetchHealthStatus(signal)

    expect(fetchMock).toHaveBeenCalledExactlyOnceWith('/actuator/health', {
      headers: { Accept: 'application/json' },
      cache: 'no-store',
      signal,
    })
  })

  it('returns the reported status', async () => {
    stubFetch(Response.json({ status: 'UP' }))

    await expect(fetchHealthStatus()).resolves.toBe('UP')
  })

  it.each(['DOWN', 'OUT_OF_SERVICE'])(
    'reads %s from the body of a 503 response',
    async (status) => {
      stubFetch(Response.json({ status }, { status: 503 }))

      await expect(fetchHealthStatus()).resolves.toBe(status)
    },
  )

  it('reports custom statuses as UNKNOWN', async () => {
    stubFetch(Response.json({ status: 'DEGRADED' }))

    await expect(fetchHealthStatus()).resolves.toBe('UNKNOWN')
  })

  it('fails on other error responses', async () => {
    stubFetch(new Response('Bad Gateway', { status: 502 }))

    await expect(fetchHealthStatus()).rejects.toThrow('HTTP status 502')
  })

  it('fails if the body is not a health document', async () => {
    stubFetch(Response.json({ healthy: true }))

    await expect(fetchHealthStatus()).rejects.toThrow('did not return a health document')
  })

  it('fails if the backend cannot be reached', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>().mockRejectedValue(new TypeError('Failed to fetch')),
    )

    await expect(fetchHealthStatus()).rejects.toThrow('Failed to fetch')
  })
})
