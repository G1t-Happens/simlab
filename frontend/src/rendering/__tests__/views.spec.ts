import {
  AdditiveBlending,
  Box3,
  BufferGeometry,
  Color,
  DataTexture,
  DoubleSide,
  FrontSide,
  LineSegments,
  Material,
  MeshPhysicalMaterial,
  NormalBlending,
  Points,
  PointsMaterial,
  SRGBColorSpace,
  Texture,
  Vector3,
} from 'three'
import { describe, expect, it, vi } from 'vitest'

import type * as Description from '@/api/scene'

import {
  createView,
  CubesView,
  FloorView,
  LinesView,
  MeshView,
  PointCloudView,
  SphereView,
} from '../views'

const UP = new Vector3(0, 1, 0)

const material: Description.Material = { color: { red: 217, green: 81, blue: 60 }, finish: 'MATTE' }

function points(changes: Partial<Description.PointCloud> = {}): Description.PointCloud {
  return {
    kind: 'PointCloud',
    count: 3,
    firstColor: { red: 255, green: 0, blue: 0 },
    lastColor: { red: 0, green: 0, blue: 255 },
    trailLength: 4,
    glowing: false,
    selectable: false,
    ...changes,
  }
}

/** square of four vertices in the plane z = 0, its two triangles face +z */
function square(changes: Partial<Description.Mesh> = {}): Description.Mesh {
  return {
    kind: 'Mesh',
    vertexCount: 4,
    triangles: [0, 1, 3, 0, 3, 2],
    material,
    grabbable: true,
    ...changes,
  }
}

/** lines colored from blue at -2 through gray at 0 to red at 2 */
function lines(changes: Partial<Description.Lines> = {}): Description.Lines {
  return {
    kind: 'Lines',
    capacity: 2,
    label: 'Strain',
    scale: {
      label: 'ε',
      unit: '%',
      min: -2,
      max: 2,
      colors: [
        { red: 0, green: 0, blue: 255 },
        { red: 128, green: 128, blue: 128 },
        { red: 255, green: 0, blue: 0 },
      ],
    },
    directed: false,
    ...changes,
  }
}

const cubes: Description.Cubes = {
  kind: 'Cubes',
  capacity: 2,
  label: 'Cells',
  color: { red: 63, green: 185, blue: 80 },
}

/** colors of the start and the end of every line a view shows, in sRGB */
function lineColors(view: LinesView): string[][] {
  const colors = view.object.geometry.getAttribute('color')
  const hexOf = (vertex: number) => new Color().fromBufferAttribute(colors, vertex).getHexString()
  return Array.from({ length: view.object.geometry.drawRange.count / 2 }, (_, line) => [
    hexOf(2 * line),
    hexOf(2 * line + 1),
  ])
}

function pointsOf(view: PointCloudView): Points {
  const points = view.object.children.find((object): object is Points => object instanceof Points)
  if (points === undefined) {
    throw new Error('The view shows no points')
  }
  return points
}

function materialOf(view: PointCloudView): PointsMaterial {
  const { material } = pointsOf(view)
  if (!(material instanceof PointsMaterial)) {
    throw new Error('The points have no material of points')
  }
  return material
}

function trailsOf(view: PointCloudView): LineSegments | undefined {
  return view.object.children.find(
    (object): object is LineSegments => object instanceof LineSegments,
  )
}

describe('createView', () => {
  it.each([
    ['point cloud', points(), PointCloudView],
    ['mesh', square(), MeshView],
    ['sphere', { kind: 'Sphere', material }, SphereView],
    ['floor', { kind: 'Floor', radius: 4 }, FloorView],
    ['set of lines', lines(), LinesView],
    ['set of cubes', cubes, CubesView],
  ] as const)('creates the view of a %s', (_, object, type) => {
    expect(createView(object, UP)).toBeInstanceOf(type)
  })

  it.each([
    ['point cloud with trails', points(), 2],
    ['mesh', square(), 1],
    ['sphere', { kind: 'Sphere', material }, 1],
    ['floor with its grid', { kind: 'Floor', radius: 4 }, 2],
    ['set of lines', lines(), 1],
    ['set of cubes', cubes, 1],
  ] as const)('frees the geometries and materials of a %s', (_, object, count) => {
    const disposeGeometry = vi.spyOn(BufferGeometry.prototype, 'dispose')
    const disposeMaterial = vi.spyOn(Material.prototype, 'dispose')

    createView(object, UP).dispose()

    expect(disposeGeometry).toHaveBeenCalledTimes(count)
    expect(disposeMaterial).toHaveBeenCalledTimes(count)
  })
})

