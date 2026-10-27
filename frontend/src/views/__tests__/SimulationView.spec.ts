import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { Vector3 } from 'three'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { fakeBackendHealth } from '@/__tests__/fakeBackendHealth'
import { stubServer } from '@/__tests__/fakeServer'
import { FakeWebSocket } from '@/__tests__/FakeWebSocket'
import { encodeFrame, encodeScene } from '@/__tests__/messages'
import {
  CLOTH_FRAME,
  CLOTH_XRAY,
  clothScene,
  pointsScene,
  SELECTABLE_FRAME,
  selectableScene,
} from '@/__tests__/scenes'
import { galaxies } from '@/__tests__/simulations'
import SimulationPage from '@/components/SimulationPage.vue'
import { SceneRenderer } from '@/rendering/SceneRenderer'
import type { PointerListener } from '@/rendering/Stage'

import SimulationView from '../SimulationView.vue'

vi.mock('@/rendering/SceneRenderer')

enableAutoUnmount(afterEach)

async function mountView(type: string) {
  const wrapper = mount(SimulationView, {
    props: { type },
    global: { plugins: [createPinia()], provide: fakeBackendHealth().provide },
  })
  await flushPromises()
  return wrapper
}

/** renderer the view created, with the listener it passed to it */
function createdRenderer(): { renderer: SceneRenderer; listener: PointerListener } {
  const renderer = vi.mocked(SceneRenderer).mock.instances[0]
  const listener = vi.mocked(SceneRenderer).mock.calls[0]?.[1]
  if (renderer === undefined || listener === undefined) {
    throw new Error('The view created no renderer')
  }
  return { renderer, listener }
}

