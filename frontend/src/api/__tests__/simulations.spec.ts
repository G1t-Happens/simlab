import { describe, expect, it, vi } from 'vitest'

import { lorenzLesson } from '@/__tests__/lessons'
import { galaxies, lorenz } from '@/__tests__/simulations'

import { ApiError, fetchLesson, fetchSimulations, openRoom } from '../simulations'

const [sigma, , order] = lorenz.parameters

function withOrder(changes: Record<string, unknown>): unknown {
  return { ...lorenz, parameters: [{ ...order, ...changes }] }
}

function stubFetch(response: Response) {
  const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('fetchSimulations', () => {
  it('fetches the simulations as JSON', async () => {
    const fetchMock = stubFetch(Response.json([galaxies, lorenz]))

    await expect(fetchSimulations()).resolves.toEqual([galaxies, lorenz])
    expect(fetchMock).toHaveBeenCalledExactlyOnceWith('/api/simulations', {
      headers: { Accept: 'application/json' },
    })
  })

  it.each([
    ['without parameters', { ...lorenz, parameters: undefined }],
    ['without description', { ...lorenz, description: undefined }],
    ['without unit of time', { ...lorenz, timeUnit: undefined }],
    ['with a parameter without unit', { ...lorenz, parameters: [{ ...sigma, unit: undefined }] }],
    [
      'with a parameter that takes effect at an unknown time',
      { ...lorenz, parameters: [{ ...sigma, effect: 'SOMETIME' }] },
    ],
    [
      'with a parameter of an unknown kind',
      { ...lorenz, parameters: [{ ...sigma, kind: 'Dial' }] },
    ],
    ['with a choice of a single option', withOrder({ options: [{ value: 1, label: 'Euler' }] })],
    [
      'with an option without label',
      withOrder({ options: [{ value: 1 }, { value: 4, label: 'Runge-Kutta 4' }] }),
    ],
    [
      'with an option whose value is no number',
      withOrder({
        options: [
          { value: '1', label: 'Euler' },
          { value: 4, label: 'Runge-Kutta 4' },
        ],
      }),
    ],
  ])('rejects a list with a simulation %s', async (_, simulation) => {
    stubFetch(Response.json([simulation]))

    await expect(fetchSimulations()).rejects.toThrow('invalid list of simulations')
  })
})

describe('fetchLesson', () => {
  it('fetches the lesson of a simulation as JSON', async () => {
    const fetchMock = stubFetch(Response.json(lorenzLesson))

    await expect(fetchLesson('lorenz')).resolves.toEqual(lorenzLesson)
    expect(fetchMock).toHaveBeenCalledExactlyOnceWith('/api/simulations/lorenz/lesson', {
      headers: { Accept: 'application/json' },
    })
  })

  it('rejects an invalid lesson', async () => {
    stubFetch(Response.json({ blocks: [{ kind: 'Video' }] }))

    await expect(fetchLesson('lorenz')).rejects.toThrow('invalid lesson')
  })

  it('reports the detail of a problem document', async () => {
    stubFetch(
      Response.json(
        { status: 404, detail: "There is no simulation of type 'quantum'" },
        { status: 404 },
      ),
    )

    await expect(fetchLesson('quantum')).rejects.toThrow(
      new ApiError(404, "There is no simulation of type 'quantum'"),
    )
  })
})

describe('openRoom', () => {
  it('posts the type and the parameters of the room', async () => {
    const fetchMock = stubFetch(Response.json({ id: 'room-1', type: 'lorenz' }, { status: 201 }))

    await expect(openRoom('lorenz', { rho: 20 })).resolves.toEqual({ id: 'room-1', type: 'lorenz' })
    expect(fetchMock).toHaveBeenCalledExactlyOnceWith('/api/rooms', {
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: '{"type":"lorenz","parameters":{"rho":20}}',
    })
  })

  it('reports the detail of a problem document', async () => {
    stubFetch(
      Response.json({ status: 400, detail: "Unknown simulation type 'x'" }, { status: 400 }),
    )

    const failure = openRoom('x')

    await expect(failure).rejects.toThrow(new ApiError(400, "Unknown simulation type 'x'"))
    await expect(failure).rejects.toHaveProperty('status', 400)
  })

  it('reports the status of an error without problem document', async () => {
    stubFetch(new Response('Bad Gateway', { status: 502 }))

    await expect(openRoom('lorenz')).rejects.toThrow('Request failed with HTTP status 502')
  })

  it('rejects an invalid room', async () => {
    stubFetch(Response.json({ id: 1 }, { status: 201 }))

    await expect(openRoom('lorenz')).rejects.toThrow('invalid room')
  })
})
