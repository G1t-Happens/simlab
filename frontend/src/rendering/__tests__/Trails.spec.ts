import { describe, expect, it } from 'vitest'

import { Trails } from '../Trails'

describe('Trails', () => {
  it('connects every particle to its position one frame earlier', () => {
    const trails = new Trails(2, 3)

    // vertex = age * particleCount + particle
    expect([...trails.indices]).toEqual([0, 2, 1, 3, 2, 4, 3, 5])
  })

  it('keeps the newest frame first and drops the oldest one once full', () => {
    const trails = new Trails(1, 3)

    for (const position of [1, 2, 3, 4]) {
      trails.push(Float32Array.of(position, position, position))
    }

    expect([...trails.positions]).toEqual([4, 4, 4, 3, 3, 3, 2, 2, 2])
  })

  it('draws segments only between frames it has seen', () => {
    const trails = new Trails(2, 3)
    const indexCounts = [trails.indexCount]

    for (let frame = 0; frame < 4; frame++) {
      trails.push(new Float32Array(6))
      indexCounts.push(trails.indexCount)
    }

    expect(indexCounts).toEqual([0, 0, 4, 8, 8])
  })

  it('forgets every frame when cleared', () => {
    const trails = new Trails(1, 3)
    trails.push(new Float32Array(3))
    trails.push(new Float32Array(3))

    trails.clear()

    expect(trails.indexCount).toBe(0)
  })

  it('rejects frames of another number of particles', () => {
    expect(() => {
      new Trails(2, 3).push(new Float32Array(3))
    }).toThrow(RangeError)
  })

  it('needs at least two frames', () => {
    expect(() => new Trails(1, 1)).toThrow(RangeError)
  })
})
