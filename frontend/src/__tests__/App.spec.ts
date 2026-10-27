import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from '../App.vue'
import router from '../router'
import { stubServer } from './fakeServer'
import { FakeWebSocket } from './FakeWebSocket'

vi.mock('@/rendering/SceneRenderer')

enableAutoUnmount(afterEach)

async function mountApp() {
  const wrapper = mount(App, { global: { plugins: [createPinia(), router] } })
  await flushPromises()
  return wrapper
}

describe('App', () => {
  beforeEach(async () => {
    FakeWebSocket.reset()
    vi.stubGlobal('WebSocket', FakeWebSocket)
    await router.push('/')
  })

  it('shows the health of the backend', async () => {
    stubServer()

    const wrapper = await mountApp()

    expect(wrapper.get('[role="status"]').text()).toBe('Backend UP')
  })

  it('renders the start page', async () => {
    stubServer()

    const wrapper = await mountApp()

    expect(wrapper.get('h1').text()).toBe('Live physics simulations')
  })

  it('starts the page of a simulation over when the address changes to another one', async () => {
    const fetchMock = stubServer()
    const wrapper = await mountApp()

    await router.push('/simulations/lorenz')
    await flushPromises()
    await router.push('/simulations/cloth')
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('Cloth')
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/rooms',
      expect.objectContaining({ body: '{"type":"cloth","parameters":{"wind":3,"gravity":9.81}}' }),
    )
    expect(FakeWebSocket.instances.map(({ readyState }) => readyState)).toEqual([
      FakeWebSocket.CLOSED,
      FakeWebSocket.CONNECTING,
    ])
  })
})
