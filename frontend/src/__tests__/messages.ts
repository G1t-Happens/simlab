import { HEADER_SIZE, MessageType } from '@/api/protocol'
import type { Scene } from '@/api/scene'

/** encodes a frame like the server does */
export function encodeFrame(tick: number, time: number, floats: readonly number[]): ArrayBuffer {
  return encodeMessage(
    MessageType.FRAME,
    new Uint8Array(Float32Array.from(floats).buffer),
    tick,
    time,
  )
}

/** encodes a scene like the server does */
export function encodeScene(scene: Scene): ArrayBuffer {
  return encodeMessage(MessageType.SCENE, new TextEncoder().encode(JSON.stringify(scene)))
}

/** encodes a message with any payload, e.g. one that a valid message wouldn't hold */
export function encodeMessage(
  messageType: number,
  payload: Uint8Array,
  tick = 0,
  time = 0,
): ArrayBuffer {
  const message = new Uint8Array(HEADER_SIZE + payload.length)
  const header = new DataView(message.buffer, 0, HEADER_SIZE)
  header.setUint8(0, messageType)
  header.setUint32(4, tick, true)
  header.setFloat32(8, time, true)
  header.setUint32(12, payload.length, true)
  message.set(payload, HEADER_SIZE)
  return message.buffer
}