describe('SimulationView', () => {
  beforeEach(() => {
    FakeWebSocket.reset()
    vi.stubGlobal('WebSocket', FakeWebSocket)
  })

  it('opens a room for its simulation with the default parameters and joins it', async () => {
    const fetchMock = stubServer()

    const wrapper = await mountView('lorenz')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/rooms',
      expect.objectContaining({
        body: '{"type":"lorenz","parameters":{"sigma":10,"rho":28,"order":4}}',
      }),
    )
    expect(FakeWebSocket.latest().url).toMatch(/\/ws\/rooms\/room-1$/)
    expect(wrapper.get('h1').text()).toBe('Lorenz attractor')
  })

  it('shows the scene and the frames of the room, with the unit of the time', async () => {
    stubServer()
    const wrapper = await mountView('cloth')
    const socket = FakeWebSocket.latest()
    const { renderer } = createdRenderer()

    socket.acceptConnection()
    socket.receive(encodeScene(clothScene))
    socket.receive(encodeFrame(45, 1.5, CLOTH_FRAME))
    await flushPromises()

    expect(renderer.show).toHaveBeenCalledExactlyOnceWith(clothScene)
    expect(renderer.update).toHaveBeenCalledExactlyOnceWith(Float32Array.from(CLOTH_FRAME))
    expect(wrapper.get('.time').text()).toBe('t = 1.5\u00a0s')
    expect(wrapper.get('.hint').text()).toContain('Drag the surface to pull it')
  })

  it('shows the time without unit for a simulation whose time has none', async () => {
    stubServer()
    const wrapper = await mountView('lorenz')
    const socket = FakeWebSocket.latest()

    socket.receive(encodeScene(pointsScene))
    socket.receive(encodeFrame(45, 1.5, [1, 2, 3, 4, 5, 6]))
    await flushPromises()

    expect(wrapper.get('.time').text()).toBe('t = 1.5')
    expect(wrapper.get('.hint').text()).toBe('Drag to turn the view. The mouse wheel zooms.')
  })

  it('keeps the controls disabled until the connection is open', async () => {
    stubServer()
    const wrapper = await mountView('lorenz')

    expect(wrapper.get<HTMLInputElement>('input').element.disabled).toBe(true)

    FakeWebSocket.latest().acceptConnection()
    await flushPromises()

    expect(wrapper.get<HTMLInputElement>('input').element.disabled).toBe(false)
  })

  it('sends changed parameters to the room', async () => {
    stubServer()
    const wrapper = await mountView('lorenz')
    const socket = FakeWebSocket.latest()
    socket.acceptConnection()
    await flushPromises()

    await wrapper.get('input[min="0"]').setValue('20')

    expect(socket.sent).toEqual(['{"type":"param","name":"rho","value":20}'])
  })

  it('sends the chosen numerical method to the room', async () => {
    stubServer()
    const wrapper = await mountView('lorenz')
    const socket = FakeWebSocket.latest()
    socket.acceptConnection()
    await flushPromises()

    await wrapper.get('input[type="radio"][value="1"]').setValue(true)

    expect(socket.sent).toEqual(['{"type":"param","name":"order","value":1}'])
  })

  it('resets the parameters to their defaults', async () => {
    stubServer()
    const wrapper = await mountView('lorenz')
    const socket = FakeWebSocket.latest()
    socket.acceptConnection()
    await flushPromises()

    await wrapper.get('.actions button:first-of-type').trigger('click')

    expect(socket.sent).toEqual([
      '{"type":"param","name":"sigma","value":10}',
      '{"type":"param","name":"rho","value":28}',
      '{"type":"param","name":"order","value":4}',
    ])
  })

  it('runs the experiments of the lesson in the room', async () => {
    stubServer()
    const wrapper = await mountView('lorenz')
    const socket = FakeWebSocket.latest()
    socket.acceptConnection()
    await flushPromises()

    wrapper.getComponent(SimulationPage).vm.$emit('experiment', { rho: 0.5 })

    expect(socket.sent).toEqual([
      '{"type":"param","name":"sigma","value":10}',
      '{"type":"param","name":"rho","value":0.5}',
      '{"type":"param","name":"order","value":4}',
    ])
  })

  it('sends what the pointer does with the scene to the room', async () => {
    stubServer()
    await mountView('cloth')
    const socket = FakeWebSocket.latest()
    socket.acceptConnection()
    const { listener } = createdRenderer()

    listener.grab(7, new Vector3(0.5, 2, 0.25))
    listener.drag(7, new Vector3(1, 2.5, 0))
    listener.release(7)

    expect(socket.sent).toEqual([
      '{"type":"grab","vertex":7,"x":0.5,"y":2,"z":0.25}',
      '{"type":"drag","vertex":7,"x":1,"y":2.5,"z":0}',
      '{"type":"release","vertex":7}',
    ])
  })

  it('sends the points that the pointer selects to the room', async () => {
    stubServer()
    await mountView('lorenz')
    const socket = FakeWebSocket.latest()
    socket.acceptConnection()
    const { listener } = createdRenderer()

    listener.select(42)
    listener.deselect()

    expect(socket.sent).toEqual(['{"type":"select","point":42}', '{"type":"deselect"}'])
  })

  it('lists the readouts of the frames, and tells that points can be selected', async () => {
    stubServer()
    const wrapper = await mountView('lorenz')
    const socket = FakeWebSocket.latest()

    socket.acceptConnection()
    socket.receive(encodeScene(selectableScene))
    socket.receive(encodeFrame(1, 0.5, SELECTABLE_FRAME))
    await flushPromises()

    expect(
      wrapper
        .findAll('.readout')
        .map((readout) => [readout.get('dt').text(), readout.get('dd').text()]),
    ).toEqual([
      ['x', '1.50'],
      ['ẋ xDot', '−21.4'],
    ])
    expect(wrapper.get('.hint').text()).toContain('Click a point to read its values')
  })

  it('shows the x-ray of the room on request, and explains it', async () => {
    stubServer()
    const wrapper = await mountView('cloth')
    const socket = FakeWebSocket.latest()
    const { renderer } = createdRenderer()
    socket.acceptConnection()
    socket.receive(encodeScene(clothScene))
    await flushPromises()

    await wrapper.get('.xray-controls input').setValue(true)
    socket.receive(encodeFrame(46, 1.5, [...CLOTH_FRAME, ...CLOTH_XRAY]))
    await flushPromises()

    expect(socket.sent).toEqual(['{"type":"xray","shown":true}'])
    expect(renderer.update).toHaveBeenLastCalledWith(
      Float32Array.from([...CLOTH_FRAME, ...CLOTH_XRAY]),
    )
    expect(wrapper.get('[aria-label="What the x-ray shows"]').text()).toContain(
      'Strain of the edges',
    )
  })

  it('offers no x-ray for a scene without one', async () => {
    stubServer()
    const wrapper = await mountView('lorenz')
    FakeWebSocket.latest().acceptConnection()
    FakeWebSocket.latest().receive(encodeScene(pointsScene))
    await flushPromises()

    expect(wrapper.find('.xray-controls').exists()).toBe(false)
  })

  it('pauses the room, steps through it and slows it down', async () => {
    stubServer()
    const wrapper = await mountView('lorenz')
    const socket = FakeWebSocket.latest()
    socket.acceptConnection()
    await flushPromises()
    const clock = wrapper.get('[role="group"][aria-label="Time"]')

    await clock.get('button:first-of-type').trigger('click')
    await clock.get('button:last-of-type').trigger('click')
    await clock.get('select').setValue('0.25')
    await clock.get('button:first-of-type').trigger('click')

    expect(socket.sent).toEqual([
      '{"type":"pause"}',
      '{"type":"step"}',
      '{"type":"speed","value":0.25}',
      '{"type":"play"}',
    ])
  })

  it('starts over in a new room with the current parameters, and shows its scene', async () => {
    const fetchMock = stubServer()
    const wrapper = await mountView('lorenz')
    const { renderer } = createdRenderer()
    FakeWebSocket.latest().acceptConnection()
    FakeWebSocket.latest().receive(encodeScene(pointsScene))
    await flushPromises()
    await wrapper.get('input[min="0"]').setValue('20')

    await wrapper.get('.actions button:last-of-type').trigger('click')
    await flushPromises()
    FakeWebSocket.latest().receive(encodeScene(pointsScene))
    await flushPromises()

    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/rooms',
      expect.objectContaining({
        body: '{"type":"lorenz","parameters":{"sigma":10,"rho":20,"order":4}}',
      }),
    )
    expect(FakeWebSocket.latest().url).toMatch(/\/ws\/rooms\/room-2$/)
    expect(FakeWebSocket.instances[0]?.readyState).toBe(FakeWebSocket.CLOSED)
    expect(renderer.show).toHaveBeenCalledTimes(2)
  })

  it('starts over with parameters that apply on restart, which it keeps from the room', async () => {
    const fetchMock = stubServer([galaxies])
    const wrapper = await mountView('galaxies')
    const socket = FakeWebSocket.latest()
    socket.acceptConnection()
    await flushPromises()

    await wrapper.get('section input').setValue('0.5')
    await wrapper.get('button.pending').trigger('click')
    await flushPromises()

    expect(socket.sent).toEqual([])
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/rooms',
      expect.objectContaining({
        body: '{"type":"galaxies","parameters":{"pace":15,"ratio":0.5,"inclination":60}}',
      }),
    )
    expect(FakeWebSocket.latest().url).toMatch(/\/ws\/rooms\/room-2$/)
    expect(wrapper.find('button.pending').exists()).toBe(false)
  })

  it('reports a simulation that the server does not offer', async () => {
    stubServer()

    const wrapper = await mountView('quantum')

    expect(wrapper.get('[role="alert"]').text()).toBe(
      'The server does not offer the quantum simulation.',
    )
  })

  it('reports a simulation that cannot be started', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>().mockResolvedValue(new Response('Service Unavailable', { status: 503 })),
    )

    const wrapper = await mountView('lorenz')

    expect(wrapper.get('[role="alert"]').text()).toBe('Request failed with HTTP status 503')
  })

  it('explains that the browser cannot display the simulation without WebGL', async () => {
    stubServer()
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    vi.mocked(SceneRenderer).mockImplementationOnce(function () {
      throw new Error('WebGL is not available')
    })

    const wrapper = await mountView('lorenz')

    expect(wrapper.get('.notice').text()).toBe('This browser cannot display 3D graphics.')
  })

  it('disposes the renderer when it is unmounted', async () => {
    stubServer()
    const wrapper = await mountView('lorenz')

    wrapper.unmount()

    expect(createdRenderer().renderer.dispose).toHaveBeenCalledOnce()
  })
})
