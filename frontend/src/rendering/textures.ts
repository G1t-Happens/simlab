import { DataTexture, LinearFilter } from 'three'

/**
 * Square texture of white texels whose opacity depends on how far they lie from the center, like
 * the soft spot of a glowing point or a ring
 *
 * @param size texels along each edge
 * @param opacityAt opacity from 0 to 1 at a distance from the center, which is 1 in the middle of
 *     every edge
 */
export function radialTexture(size: number, opacityAt: (distance: number) => number): DataTexture {
  const data = new Uint8Array(4 * size * size)
  for (let row = 0; row < size; row++) {
    for (let column = 0; column < size; column++) {
      // center of the texel, from -1 to 1 across the texture
      const x = (2 * column + 1) / size - 1
      const y = (2 * row + 1) / size - 1
      const texel = 4 * (row * size + column)
      data.fill(255, texel, texel + 3)
      data[texel + 3] = Math.round(255 * opacityAt(Math.hypot(x, y)))
    }
  }
  const texture = new DataTexture(data, size, size)
  texture.magFilter = LinearFilter
  texture.minFilter = LinearFilter
  texture.needsUpdate = true
  return texture
}
