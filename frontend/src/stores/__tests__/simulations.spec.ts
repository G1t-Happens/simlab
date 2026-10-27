import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { lorenz } from '@/__tests__/simulations'
import { fetchSimulations } from '@/api/simulations'

import { useSimulationsStore } from '../simulations'

vi.mock('@/api/simulations')

const fetchSimulationsMock = vi.mocked(fetchSimulations)

describe('useSimulationsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('fetches the simulations only once', async () => {
    fetchSimulationsMock.mockResolvedValue([lorenz])
    const store = useSimulationsStore()

    await expect(store.load()).resolves.toEqual([lorenz])
    await expect(store.load()).resolves.toEqual([lorenz])

    expect(fetchSimulationsMock).toHaveBeenCalledOnce()
    expect(store.simulations).toEqual([lorenz])
  })

  it('shares a fetch that is under way', async () => {
    fetchSimulationsMock.mockResolvedValue([lorenz])
    const store = useSimulationsStore()

    await Promise.all([store.load(), store.load()])

    expect(fetchSimulationsMock).toHaveBeenCalledOnce()
  })

  it('fetches again after a failure', async () => {
    fetchSimulationsMock
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce([lorenz])
    const store = useSimulationsStore()

    await expect(store.load()).rejects.toThrow('Failed to fetch')
    await expect(store.load()).resolves.toEqual([lorenz])
  })
})
