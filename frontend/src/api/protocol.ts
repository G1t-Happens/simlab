import { isScene, type Scene } from './scene'

/**
 * Messages between the browser and a room. Rooms send binary messages that start with a 16-byte
 * header, little-endian. The browser sends JSON:
 *
 * | Offset | Type    | Field                                 |
 * | ------ | ------- | ------------------------------------- |
 * | 0      | uint8   | message type, see {@link MessageType} |
 * | 1      |         | reserved, 3 bytes                     |
 * | 4      | uint32  | tick number                           |
 * | 8      | float32 | simulation time                       |
 * | 12     | uint32  | length of the payload in bytes        |
 * | 16     |         | payload                               |
 *
 * A room first sends its scene as JSON in UTF-8, then a frame of floats per tick. While the room
 * shows the x-ray of the simulation the floats of the x-ray follow those of the frame
 */

export const HEADER_SIZE = 16

export const MessageType = {
  FRAME: 1,
  SCENE: 2,
} as const

/** state of a simulation after a tick */
export interface Frame {
  readonly type: 'frame'
  /** wraps around after 2^32 ticks */
  readonly tick: number
  /** simulated time, in the simulation's unit of time */
  readonly time: number
  /**
   * The floats of one scene object after another, followed by those of the x-ray objects while the
   * room shows it. A view on the received message
   */
  readonly payload: Float32Array
}

/** what the simulation shows and how its frames are laid out */
export interface SceneMessage {
  readonly type: 'scene'
  readonly scene: Scene
}

export type ServerMessage = Frame | SceneMessage

export interface ParameterMessage {
  readonly type: 'param'
  readonly name: string
  readonly value: number
}

/** grabs a vertex and holds it at a position */
export interface GrabMessage {
  readonly type: 'grab'
  readonly vertex: number
  readonly x: number
  readonly y: number
  readonly z: number
}

export interface DragMessage {
  readonly type: 'drag'
  readonly vertex: number
  readonly x: number
  readonly y: number
  readonly z: number
}

export interface ReleaseMessage {
  readonly type: 'release'
  readonly vertex: number
}

/** selects a point of a selectable point cloud, the simulation then reports its values */
export interface SelectMessage {
  readonly type: 'select'
  /** index among the points of all point clouds of the scene */
  readonly point: number
}

export interface DeselectMessage {
  readonly type: 'deselect'
}

/** stops the room's clock: the simulation stands still but inputs still apply */
export interface PauseMessage {
  readonly type: 'pause'
}

export interface PlayMessage {
  readonly type: 'play'
}

/** advances a paused room by one tick */
export interface StepMessage {
  readonly type: 'step'
}

/** plays the simulation at a share of real time, above 0 and at most 1, for slow motion */
export interface SpeedMessage {
  readonly type: 'speed'
  readonly value: number
}

/** lets the room's frames show the x-ray after the floats of its objects, or no longer */
export interface XrayMessage {
  readonly type: 'xray'
  readonly shown: boolean
}

/**
 * A message from the browser to its room: an input of the simulation, one to its clock, or one
 * that shows its x-ray
 */
export type ClientMessage =
  | ParameterMessage
  | GrabMessage
  | DragMessage
  | ReleaseMessage
  | SelectMessage
  | DeselectMessage
  | PauseMessage
  | PlayMessage
  | StepMessage
  | SpeedMessage
  | XrayMessage

export class ProtocolError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'ProtocolError'
  }
}

const utf8 = new TextDecoder('utf-8', { fatal: true })

/**
 * Decodes a message of a room, frames stay views on the message
 *
 * @throws ProtocolError if the message is of an unknown type, cut off or malformed
 */
export function decodeMessage(message: ArrayBuffer): ServerMessage {
  if (message.byteLength < HEADER_SIZE) {
    throw new ProtocolError(
      `Message of ${String(message.byteLength)} bytes is shorter than a header`,
    )
  }
  const header = new DataView(message, 0, HEADER_SIZE)
  const payloadLength = header.getUint32(12, true)
  const length = HEADER_SIZE + payloadLength
  if (message.byteLength !== length) {
    throw new ProtocolError(
      `Message of ${String(message.byteLength)} bytes, but its header announces ${String(length)}`,
    )
  }
  const messageType = header.getUint8(0)
  switch (messageType) {
    case MessageType.FRAME:
      if (payloadLength % Float32Array.BYTES_PER_ELEMENT !== 0) {
        throw new ProtocolError(`A frame of ${String(payloadLength)} bytes holds no whole floats`)
      }
      return {
        type: 'frame',
        tick: header.getUint32(4, true),
        time: header.getFloat32(8, true),
        payload: new Float32Array(
          message,
          HEADER_SIZE,
          payloadLength / Float32Array.BYTES_PER_ELEMENT,
        ),
      }
    case MessageType.SCENE:
      return { type: 'scene', scene: parseScene(new Uint8Array(message, HEADER_SIZE)) }
    default:
      throw new ProtocolError(`Unknown message type ${String(messageType)}`)
  }
}

function parseScene(json: Uint8Array): Scene {
  let scene: unknown
  try {
    scene = JSON.parse(utf8.decode(json))
  } catch (error) {
    throw new ProtocolError(`The scene is not JSON in UTF-8: ${String(error)}`)
  }
  if (!isScene(scene)) {
    throw new ProtocolError('The scene does not follow the protocol')
  }
  return scene
}
