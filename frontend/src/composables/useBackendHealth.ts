import { inject, onScopeDispose, readonly, ref, type InjectionKey, type Ref } from 'vue'

import { fetchHealthStatus, type HealthStatus } from '@/api/actuator'

/** health of the backend as the browser sees it */
export type BackendHealth = HealthStatus | 'PENDING' | 'UNREACHABLE'

export interface BackendHealthOptions {
  /** ms between two checks while the backend is up */
  interval?: number
  /** ms between two checks while it's not, so its return shows soon */
  retryInterval?: number
  /** ms after which a check is aborted and the backend counts as unreachable */
  timeout?: number
}

export interface BackendHealthState {
  /** result of the latest check, `PENDING` until the first one completes */
  readonly health: Readonly<Ref<BackendHealth>>
  /** checks right away, e.g. because a connection to the backend broke */
  readonly refresh: () => void
}

/** the health of the backend, which the app provides to every component */
export const backendHealthKey: InjectionKey<BackendHealthState> = Symbol('backend health')

/**
 * The health of the backend that the app provides
 *
 * @throws if no component above the current one provides it
 */
export function injectBackendHealth(): BackendHealthState {
  const backend = inject(backendHealthKey)
  if (backend === undefined) {
    throw new Error('The health of the backend is missing, which the app provides')
  }
  return backend
}

/**
 * Keeps checking the health of the backend until the current effect scope is disposed, more often
 * while the backend isn't up.
 *
 * Browsers throttle timers in hidden pages, so checks pause while the page is hidden. They resume
 * right away once the page shows again, the window gets the focus or the network changes. A new
 * check aborts the one still running, so results never arrive out of order
 */
export function useBackendHealth({
  interval = 3_000,
  retryInterval = 1_000,
  timeout = 2_000,
}: BackendHealthOptions = {}): BackendHealthState {
  const health = ref<BackendHealth>('PENDING')
  let running: AbortController | undefined
  let nextCheck: ReturnType<typeof setTimeout> | undefined

  function refresh(): void {
    clearTimeout(nextCheck)
    running?.abort()
    const check = new AbortController()
    running = check
    void fetchHealthStatus(AbortSignal.any([check.signal, AbortSignal.timeout(timeout)]))
      .catch(() => 'UNREACHABLE' as const)
      .then((status) => {
        if (running === check) {
          running = undefined
          health.value = status
          scheduleNextCheck()
        }
      })
  }

  function scheduleNextCheck(): void {
    if (!document.hidden) {
      nextCheck = setTimeout(refresh, health.value === 'UP' ? interval : retryInterval)
    }
  }

  function onVisibilityChange(): void {
    if (document.hidden) {
      clearTimeout(nextCheck)
    } else {
      refresh()
    }
  }

  window.addEventListener('focus', refresh)
  window.addEventListener('online', refresh)
  window.addEventListener('offline', refresh)
  document.addEventListener('visibilitychange', onVisibilityChange)

  onScopeDispose(() => {
    window.removeEventListener('focus', refresh)
    window.removeEventListener('online', refresh)
    window.removeEventListener('offline', refresh)
    document.removeEventListener('visibilitychange', onVisibilityChange)
    clearTimeout(nextCheck)
    running?.abort()
    running = undefined
  })

  refresh()

  return { health: readonly(health), refresh }
}