describe('PointCloudView', () => {
  it('hides the points until a frame tells where they are', () => {
    const view = new PointCloudView(points())
    expect(view.object.visible).toBe(false)

    view.update(Float32Array.of(1, 2, 3, 4, 5, 6, 7, 8, 9))

    expect(view.object.visible).toBe(true)
    expect(pointsOf(view).geometry.getAttribute('position').array).toEqual(
      Float32Array.of(1, 2, 3, 4, 5, 6, 7, 8, 9),
    )
  })

  it('keeps where the latest frame put the points, for the pointer to select them', () => {
    const view = new PointCloudView(points({ selectable: true }))

    view.update(Float32Array.of(1, 2, 3, 4, 5, 6, 7, 8, 9))

    expect(view.selectable).toBe(true)
    expect(view.coordinates).toEqual(Float32Array.of(1, 2, 3, 4, 5, 6, 7, 8, 9))
  })

  it('blends the colors of the points along the hue, from the first to the last color', () => {
    const view = new PointCloudView(points())

    const colors = pointsOf(view).geometry.getAttribute('color')
    const hexOf = (point: number) => new Color().fromBufferAttribute(colors, point).getHexString()
    expect([hexOf(0), hexOf(1), hexOf(2)]).toEqual(['ff0000', '00ff00', '0000ff'])
  })

  it('draws trails that grow with every frame, up to their length', () => {
    const view = new PointCloudView(points({ count: 1, trailLength: 3 }))
    const segments: (number | undefined)[] = []

    for (let frame = 0; frame < 4; frame++) {
      view.update(Float32Array.of(frame, 0, 0))
      segments.push(trailsOf(view)?.geometry.drawRange.count)
    }

    expect(segments).toEqual([0, 2, 4, 4])
  })

  it('draws no trails that connect no frames', () => {
    expect(trailsOf(new PointCloudView(points({ trailLength: 0 })))).toBeUndefined()
  })

  it('draws points as dots that hide what lies behind them', () => {
    expect(materialOf(new PointCloudView(points()))).toMatchObject({
      blending: NormalBlending,
      map: null,
      depthWrite: true,
    })
  })

  it('draws glowing points as soft spots of light that add up where they crowd', () => {
    const material = materialOf(new PointCloudView(points({ glowing: true })))

    expect(material).toMatchObject({
      blending: AdditiveBlending,
      transparent: true,
      depthWrite: false,
    })
    expect(material.map).toBeInstanceOf(DataTexture)
  })

  it('lets the light of a glowing point fade from its center to its edges', () => {
    const { map } = materialOf(new PointCloudView(points({ glowing: true })))
    if (!(map instanceof DataTexture)) {
      throw new Error('Glowing points have no texture of data')
    }
    const { data, width, height } = map.image

    const opacity = (column: number, row: number) => data?.[4 * (row * width + column) + 3]
    expect(opacity(width / 2, height / 2)).toBeGreaterThan(250)
    expect(opacity(width / 2, 0)).toBeLessThan(3)
    expect(opacity(0, 0)).toBe(0)
  })

  it('frees the texture of glowing points', () => {
    const disposeTexture = vi.spyOn(Texture.prototype, 'dispose')

    new PointCloudView(points({ glowing: true })).dispose()

    expect(disposeTexture).toHaveBeenCalledOnce()
  })
})

