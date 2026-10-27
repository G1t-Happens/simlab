import { effectScope, nextTick, ref, type Ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { FakeWebSocket } from '@/__tests__/FakeWebSocket'
import { encodeFrame, encodeScene } from '@/__tests__/messages'
import { CLOTH_FRAME, CLOTH_XRAY, clothScene, pointsScene } from '@/__tests__/scenes'

import { useSimulationStream } from '../useSimulationStream'

/** runs the composable in an effect scope of its own, like a component would */
function startStream(roomId: Ref<string | undefined>) {
  const scope = effectScope()
  const stream = scope.run(() => useSimulationStream(roomId))
  if (stream === undefined) {
    throw new Error('Effect scope is inactive')
  }
  return {
    ...stream,
    stop: () => {
      scope.stop()
    },
  }
}

describe('useSimulationStream', () => {
  beforeEach(() => {
    FakeWebSocket.reset()
    vi.stubGlobal('WebSocket', FakeWebSocket)
  })

  it('joins the room through its WebSocket', () => {
    const { status } = startStream(ref('room-1'))
    const socket = FakeWebSocket.latest()

    expect(socket.url).toBe('ws://localhost:3000/ws/rooms/room-1')
    expect(socket.binaryType).toBe('arraybuffer')
    expect(status.value).toBe('connecting')

    socket.acceptConnection()

    expect(status.value).toBe('open')
  })

  it('stays closed while there is no room', () => {
    const { status } = startStream(ref(undefined))

    expect(FakeWebSocket.instances).toHaveLength(0)
    expect(status.value).toBe('closed')
  })

  it('decodes the scene and the frames of the room', () => {
    const { scene, frame } = startStream(ref('room-1'))
    const socket = FakeWebSocket.latest()

    socket.receive(encodeScene(pointsScene))
    socket.receive(encodeFrame(3, 0.5, [1, 2, 3, 4, 5, 6]))

    expect(scene.value).toEqual(pointsScene)
    expect(frame.value).toEqual({
      type: 'frame',
      tick: 3,
      time: 0.5,
      payload: Float32Array.of(1, 2, 3, 4, 5, 6),
    })
  })

  it('decodes frames that show the x-ray of the scene after its objects', () => {
    const { frame } = startStream(ref('room-1'))
    const socket = FakeWebSocket.latest()

    socket.receive(encodeScene(clothScene))
    socket.receive(encodeFrame(3, 0.5, [...CLOTH_FRAME, ...CLOTH_XRAY]))

    expect(frame.value?.payload).toEqual(Float32Array.from([...CLOTH_FRAME, ...CLOTH_XRAY]))
  })

  it('sends messages as JSON once the connection is open', () => {
    const { send } = startStream(ref('room-1'))
    const socket = FakeWebSocket.latest()

    expect(send({ type: 'release', vertex: 7 })).toBe(false)

    socket.acceptConnection()

    expect(send({ type: 'release', vertex: 7 })).toBe(true)
    expect(socket.sent).toEqual(['{"type":"release","vertex":7}'])
  })

  it('leaves the old room when the room changes', async () => {
    const roomId = ref('room-1')
    const { scene, frame } = startStream(roomId)
    const oldSocket = FakeWebSocket.latest()
    oldSocket.receive(encodeScene(pointsScene))

    roomId.value = 'room-2'
    await nextTick()
    oldSocket.receive(encodeFrame(1, 0, [1, 2, 3, 4, 5, 6]))

    expect(oldSocket.readyState).toBe(FakeWebSocket.CLOSED)
    expect(FakeWebSocket.latest().url).toBe('ws://localhost:3000/ws/rooms/room-2')
    expect(scene.value).toBeUndefined()
    expect(frame.value).toBeUndefined()
  })

  it.each([1000, 1007, 1011])(
    'reports a connection that the room closes with %i as closed',
    (code) => {
      const { status } = startStream(ref('room-1'))
      const socket = FakeWebSocket.latest()
      socket.acceptConnection()

      socket.closeFromServer(code)

      expect(status.value).toBe('closed')
    },
  )

  it.each([1001, 1006])(
    'reports a connection that ends with %i as lost with the server',
    (code) => {
      const { status } = startStream(ref('room-1'))
      const socket = FakeWebSocket.latest()
      socket.acceptConnection()

      socket.closeFromServer(code)

      expect(status.value).toBe('lost')
    },
  )

  it.each([
    ['is cut off', [new ArrayBuffer(3)]],
    ['is a frame before the scene', [encodeFrame(1, 0, [1, 2, 3, 4, 5, 6])]],
    [
      'is a frame that does not fit the scene',
      [encodeScene(pointsScene), encodeFrame(1, 0, [1, 2, 3])],
    ],
    [
      'is a frame with part of the x-ray',
      [encodeScene(clothScene), encodeFrame(1, 0, [...CLOTH_FRAME, ...CLOTH_XRAY.slice(1)])],
    ],
  ])('leaves the room after a message that %s', (_, messages) => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    const { status, frame } = startStream(ref('room-1'))
    const socket = FakeWebSocket.latest()
    socket.acceptConnection()

    for (const message of messages) {
      socket.receive(message)
    }

    expect(socket.readyState).toBe(FakeWebSocket.CLOSED)
    expect(status.value).toBe('closed')
    expect(frame.value).toBeUndefined()
    expect(consoleError).toHaveBeenCalledOnce()
  })

  it('leaves the room when its scope is disposed', () => {
    const { status, stop } = startStream(ref('room-1'))
    const socket = FakeWebSocket.latest()
    socket.acceptConnection()

    stop()

    expect(socket.readyState).toBe(FakeWebSocket.CLOSED)
    expect(status.value).toBe('closed')
  })
})
