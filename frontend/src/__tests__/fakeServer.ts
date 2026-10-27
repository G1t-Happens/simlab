import { vi } from 'vitest'

import type { SimulationInfo } from '@/api/simulations'

import { cloth, lorenz } from './simulations'

/**
 * Lets `fetch` answer like the server: it's up, offers the given simulations and opens a new room
 * for every request, `room-1` first
 *
 * @returns the mock of `fetch`, which records the requests
 */
export function stubServer(
  simulations: readonly SimulationInfo[] = [cloth, lorenz],
): ReturnType<typeof vi.fn<typeof fetch>> {
  let rooms = 0
  const fetchMock = vi.fn<typeof fetch>((input, init) => {
    switch (input) {
      case '/actuator/health':
        return Promise.resolve(Response.json({ status: 'UP' }))
      case '/api/simulations':
        return Promise.resolve(Response.json(simulations))
      case '/api/rooms':
        rooms++
        return Promise.resolve(
          Response.json({ id: `room-${String(rooms)}`, type: typeOf(init) }, { status: 201 }),
        )
      default:
        return Promise.resolve(new Response('Not Found', { status: 404 }))
    }
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function typeOf(init: RequestInit | undefined): unknown {
  if (typeof init?.body !== 'string') {
    throw new TypeError('A request to open a room has a JSON body')
  }
  const { type } = JSON.parse(init.body) as { type: unknown }
  return type
}
