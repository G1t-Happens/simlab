import { mount } from '@vue/test-utils'
import { defineComponent, effectScope } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { fakeBackendHealth } from '@/__tests__/fakeBackendHealth'
import { fetchHealthStatus } from '@/api/actuator'

import {
  injectBackendHealth,
  useBackendHealth,
  type BackendHealthOptions,
  type BackendHealthState,
} from '../useBackendHealth'

vi.mock('@/api/actuator')

const fetchHealthStatusMock = vi.mocked(fetchHealthStatus)

const OPTIONS: BackendHealthOptions = { interval: 3_000, retryInterval: 1_000, timeout: 500 }

/** runs the composable in an effect scope of its own, like a component would */
function startMonitoring(options = OPTIONS) {
  const scope = effectScope()
  const state = scope.run(() => useBackendHealth(options))
  if (state === undefined) {
    throw new Error('Effect scope is inactive')
  }
  return {
    ...state,
    stop: () => {
      scope.stop()
    },
  }
}

/** a request that only settles when it's aborted */
function rejectOnAbort(signal?: AbortSignal): Promise<never> {
  return new Promise((_, reject) => {
    signal?.addEventListener('abort', () => {
      reject(new DOMException('The operation was aborted.', 'AbortError'))
    })
  })
}

/** shows or hides the page, like switching tabs does */
function setPageHidden(hidden: boolean): void {
  vi.spyOn(document, 'hidden', 'get').mockReturnValue(hidden)
  document.dispatchEvent(new Event('visibilitychange'))
}

describe('useBackendHealth', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('is pending until the first check completes', () => {
    fetchHealthStatusMock.mockImplementation(rejectOnAbort)

    const { health, stop } = startMonitoring()

    expect(health.value).toBe('PENDING')
    stop()
  })

  it('reports the status of the backend', async () => {
    fetchHealthStatusMock.mockResolvedValue('UP')

    const { health, stop } = startMonitoring()
    await vi.advanceTimersByTimeAsync(0)

    expect(health.value).toBe('UP')
    stop()
  })

  it('reports the backend as unreachable if a check fails', async () => {
    fetchHealthStatusMock.mockRejectedValue(new TypeError('Failed to fetch'))

    const { health, stop } = startMonitoring()
    await vi.advanceTimersByTimeAsync(0)

    expect(health.value).toBe('UNREACHABLE')
    stop()
  })

  it('reports the backend as unreachable if a check times out', async () => {
    // fake timers don't reach the timer behind AbortSignal.timeout(), so the timeout is triggered
    // by hand
    const timeout = new AbortController()
    const timeoutSpy = vi.spyOn(AbortSignal, 'timeout').mockReturnValue(timeout.signal)
    fetchHealthStatusMock.mockImplementation(rejectOnAbort)

    const { health, stop } = startMonitoring()
    timeout.abort(new DOMException('The operation timed out.', 'TimeoutError'))
    await vi.advanceTimersByTimeAsync(0)

    expect(timeoutSpy).toHaveBeenCalledExactlyOnceWith(500)
    expect(health.value).toBe('UNREACHABLE')
    stop()
  })

  it('checks again after the interval while the backend is up', async () => {
    fetchHealthStatusMock.mockResolvedValueOnce('UP').mockResolvedValueOnce('DOWN')

    const { health, stop } = startMonitoring()
    await vi.advanceTimersByTimeAsync(2_999)

    expect(health.value).toBe('UP')
    expect(fetchHealthStatusMock).toHaveBeenCalledOnce()

    await vi.advanceTimersByTimeAsync(1)

    expect(health.value).toBe('DOWN')
    stop()
  })

  it('checks again sooner while the backend is not up, so that its return shows soon', async () => {
    fetchHealthStatusMock
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce('UP')

    const { health, stop } = startMonitoring()
    await vi.advanceTimersByTimeAsync(999)

    expect(health.value).toBe('UNREACHABLE')

    await vi.advanceTimersByTimeAsync(1)

    expect(health.value).toBe('UP')
    stop()
  })

  it('pauses while the page is hidden and checks right away when it shows again', async () => {
    fetchHealthStatusMock.mockResolvedValue('UP')
    const { stop } = startMonitoring()
    await vi.advanceTimersByTimeAsync(0)

    setPageHidden(true)
    await vi.advanceTimersByTimeAsync(60_000)

    expect(fetchHealthStatusMock).toHaveBeenCalledOnce()

    setPageHidden(false)
    await vi.advanceTimersByTimeAsync(0)

    expect(fetchHealthStatusMock).toHaveBeenCalledTimes(2)
    stop()
  })

  it.each(['focus', 'online', 'offline'])(
    'checks right away when the window gets a %s event',
    async (type) => {
      fetchHealthStatusMock.mockResolvedValue('UP')
      const { stop } = startMonitoring()
      await vi.advanceTimersByTimeAsync(0)

      window.dispatchEvent(new Event(type))
      await vi.advanceTimersByTimeAsync(0)

      expect(fetchHealthStatusMock).toHaveBeenCalledTimes(2)
      stop()
    },
  )

  it('checks right away when asked to, dropping the answer of the check it replaces', async () => {
    let answerFirstCheck: (status: 'UP') => void = () => undefined
    fetchHealthStatusMock
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            answerFirstCheck = resolve
          }),
      )
      .mockResolvedValueOnce('DOWN')
    const { health, refresh, stop } = startMonitoring()
    const firstSignal = fetchHealthStatusMock.mock.lastCall?.[0]

    refresh()
    await vi.advanceTimersByTimeAsync(0)
    answerFirstCheck('UP')
    await vi.advanceTimersByTimeAsync(0)

    expect(firstSignal?.aborted).toBe(true)
    expect(health.value).toBe('DOWN')
    stop()
  })

  it('stops checking when its scope is disposed', async () => {
    fetchHealthStatusMock.mockImplementation(rejectOnAbort)
    const { health, stop } = startMonitoring()
    const signal = fetchHealthStatusMock.mock.lastCall?.[0]

    stop()
    window.dispatchEvent(new Event('focus'))
    await vi.advanceTimersByTimeAsync(60_000)

    expect(signal?.aborted).toBe(true)
    expect(fetchHealthStatusMock).toHaveBeenCalledOnce()
    expect(health.value).toBe('PENDING')
  })
})

describe('injectBackendHealth', () => {
  /** mounts a component that injects the backend health and returns what it got */
  function injectInComponent(provide: Record<symbol, unknown>) {
    let injected: BackendHealthState | undefined
    mount(
      defineComponent({
        setup() {
          injected = injectBackendHealth()
          return () => null
        },
      }),
      { global: { provide } },
    )
    return injected
  }

  it('returns the health of the backend that the app provides', () => {
    const backend = fakeBackendHealth()

    expect(injectInComponent(backend.provide)?.health.value).toBe('UP')
  })

  it('fails without an app that provides it', () => {
    vi.spyOn(console, 'warn').mockImplementation(() => undefined)

    expect(() => injectInComponent({})).toThrow('The health of the backend is missing')
  })
})
