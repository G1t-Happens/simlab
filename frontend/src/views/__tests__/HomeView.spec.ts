import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { fakeBackendHealth } from '@/__tests__/fakeBackendHealth'
import { cloth, lorenz } from '@/__tests__/simulations'
import router from '@/router'

import HomeView from '../HomeView.vue'

enableAutoUnmount(afterEach)

function mountView(backend = fakeBackendHealth()) {
  return mount(HomeView, {
    global: { plugins: [createPinia(), router], provide: backend.provide },
  })
}

describe('HomeView', () => {
  it('lists the simulations that the server offers, with links to their pages', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(Response.json([cloth, lorenz])))

    const wrapper = mountView()
    await flushPromises()

    const cards = wrapper.findAll('a.card')
    expect(cards.map((card) => card.attributes('href'))).toEqual([
      '/simulations/cloth',
      '/simulations/lorenz',
    ])
    expect(cards[0]?.get('strong').text()).toBe('Cloth')
    expect(cards[0]?.get('span').text()).toBe('A cloth hangs from two corners.')
  })

  it('names the section of the simulations by its heading', () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockReturnValue(new Promise(() => undefined)))

    const section = mountView().get('section[aria-labelledby]')

    expect(section.attributes('aria-labelledby')).toBe(section.get('h2').attributes('id'))
    expect(section.get('h2').text()).toBe('Simulations')
  })

  it('shows that it loads the simulations', () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockReturnValue(new Promise(() => undefined)))

    expect(mountView().get('.loading').text()).toBe('Loading…')
  })

  it('reports a failure to load, and loads again once the backend is back', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn<typeof fetch>()
        .mockRejectedValueOnce(new TypeError('Failed to fetch'))
        .mockResolvedValueOnce(Response.json([lorenz])),
    )
    const backend = fakeBackendHealth('UNREACHABLE')

    const wrapper = mountView(backend)
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe(
      'The simulations could not be loaded: Failed to fetch',
    )

    backend.health.value = 'UP'
    await flushPromises()

    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.findAll('a.card')).toHaveLength(1)
  })
})
