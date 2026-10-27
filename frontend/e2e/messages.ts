import { Buffer } from 'node:buffer'

/** encodes a frame like the server does, see `src/api/protocol.ts` */
export function encodeFrame(tick: number, time: number, floats: readonly number[]): Buffer {
  const message = encodeHeader(1, floats.length * 4, tick, time)
  floats.forEach((float, index) => message.writeFloatLE(float, 16 + index * 4))
  return message
}

/** encodes a scene like the server does, see `src/api/protocol.ts` */
export function encodeScene(scene: object): Buffer {
  const json = Buffer.from(JSON.stringify(scene), 'utf8')
  const message = encodeHeader(2, json.length, 0, 0)
  json.copy(message, 16)
  return message
}

function encodeHeader(
  messageType: number,
  payloadLength: number,
  tick: number,
  time: number,
): Buffer {
  const message = Buffer.alloc(16 + payloadLength)
  message.writeUInt8(messageType, 0)
  message.writeUInt32LE(tick, 4)
  message.writeFloatLE(time, 8)
  message.writeUInt32LE(payloadLength, 12)
  return message
}
