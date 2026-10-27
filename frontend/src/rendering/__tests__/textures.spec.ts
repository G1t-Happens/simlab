import { LinearFilter } from 'three'
import { describe, expect, it } from 'vitest'

import { radialTexture } from '../textures'

describe('radialTexture', () => {
  it('makes every texel white, as opaque as asked for at its distance from the center', () => {
    // opaque at the center, transparent from a distance of 1 on
    const { data, width, height } = radialTexture(4, (distance) => Math.max(0, 1 - distance)).image

    const texel = (row: number, column: number) => {
      const start = 4 * (row * width + column)
      return Array.from(data?.subarray(start, start + 4) ?? [])
    }
    expect([width, height]).toEqual([4, 4])
    // the centers of the texels lie 0.25 and 0.75 from the middle of the texture along each axis
    expect(texel(1, 1)).toEqual([255, 255, 255, Math.round(255 * (1 - Math.hypot(0.25, 0.25)))])
    expect(texel(1, 3)).toEqual([255, 255, 255, Math.round(255 * (1 - Math.hypot(0.75, 0.25)))])
    expect(texel(0, 0)).toEqual([255, 255, 255, 0])
  })

  it('blends its texels smoothly where it is drawn larger or smaller', () => {
    const texture = radialTexture(2, () => 1)

    expect(texture.magFilter).toBe(LinearFilter)
    expect(texture.minFilter).toBe(LinearFilter)
  })
})
