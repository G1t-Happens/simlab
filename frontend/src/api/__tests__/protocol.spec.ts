import { describe, expect, it } from 'vitest'

import { encodeMessage, encodeScene } from '@/__tests__/messages'
import { clothScene } from '@/__tests__/scenes'

import {
  decodeMessage,
  HEADER_SIZE,
  MessageType,
  ProtocolError,
  type Frame,
  type ServerMessage,
} from '../protocol'

function bytes(hex: string): ArrayBuffer {
  return Uint8Array.from(hex.match(/../g) ?? [], (byte) => Number.parseInt(byte, 16)).buffer
}

function asFrame(message: ServerMessage): Frame {
  if (message.type !== 'frame') {
    throw new Error(`Expected a frame, but got a ${message.type}`)
  }
  return message
}

function sceneMessage(json: Uint8Array): ArrayBuffer {
  return encodeMessage(MessageType.SCENE, json)
}

const FRAME_TYPE = '01' + '000000'

describe('decodeMessage', () => {
  it('reads the little-endian header and the floats of a frame', () => {
    const tick = '07000000'
    const time = '0000c03f' // 1.5
    const payloadLength = '0c000000' // 12 bytes
    const floats = '0000803f' + '000000c0' + '0000003f' // 1.0, -2.0, 0.5

    const message = decodeMessage(bytes(FRAME_TYPE + tick + time + payloadLength + floats))

    expect(message).toEqual({
      type: 'frame',
      tick: 7,
      time: 1.5,
      payload: Float32Array.of(1, -2, 0.5),
    })
  })

  it('reads the tick as unsigned integer', () => {
    const message = decodeMessage(bytes(FRAME_TYPE + 'ffffffff' + '00000000' + '00000000'))

    expect(asFrame(message).tick).toBe(2 ** 32 - 1)
  })

  it('views the floats of a frame without copying them', () => {
    const data = bytes(FRAME_TYPE + '01000000' + '00000000' + '04000000' + '0000803f')

    const { payload } = asFrame(decodeMessage(data))

    expect(payload.buffer).toBe(data)
    expect(payload.byteOffset).toBe(HEADER_SIZE)
  })

  it('reads a scene from its JSON', () => {
    expect(decodeMessage(encodeScene(clothScene))).toEqual({ type: 'scene', scene: clothScene })
  })

  it.each([
    ['is shorter than a header', bytes('0100000001000000')],
    ['is of an unknown type', bytes('03000000' + '01000000' + '00000000' + '00000000')],
    [
      'lacks bytes of its payload',
      bytes(FRAME_TYPE + '01000000' + '00000000' + '08000000' + '0000803f'),
    ],
    ['holds part of a float', bytes(FRAME_TYPE + '01000000' + '00000000' + '02000000' + '0000')],
    ['holds a scene that is not JSON', sceneMessage(new TextEncoder().encode('{"camera":'))],
    ['holds a scene that is not UTF-8', sceneMessage(Uint8Array.of(0x22, 0xff, 0x22))],
    [
      'holds a scene that does not follow the protocol',
      encodeScene({ ...clothScene, objects: [{ kind: 'Floor', radius: -1 }] }),
    ],
  ])('rejects a message that %s', (_, message) => {
    expect(() => decodeMessage(message)).toThrow(ProtocolError)
  })
})
