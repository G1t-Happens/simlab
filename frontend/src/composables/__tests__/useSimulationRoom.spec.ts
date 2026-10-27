import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { defineComponent } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { fakeBackendHealth } from '@/__tests__/fakeBackendHealth'
import { stubServer } from '@/__tests__/fakeServer'
import { FakeWebSocket } from '@/__tests__/FakeWebSocket'
import { cloth, galaxies } from '@/__tests__/simulations'

import { useSimulationRoom, type SimulationRoom } from '../useSimulationRoom'

enableAutoUnmount(afterEach)

/** runs the composable in a component like a view would, with a backend health the test controls */
function startRoom(type = 'cloth') {
  const backend = fakeBackendHealth()
  let room: SimulationRoom | undefined
  mount(
    defineComponent({
      setup() {
        room = useSimulationRoom(type)
        return () => null
      },
    }),
    { global: { plugins: [createPinia()], provide: backend.provide } },
  )
  if (room === undefined) {
    throw new Error('The component did not set up')
  }
  return { room, backend }
}

describe('useSimulationRoom', () => {
  beforeEach(() => {
    FakeWebSocket.reset()
    vi.stubGlobal('WebSocket', FakeWebSocket)
  })

  it('describes the simulation and joins a room with the default parameters', async () => {
    stubServer()
    const { room } = startRoom()

    await room.start()

    expect(room.simulation.value).toEqual(cloth)
    expect(room.parameters.value).toEqual({ wind: 3, gravity: 9.81 })
    expect(room.roomId.value).toBe('room-1')
    expect(room.failure.value).toBeUndefined()
    expect(FakeWebSocket.latest().url).toMatch(/\/ws\/rooms\/room-1$/)
  })

  it('changes parameters and resets them to their defaults', async () => {
    stubServer()
    const { room } = startRoom()
    await room.start()
    const socket = FakeWebSocket.latest()
    socket.acceptConnection()

    room.changeParameter('wind', 5)
    expect(room.parameters.value).toEqual({ wind: 5, gravity: 9.81 })

    room.resetParameters()
    expect(room.parameters.value).toEqual({ wind: 3, gravity: 9.81 })
    expect(socket.sent).toEqual([
      '{"type":"param","name":"wind","value":5}',
      '{"type":"param","name":"wind","value":3}',
      '{"type":"param","name":"gravity","value":9.81}',
    ])
  })

  it('keeps parameters that apply on restart for the next room to start with', async () => {
    const fetchMock = stubServer([galaxies])
    const { room } = startRoom('galaxies')
    await room.start()
    const socket = FakeWebSocket.latest()
    socket.acceptConnection()

    room.changeParameter('ratio', 0.5)

    expect(room.parameters.value).toEqual({ pace: 15, ratio: 0.5, inclination: 60 })
    expect(socket.sent).toEqual([])
    expect(room.restartPending.value).toBe(true)

    await room.start()

    expect(room.roomId.value).toBe('room-2')
    expect(room.restartPending.value).toBe(false)
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/rooms',
      expect.objectContaining({
        body: '{"type":"galaxies","parameters":{"pace":15,"ratio":0.5,"inclination":60}}',
      }),
    )
  })

  it('runs an experiment with its values and the defaults of the other parameters', async () => {
    stubServer()
    const { room } = startRoom()
    await room.start()
    const socket = FakeWebSocket.latest()
    socket.acceptConnection()
    room.changeParameter('gravity', 1.62)

    room.runExperiment({ wind: 6 })

    expect(room.parameters.value).toEqual({ wind: 6, gravity: 9.81 })
    expect(room.roomId.value).toBe('room-1')
    expect(socket.sent.slice(1)).toEqual([
      '{"type":"param","name":"wind","value":6}',
      '{"type":"param","name":"gravity","value":9.81}',
    ])
  })

  it('starts over only for an experiment that changes how the simulation starts', async () => {
    const fetchMock = stubServer([galaxies])
    const { room } = startRoom('galaxies')
    await room.start()
    FakeWebSocket.latest().acceptConnection()

    room.runExperiment({ pace: 5 })
    await flushPromises()

    expect(room.roomId.value).toBe('room-1')

    room.runExperiment({ inclination: 180 })
    await flushPromises()

    expect(room.roomId.value).toBe('room-2')
    expect(room.restartPending.value).toBe(false)
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/rooms',
      expect.objectContaining({
        body: '{"type":"galaxies","parameters":{"pace":15,"ratio":1,"inclination":180}}',
      }),
    )
  })

  it('pauses, steps and plays the simulation in its room, and slows it down', async () => {
    stubServer()
    const { room } = startRoom()
    await room.start()
    const socket = FakeWebSocket.latest()
    socket.acceptConnection()

    room.pause()
    room.step()
    room.changeSpeed(0.25)

    expect(room.paused.value).toBe(true)
    expect(room.speed.value).toBe(0.25)

    room.play()

    expect(room.paused.value).toBe(false)
    expect(socket.sent).toEqual([
      '{"type":"pause"}',
      '{"type":"step"}',
      '{"type":"speed","value":0.25}',
      '{"type":"play"}',
    ])
  })

  it('keeps the clock as it is while the room is not connected', async () => {
    stubServer()
    const { room } = startRoom()
    await room.start()

    room.pause()
    room.changeSpeed(0.5)

    expect(room.paused.value).toBe(false)
    expect(room.speed.value).toBe(1)
  })

  it('plays every new room in real time', async () => {
    stubServer()
    const { room } = startRoom()
    await room.start()
    FakeWebSocket.latest().acceptConnection()
    room.pause()
    room.changeSpeed(0.125)

    await room.start()

    expect(room.roomId.value).toBe('room-2')
    expect(room.paused.value).toBe(false)
    expect(room.speed.value).toBe(1)
  })

  it('shows the x-ray of the simulation in its room, and hides it again', async () => {
    stubServer()
    const { room } = startRoom()
    await room.start()
    const socket = FakeWebSocket.latest()
    socket.acceptConnection()

    room.showXray(true)

    expect(room.xrayShown.value).toBe(true)

    room.showXray(false)

    expect(room.xrayShown.value).toBe(false)
    expect(socket.sent).toEqual(['{"type":"xray","shown":true}', '{"type":"xray","shown":false}'])
  })

  it('shows no x-ray while the room is not connected', async () => {
    stubServer()
    const { room } = startRoom()
    await room.start()

    room.showXray(true)

    expect(room.xrayShown.value).toBe(false)
  })

  it('shows no x-ray in a new room', async () => {
    stubServer()
    const { room } = startRoom()
    await room.start()
    FakeWebSocket.latest().acceptConnection()
    room.showXray(true)

    await room.start()

    expect(room.xrayShown.value).toBe(false)
  })

  it('urges no restart while no room is open, or once parameters are back', async () => {
    stubServer([galaxies])
    const { room } = startRoom('galaxies')

    expect(room.restartPending.value).toBe(false)

    await room.start()
    room.changeParameter('inclination', 90)
    room.changeParameter('inclination', 60)

    expect(room.restartPending.value).toBe(false)
  })

  it('reports a simulation that the server does not offer', async () => {
    stubServer([])
    const { room } = startRoom()

    await room.start()

    expect(room.failure.value).toBe('The server does not offer the cloth simulation.')
    expect(room.roomId.value).toBeUndefined()
  })

  it('starts over in a new room once the backend that took the room along is back', async () => {
    stubServer()
    const { room, backend } = startRoom()
    await room.start()
    FakeWebSocket.latest().acceptConnection()

    FakeWebSocket.latest().closeFromServer(1006)
    await flushPromises()

    expect(room.status.value).toBe('lost')
    expect(backend.refresh).toHaveBeenCalledOnce()

    backend.health.value = 'UNREACHABLE'
    await flushPromises()
    backend.health.value = 'UP'
    await flushPromises()

    expect(room.roomId.value).toBe('room-2')
    expect(FakeWebSocket.latest().url).toMatch(/\/ws\/rooms\/room-2$/)
  })

  it('starts once the backend is back, after it could not be reached', async () => {
    const fetchMock = stubServer()
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    const { room, backend } = startRoom()
    await room.start()

    expect(room.failure.value).toBe('Failed to fetch')

    backend.health.value = 'UNREACHABLE'
    await flushPromises()
    backend.health.value = 'UP'
    await flushPromises()

    expect(room.failure.value).toBeUndefined()
    expect(room.roomId.value).toBe('room-1')
  })

  it('leaves a room that ended with an error alone, however the backend fares', async () => {
    stubServer()
    const { room, backend } = startRoom()
    await room.start()
    FakeWebSocket.latest().acceptConnection()

    FakeWebSocket.latest().closeFromServer(1011)
    backend.health.value = 'UNREACHABLE'
    await flushPromises()
    backend.health.value = 'UP'
    await flushPromises()

    expect(room.status.value).toBe('closed')
    expect(backend.refresh).not.toHaveBeenCalled()
    expect(FakeWebSocket.instances).toHaveLength(1)
  })
})