describe('MeshView', () => {
  it('shows the surface once a frame tells where its vertices are', () => {
    const view = new MeshView(square())
    expect(view.object.visible).toBe(false)

    view.update(Float32Array.of(0, 0, 0, 1, 0, 0, 0, 1, 0, 1, 1, 0))

    const { geometry } = view.object
    expect(view.object.visible).toBe(true)
    expect(geometry.index?.array).toEqual(Uint16Array.of(0, 1, 3, 0, 3, 2))
    expect(geometry.getAttribute('normal').getZ(0)).toBeCloseTo(1)
    expect(geometry.boundingSphere?.radius).toBeCloseTo(Math.SQRT1_2)
  })

  it('shows both sides of the surface, in its color', () => {
    const { object, grabbable } = new MeshView(square())

    expect(object.material.side).toBe(DoubleSide)
    expect(object.material.color.getHexString()).toBe('d9513c')
    expect(grabbable).toBe(true)
  })

  it.each([
    ['MATTE', 1, 0],
    ['GLOSSY', 0.4, 0.1],
  ] as const)('gives a %s surface its roughness and metalness', (finish, roughness, metalness) => {
    const { object } = new MeshView(square({ material: { ...material, finish } }))

    expect(object.material.roughness).toBe(roughness)
    expect(object.material.metalness).toBe(metalness)
  })

  it('gives fabric a sheen of its color, halfway to white', () => {
    const color = { red: 215, green: 81, blue: 61 }

    const { object } = new MeshView(square({ material: { color, finish: 'FABRIC' } }))

    expect(object.material).toBeInstanceOf(MeshPhysicalMaterial)
    expect(object.material).toHaveProperty('sheen', 1)
    expect(object.material).toHaveProperty(
      'sheenColor',
      new Color().setRGB(235 / 255, 168 / 255, 158 / 255, SRGBColorSpace),
    )
  })
})

describe('MeshView#setTranslucent', () => {
  it('lets what lies behind the surface show through it', () => {
    const view = new MeshView(square())

    view.setTranslucent(true)

    expect(view.object.material).toMatchObject({
      transparent: true,
      opacity: 0.2,
      depthWrite: false,
    })
  })

  it('lies a little behind lines on the surface, so as not to tint them', () => {
    const view = new MeshView(square())

    view.setTranslucent(true)

    expect(view.object.material).toMatchObject({
      polygonOffset: true,
      polygonOffsetFactor: 1,
      polygonOffsetUnits: 1,
    })
  })

  it('makes the surface opaque again', () => {
    const view = new MeshView(square())
    view.setTranslucent(true)

    view.setTranslucent(false)

    expect(view.object.material).toMatchObject({
      transparent: false,
      opacity: 1,
      depthWrite: true,
      polygonOffset: false,
    })
  })
})

describe('LinesView', () => {
  it('hides the lines until a frame tells where they are', () => {
    const view = new LinesView(lines())
    expect(view.object.visible).toBe(false)

    view.update(Float32Array.of(1, 1, 2, 3, 4, 5, 6, 0, ...Array<number>(7).fill(0)))

    const { geometry } = view.object
    expect(view.object.visible).toBe(true)
    expect(geometry.drawRange.count).toBe(2)
    expect(geometry.getAttribute('position').array.subarray(0, 6)).toEqual(
      Float32Array.of(1, 2, 3, 4, 5, 6),
    )
  })

  it.each([
    ['all lines', 2, 4],
    ['no more lines than it has room for', 3, 4],
    ['no lines for a negative number', -1, 0],
    ['no lines for a fraction', 1.5, 0],
    ['no lines for an unknown number', Number.NaN, 0],
  ])('draws %s', (_, count, vertices) => {
    const view = new LinesView(lines())

    view.update(Float32Array.of(count, ...Array<number>(14).fill(0)))

    expect(view.object.geometry.drawRange.count).toBe(vertices)
  })

  it('colors every line by its value, which takes the color of the nearer end beyond them', () => {
    const view = new LinesView(lines())

    view.update(Float32Array.of(2, 0, 0, 0, 1, 0, 0, -2, 0, 0, 0, 1, 0, 0, 7))

    expect(lineColors(view)).toEqual([
      ['0000ff', '0000ff'],
      ['ff0000', 'ff0000'],
    ])
  })

  it('blends the colors next to a value in between, as the legend does', () => {
    const view = new LinesView(lines())

    view.update(Float32Array.of(2, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1))

    const colors = view.object.geometry.getAttribute('color')
    expect(lineColors(view)[0]).toEqual(['808080', '808080'])
    const halfway = new Color().setRGB(191.5 / 255, 64 / 255, 64 / 255, SRGBColorSpace)
    const blend = new Color().fromBufferAttribute(colors, 3)
    expect(blend.r).toBeCloseTo(halfway.r, 5)
    expect(blend.g).toBeCloseTo(halfway.g, 5)
    expect(blend.b).toBeCloseTo(halfway.b, 5)
  })

  it('dims the start of a directed line, so that it points to its end', () => {
    const view = new LinesView(lines({ directed: true }))

    view.update(Float32Array.of(1, 0, 0, 0, 1, 0, 0, 2, ...Array<number>(7).fill(0)))

    const colors = view.object.geometry.getAttribute('color')
    const start = new Color().fromBufferAttribute(colors, 0)
    const end = new Color().fromBufferAttribute(colors, 1)
    expect(end.getHexString()).toBe('ff0000')
    expect(start.r).toBeCloseTo(0.15 * end.r, 5)
    expect(start.g).toBe(0)
  })
})

