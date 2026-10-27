import { defineStore } from 'pinia'
import { shallowRef } from 'vue'

import { fetchSimulations, type SimulationInfo } from '@/api/simulations'

/** simulations the server offers, fetched once and then kept */
export const useSimulationsStore = defineStore('simulations', () => {
  const simulations = shallowRef<readonly SimulationInfo[]>()
  let pending: Promise<readonly SimulationInfo[]> | undefined

  /** fetches the simulations unless they're known already. A failed fetch can be retried */
  async function load(): Promise<readonly SimulationInfo[]> {
    if (simulations.value === undefined) {
      pending ??= fetchSimulations().finally(() => {
        pending = undefined
      })
      simulations.value = await pending
    }
    return simulations.value
  }

  return { simulations, load }
})
