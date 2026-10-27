import {
  onScopeDispose,
  readonly,
  ref,
  shallowReadonly,
  shallowRef,
  toValue,
  watch,
  type MaybeRefOrGetter,
  type Ref,
  type ShallowRef,
} from 'vue'

import { decodeMessage, ProtocolError, type ClientMessage, type Frame } from '@/api/protocol'
import { sceneFrameFloats, sceneXrayFloats, type Scene } from '@/api/scene'

/**
 * State of the connection to a room. `lost` if the server went away (which took the room with it),
 * `closed` if the browser closed it or the room ended it
 */
export type StreamStatus = 'connecting' | 'open' | 'closed' | 'lost'

/** close codes of a server going away: shutting down, or gone without closing connections */
const LOST_CONNECTION_CODES: readonly number[] = [1001, 1006]

export interface SimulationStream {
  readonly status: Readonly<Ref<StreamStatus>>
  /** what the simulation shows, `undefined` until the room describes it */
  readonly scene: Readonly<ShallowRef<Scene | undefined>>
  /** latest frame, laid out as the scene describes. `undefined` until the first one arrives */
  readonly frame: Readonly<ShallowRef<Frame | undefined>>
  /**
   * Sends a message to the room
   *
   * @returns whether the message was sent, which it isn't unless the connection is open
   */
  readonly send: (message: ClientMessage) => boolean
}

/**
 * Joins a room through its WebSocket and receives its messages until the current effect scope is
 * disposed. When the room changes the stream leaves the old room and joins the new one. A message
 * that doesn't follow the protocol (like a frame that doesn't fit the scene) closes the connection
 */
export function useSimulationStream(
  roomId: MaybeRefOrGetter<string | undefined>,
): SimulationStream {
  const status = ref<StreamStatus>('closed')
  const scene = shallowRef<Scene>()
  const frame = shallowRef<Frame>()
  let socket: WebSocket | undefined

  function join(id: string): void {
    const joined = new WebSocket(roomUrl(id))
    joined.binaryType = 'arraybuffer'
    joined.addEventListener('open', () => {
      if (socket === joined) {
        status.value = 'open'
      }
    })
    joined.addEventListener('message', ({ data }: MessageEvent<unknown>) => {
      if (socket === joined && data instanceof ArrayBuffer) {
        receive(joined, data)
      }
    })
    joined.addEventListener('close', ({ code }: CloseEvent) => {
      if (socket === joined) {
        socket = undefined
        status.value = LOST_CONNECTION_CODES.includes(code) ? 'lost' : 'closed'
      }
    })
    socket = joined
    status.value = 'connecting'
  }

  function receive(from: WebSocket, data: ArrayBuffer): void {
    try {
      const message = decodeMessage(data)
      if (message.type === 'scene') {
        scene.value = message.scene
      } else {
        checkFits(message, scene.value)
        frame.value = message
      }
    } catch (error) {
      console.error('Leaving the room after an invalid message', error)
      from.close()
    }
  }

  function leave(): void {
    socket?.close()
    socket = undefined
    status.value = 'closed'
    scene.value = undefined
    frame.value = undefined
  }

  function send(message: ClientMessage): boolean {
    if (socket?.readyState !== WebSocket.OPEN) {
      return false
    }
    socket.send(JSON.stringify(message))
    return true
  }

  watch(
    () => toValue(roomId),
    (id) => {
      leave()
      if (id !== undefined) {
        join(id)
      }
    },
    { immediate: true },
  )
  onScopeDispose(leave)

  return {
    status: readonly(status),
    scene: shallowReadonly(scene),
    frame: shallowReadonly(frame),
    send,
  }
}

/**
 * @throws ProtocolError if the frame arrived before the scene, or holds neither the floats of the
 *     scene objects nor those plus the ones of its x-ray
 */
function checkFits({ payload }: Frame, scene: Scene | undefined): void {
  if (scene === undefined) {
    throw new ProtocolError('A frame arrived before the scene')
  }
  const objects = sceneFrameFloats(scene)
  const xray = sceneXrayFloats(scene)
  if (payload.length !== objects && !(xray > 0 && payload.length === objects + xray)) {
    const withXray = xray > 0 ? `, or of ${String(objects + xray)} with its x-ray` : ''
    throw new ProtocolError(
      `A frame of ${String(payload.length)} floats does not fit a scene of ${String(objects)}` +
        withXray,
    )
  }
}

function roomUrl(id: string): string {
  const url = new URL(`/ws/rooms/${encodeURIComponent(id)}`, window.location.href)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return url.href
}