describe('CubesView', () => {
  it('hides the cubes until a frame tells where they are', () => {
    const view = new CubesView(cubes)
    expect(view.object.visible).toBe(false)

    view.update(Float32Array.of(1, 1, 2, 3, 0.5, 0, 0, 0, 0))

    expect(view.object.visible).toBe(true)
  })

  it('draws the twelve edges of every cube, between its corners', () => {
    const view = new CubesView(cubes)

    view.update(Float32Array.of(2, 1, 2, 3, 0.5, 0, 0, 0, 2))

    const { geometry } = view.object
    const corners = geometry.getAttribute('position')
    const corner = (vertex: number) => new Vector3().fromBufferAttribute(corners, vertex)
    const edges = Array.from({ length: 12 }, (_, edge): [Vector3, Vector3] => [
      corner(geometry.index?.getX(2 * edge) ?? Number.NaN),
      corner(geometry.index?.getX(2 * edge + 1) ?? Number.NaN),
    ])
    expect(geometry.drawRange.count).toBe(2 * 24)
    // twelve different edges of length 1 between the corners of a box of edge 1 make up its cube
    expect(edges.map(([from, to]) => from.distanceTo(to))).toEqual(Array<number>(12).fill(1))
    /** names an edge by its corners, whichever way round it runs */
    const name = (edge: Vector3[]) =>
      edge
        .map((point) => point.toArray().join())
        .sort()
        .join(' ')
    expect(new Set(edges.map(name)).size).toBe(12)
    expect(new Box3().setFromPoints(edges.flat())).toEqual(
      new Box3(new Vector3(0.5, 1.5, 2.5), new Vector3(1.5, 2.5, 3.5)),
    )
    expect([corner(8).toArray(), corner(15).toArray()]).toEqual([
      [-2, -2, -2],
      [2, 2, 2],
    ])
  })

  it('lets what lies behind the edges show through where they crowd', () => {
    const { object } = new CubesView(cubes)

    expect(object.material).toMatchObject({ transparent: true, opacity: 0.55, depthWrite: false })
    expect(object.material.color.getHexString()).toBe('3fb950')
  })
})

describe('SphereView', () => {
  it('moves and scales the sphere as a frame tells', () => {
    const view = new SphereView({ kind: 'Sphere', material })
    expect(view.object.visible).toBe(false)

    view.update(Float32Array.of(0.25, 0.5, -1, 0.75))

    expect(view.object.visible).toBe(true)
    expect(view.object.position.toArray()).toEqual([0.25, 0.5, -1])
    expect(view.object.scale.toArray()).toEqual([0.75, 0.75, 0.75])
    expect(view.object.material.side).toBe(FrontSide)
  })
})

describe('FloorView', () => {
  it('lies around the origin, facing up from the start', () => {
    const up = new Vector3(0, 0, 1)

    const { object } = new FloorView({ kind: 'Floor', radius: 4 }, up)

    expect(object.visible).toBe(true)
    expect(object.position.length()).toBe(0)
    expect(new Vector3(0, 1, 0).applyQuaternion(object.quaternion).distanceTo(up)).toBeCloseTo(0)
  })
})
