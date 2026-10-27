import { readonly, ref } from 'vue'
import { vi } from 'vitest'

import {
  backendHealthKey,
  type BackendHealth,
  type BackendHealthState,
} from '@/composables/useBackendHealth'

/** stands in for the backend health the app provides, tests set it as they need */
export function fakeBackendHealth(initial: BackendHealth = 'UP') {
  const health = ref<BackendHealth>(initial)
  const refresh = vi.fn<() => void>()
  const state: BackendHealthState = { health: readonly(health), refresh }
  return {
    health,
    refresh,
    /** options for `mount` that provide the fake to the mounted components */
    provide: { [backendHealthKey as symbol]: state },
  }
}
