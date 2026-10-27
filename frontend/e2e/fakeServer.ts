import type { Page, WebSocketRoute } from '@playwright/test'

import { encodeFrame, encodeScene } from './messages.ts'

export interface FakeSimulation {
  /** how `/api/simulations` describes the simulation */
  readonly info: { readonly type: string; readonly [field: string]: unknown }
  /** the scene every room sends first */
  readonly scene: object
  /**
   * Floats of the frame after a tick, the first tick being 1, while the given point is selected,
   * if any
   */
  readonly frame: (tick: number, selected: number | undefined) => readonly number[]
  /**
   * Floats of the x-ray after a tick, which the frames hold after those of the objects while the
   * page shows the x-ray. None if the scene has no x-ray
   */
  readonly xray?: (tick: number) => readonly number[]
  /** how `/api/simulations/{type}/lesson` describes the lesson, if a test opens it */
  readonly lesson?: object
}

export interface SentMessage {
  readonly type: string
  readonly [field: string]: unknown
}

export interface FakeServer {
  /** bodies of the requests that opened rooms, oldest first */
  readonly openedRooms: unknown[]
  /** URLs of the WebSockets the page opened, oldest first */
  readonly joinedRooms: string[]
  /** messages the page sent through any WebSocket, oldest first */
  readonly messages: SentMessage[]
  /** whether the server answers, while it doesn't every request fails */
  up: boolean
  /** goes down like a server that shuts down, closing the WebSockets of its rooms */
  shutDown(): void
}

/**
 * Answers like the server with a single simulation and streams its scene and then a frame per tick
 * to every WebSocket of a room, with the point the page selected in the room and with the x-ray
 * while the page shows it there
 */
export async function fakeServer(page: Page, simulation: FakeSimulation): Promise<FakeServer> {
  const sockets = new Set<WebSocketRoute>()
  const server: FakeServer = {
    openedRooms: [],
    joinedRooms: [],
    messages: [],
    up: true,
    shutDown() {
      this.up = false
      for (const socket of sockets) {
        void socket.close({ code: 1001, reason: 'Server shutting down' })
      }
    },
  }
  let rooms = 0
  await page.route('/actuator/health', (route) =>
    server.up ? route.fulfill({ json: { status: 'UP' } }) : route.abort('connectionrefused'),
  )
  await page.route('/api/simulations', (route) =>
    server.up ? route.fulfill({ json: [simulation.info] }) : route.abort('connectionrefused'),
  )
  const { lesson } = simulation
  if (lesson !== undefined) {
    await page.route(`/api/simulations/${simulation.info.type}/lesson`, (route) =>
      server.up ? route.fulfill({ json: lesson }) : route.abort('connectionrefused'),
    )
  }
  await page.route('/api/rooms', (route) => {
    if (!server.up) {
      return route.abort('connectionrefused')
    }
    rooms++
    server.openedRooms.push(route.request().postDataJSON())
    return route.fulfill({
      status: 201,
      json: { id: `room-${String(rooms)}`, type: simulation.info.type },
    })
  })
  await page.routeWebSocket(/\/ws\/rooms\/[^/]+$/, (socket) => {
    server.joinedRooms.push(socket.url())
    sockets.add(socket)
    socket.send(encodeScene(simulation.scene))
    let tick = 0
    let selected: number | undefined
    let xrayShown = false
    const stream = setInterval(() => {
      tick++
      const xray = xrayShown ? (simulation.xray?.(tick) ?? []) : []
      socket.send(encodeFrame(tick, tick / 30, [...simulation.frame(tick, selected), ...xray]))
    }, 1000 / 30)
    socket.onMessage((message) => {
      const sent = JSON.parse(message.toString()) as SentMessage
      server.messages.push(sent)
      if (sent.type === 'select' && typeof sent.point === 'number') {
        selected = sent.point
      } else if (sent.type === 'deselect') {
        selected = undefined
      } else if (sent.type === 'xray' && typeof sent.shown === 'boolean') {
        xrayShown = sent.shown
      }
    })
    socket.onClose(() => {
      clearInterval(stream)
      sockets.delete(socket)
    })
  })
  return server
}
