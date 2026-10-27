import {
  AdditiveBlending,
  BufferAttribute,
  BufferGeometry,
  CircleGeometry,
  Color,
  DoubleSide,
  DynamicDrawUsage,
  FrontSide,
  GridHelper,
  Group,
  LineBasicMaterial,
  LineSegments,
  MathUtils,
  Mesh,
  MeshPhysicalMaterial,
  MeshStandardMaterial,
  Points,
  PointsMaterial,
  SphereGeometry,
  SRGBColorSpace,
  Vector3,
  type DataTexture,
  type Object3D,
  type Side,
} from 'three'

import { FLOATS_PER_CUBE, FLOATS_PER_LINE } from '@/api/scene'
import type * as Description from '@/api/scene'

import { radialTexture } from './textures'
import { Trails } from './Trails'

const Y_AXIS = new Vector3(0, 1, 0)

/** size in pixels of a glowing point, its texture fills it with a soft spot of light */
const GLOW_SIZE = 5

/** brightness of a glowing point at its center, as a share of its color */
const GLOW_BRIGHTNESS = 0.3

/** the light of a glowing point falls off as e^(-k r²), with r = 1 at its edge */
const GLOW_FALLOFF = 6

/** texels along each edge of the texture of a glowing point */
const GLOW_TEXTURE_SIZE = 32

/** brightness of the start of a directed line, as a share of the brightness of its end */
const DIRECTED_START_BRIGHTNESS = 0.15

/** lets what lies behind the cube edges show through where they crowd */
const CUBE_OPACITY = 0.55

/** opacity of a surface while the x-ray shows through it */
const TRANSLUCENT_OPACITY = 0.2

/**
 * The twelve edges of a cube as pairs of its corners: four along x, four along y and four along z.
 * Bits 0, 1 and 2 of the number of a corner tell whether it lies at the far end of the x, y and z
 * axis, so the corners of an edge differ in a single bit
 */
const CUBE_EDGES: readonly number[] = [
  // along x
  0, 1, 2, 3, 4, 5, 6, 7,
  // along y
  0, 2, 1, 3, 4, 6, 5, 7,
  // along z
  0, 4, 1, 5, 2, 6, 3, 7,
]

/** shows a scene object, which the frames of the simulation move */
export interface ObjectView {
  /** what the view adds to the scene */
  readonly object: Object3D
  /** shows the object in the state its floats from a frame describe */
  update(floats: Float32Array): void
  dispose(): void
}

/** scene object the browser renders in 3D, readouts show beside the scene instead */
export type RenderedObject = Exclude<Description.SceneObject, Description.Readouts>

/**
 * Creates the view of a scene object. Objects that move stay hidden until the first frame tells
 * where they are
 *
 * @param up unit vector that points up in the scene
 */
export function createView(object: RenderedObject, up: Vector3): ObjectView {
  switch (object.kind) {
    case 'PointCloud':
      return new PointCloudView(object)
    case 'Mesh':
      return new MeshView(object)
    case 'Sphere':
      return new SphereView(object)
    case 'Floor':
      return new FloorView(object, up)
    case 'Lines':
      return new LinesView(object)
    case 'Cubes':
      return new CubesView(object)
  }
}

/**
 * Points drawn as dots of two pixels with fading trails behind them, or as glowing stars whose soft
 * light adds up where they crowd
 */
export class PointCloudView implements ObjectView {
  readonly object = new Group()
  /** x, y and z of every point, where the latest frame put it */
  readonly coordinates: Float32Array
  readonly selectable: boolean
  private readonly points: Points<BufferGeometry, PointsMaterial>
  private readonly positions: BufferAttribute
  private readonly trails: TrailLines | undefined

  constructor({
    count,
    firstColor,
    lastColor,
    trailLength,
    glowing,
    selectable,
  }: Description.PointCloud) {
    const colors = gradient(count, toColor(firstColor), toColor(lastColor))
    this.coordinates = new Float32Array(3 * count)
    this.selectable = selectable
    this.positions = new BufferAttribute(this.coordinates, 3).setUsage(DynamicDrawUsage)
    this.points = new Points(
      new BufferGeometry()
        .setAttribute('position', this.positions)
        .setAttribute('color', new BufferAttribute(colors, 3)),
      glowing ? glowingMaterial() : dotMaterial(),
    )
    // the bounding volume would be computed once, from the initial positions at the origin
    this.points.frustumCulled = false
    this.object.add(this.points)
    this.trails = trailLength > 0 ? new TrailLines(colors, trailLength) : undefined
    if (this.trails) {
      this.object.add(this.trails.lines)
    }
    this.object.visible = false
  }

  update(floats: Float32Array): void {
    this.coordinates.set(floats)
    this.positions.needsUpdate = true
    this.trails?.push(floats)
    this.object.visible = true
  }

  dispose(): void {
    this.points.geometry.dispose()
    this.points.material.map?.dispose()
    this.points.material.dispose()
    this.trails?.dispose()
  }
}

/** dots of light that no tone mapping dims */
function dotMaterial(): PointsMaterial {
  return new PointsMaterial({
    size: 2,
    sizeAttenuation: false,
    vertexColors: true,
    toneMapped: false,
  })
}

/**
 * Glowing points: soft spots of light that add up where points crowd so dense regions shine
 * brightly, and that hide nothing behind them
 */
function glowingMaterial(): PointsMaterial {
  return new PointsMaterial({
    size: GLOW_SIZE,
    sizeAttenuation: false,
    vertexColors: true,
    color: new Color().setScalar(GLOW_BRIGHTNESS),
    map: glowTexture(),
    transparent: true,
    blending: AdditiveBlending,
    depthWrite: false,
    toneMapped: false,
  })
}

/**
 * Texture of a glowing point: white and opaque at the center, from where the opacity falls off like
 * a Gaussian towards the edges, which it reaches all but transparent
 */
function glowTexture(): DataTexture {
  return radialTexture(GLOW_TEXTURE_SIZE, (distance) => Math.exp(-GLOW_FALLOFF * distance ** 2))
}

/** line segments from every point to where it was in each of the latest frames, fading with age */
class TrailLines {
  readonly lines: LineSegments<BufferGeometry, LineBasicMaterial>
  private readonly trails: Trails
  private readonly positions: BufferAttribute

  /** @param colors three channels per point */
  constructor(colors: Float32Array, length: number) {
    this.trails = new Trails(colors.length / 3, length)
    this.positions = new BufferAttribute(this.trails.positions, 3).setUsage(DynamicDrawUsage)
    const geometry = new BufferGeometry()
      .setAttribute('position', this.positions)
      .setAttribute('color', new BufferAttribute(fadingColors(colors, length), 3))
      .setIndex(new BufferAttribute(this.trails.indices, 1))
    geometry.setDrawRange(0, 0)
    this.lines = new LineSegments(
      geometry,
      new LineBasicMaterial({
        vertexColors: true,
        blending: AdditiveBlending,
        transparent: true,
        depthWrite: false,
        toneMapped: false,
      }),
    )
    // the bounding volume would be computed once, from the initial positions at the origin
    this.lines.frustumCulled = false
  }

  push(positions: Float32Array): void {
    this.trails.push(positions)
    this.positions.needsUpdate = true
    this.lines.geometry.setDrawRange(0, this.trails.indexCount)
  }

  dispose(): void {
    this.lines.geometry.dispose()
    this.lines.material.dispose()
  }
}

/** blends the colors of the points along the hue, so points next to each other look alike */
function gradient(count: number, first: Color, last: Color): Float32Array {
  const colors = new Float32Array(3 * count)
  const color = new Color()
  for (let point = 0; point < count; point++) {
    color
      .copy(first)
      .lerpHSL(last, count > 1 ? point / (count - 1) : 0)
      .toArray(colors, 3 * point)
  }
  return colors
}

/**
 * Repeats the colors for every frame of the trails, fading out towards the oldest one. Trails add
 * up where they overlap, so each one stays dim enough for dense regions to keep their color
 */
function fadingColors(colors: Float32Array, length: number): Float32Array {
  const faded = new Float32Array(colors.length * length)
  for (let age = 0; age < length; age++) {
    const brightness = 0.2 * (1 - age / length) ** 2
    faded.set(
      colors.map((channel) => channel * brightness),
      age * colors.length,
    )
  }
  return faded
}

/** surface whose vertices move, visible from both sides. Casts and receives shadows */
export class MeshView implements ObjectView {
  readonly object: Mesh<BufferGeometry, MeshStandardMaterial>
  readonly grabbable: boolean
  private readonly positions: BufferAttribute

  constructor({ vertexCount, triangles, material, grabbable }: Description.Mesh) {
    this.grabbable = grabbable
    this.positions = new BufferAttribute(new Float32Array(3 * vertexCount), 3).setUsage(
      DynamicDrawUsage,
    )
    const geometry = new BufferGeometry()
      .setAttribute('position', this.positions)
      .setIndex([...triangles])
    this.object = new Mesh(geometry, createMaterial(material, DoubleSide))
    this.object.castShadow = true
    this.object.receiveShadow = true
    this.object.visible = false
  }

  update(floats: Float32Array): void {
    this.positions.array.set(floats)
    this.positions.needsUpdate = true
    // light needs normals, and picking with the pointer needs the bounds of the moved surface
    this.object.geometry.computeVertexNormals()
    this.object.geometry.computeBoundingSphere()
    this.object.visible = true
  }

  /**
   * Lets what lies behind and inside the surface show through it (like while the x-ray shows what
   * goes on in a cloth) or makes the surface opaque again
   */
  setTranslucent(translucent: boolean): void {
    const material = this.object.material
    material.transparent = translucent
    material.opacity = translucent ? TRANSLUCENT_OPACITY : 1
    // a translucent surface hides nothing behind it
    material.depthWrite = !translucent
    // a little behind the lines that lie on the surface so it doesn't tint them
    material.polygonOffset = translucent
    material.polygonOffsetFactor = 1
    material.polygonOffsetUnits = 1
    // transparency takes another shader program
    material.needsUpdate = true
  }

  dispose(): void {
    this.object.geometry.dispose()
    this.object.material.dispose()
  }
}

/** sphere that moves and changes its size. Casts and receives shadows */
export class SphereView implements ObjectView {
  readonly object: Mesh<SphereGeometry, MeshStandardMaterial>

  constructor({ material }: Description.Sphere) {
    this.object = new Mesh(new SphereGeometry(1, 64, 32), createMaterial(material, FrontSide))
    this.object.castShadow = true
    this.object.receiveShadow = true
    this.object.visible = false
  }

  /** @param floats x, y and z of the center, then the radius */
  update(floats: Float32Array): void {
    const [x = 0, y = 0, z = 0, radius = 0] = floats
    this.object.position.set(x, y, z)
    this.object.scale.setScalar(radius)
    this.object.visible = true
  }

  dispose(): void {
    this.object.geometry.dispose()
    this.object.material.dispose()
  }
}

/** the ground: a dark disk with a grid on it, facing up, receives shadows */
export class FloorView implements ObjectView {
  readonly object = new Group()
  private readonly disk: Mesh<CircleGeometry, MeshStandardMaterial>
  private readonly grid: GridHelper

  /** @param up unit vector that points up in the scene */
  constructor({ radius }: Description.Floor, up: Vector3) {
    // laid out with y pointing up, then turned so y points up in the scene
    this.disk = new Mesh(
      new CircleGeometry(radius, 64),
      new MeshStandardMaterial({ color: 0x161b22, roughness: 1 }),
    )
    this.disk.rotation.x = -Math.PI / 2
    this.disk.receiveShadow = true
    this.grid = new GridHelper(2 * radius, 32, 0x3d444d, 0x21262d)
    // just above the disk so the two don't flicker
    this.grid.position.y = radius / 4000
    this.object.add(this.disk, this.grid)
    this.object.quaternion.setFromUnitVectors(Y_AXIS, up)
  }

  update(): void {
    // the floor doesn't move
  }

  dispose(): void {
    this.disk.geometry.dispose()
    this.disk.material.dispose()
    this.grid.dispose()
  }
}

/**
 * Straight lines, each colored by its value along a color scale. Directed lines brighten from start
 * to end so they point the way arrows do
 */
export class LinesView implements ObjectView {
  readonly object: LineSegments<BufferGeometry, LineBasicMaterial>
  private readonly capacity: number
  private readonly scale: ColorRamp
  private readonly directed: boolean
  private readonly positions: BufferAttribute
  private readonly colors: BufferAttribute
  private readonly color = new Color()

  constructor({ capacity, scale, directed }: Description.Lines) {
    this.capacity = capacity
    this.scale = new ColorRamp(scale)
    this.directed = directed
    // two vertices per line, each with three coordinates and three color channels
    this.positions = new BufferAttribute(new Float32Array(6 * capacity), 3).setUsage(
      DynamicDrawUsage,
    )
    this.colors = new BufferAttribute(new Float32Array(6 * capacity), 3).setUsage(DynamicDrawUsage)
    const geometry = new BufferGeometry()
      .setAttribute('position', this.positions)
      .setAttribute('color', this.colors)
    geometry.setDrawRange(0, 0)
    this.object = new LineSegments(
      geometry,
      new LineBasicMaterial({ vertexColors: true, toneMapped: false }),
    )
    // the bounding volume would be computed once, from the initial positions at the origin
    this.object.frustumCulled = false
    this.object.visible = false
  }

  /** @param floats number of lines, then start, end and value of every line */
  update(floats: Float32Array): void {
    const count = shownCount(floats[0], this.capacity)
    const { array: positions } = this.positions
    const { array: colors } = this.colors
    for (let line = 0; line < count; line++) {
      const first = 1 + FLOATS_PER_LINE * line
      positions.set(floats.subarray(first, first + 6), 6 * line)
      this.scale.colorOf(floats[first + 6] ?? Number.NaN, this.color).toArray(colors, 6 * line + 3)
      if (this.directed) {
        this.color.multiplyScalar(DIRECTED_START_BRIGHTNESS)
      }
      this.color.toArray(colors, 6 * line)
    }
    this.positions.needsUpdate = true
    this.colors.needsUpdate = true
    this.object.geometry.setDrawRange(0, 2 * count)
    this.object.visible = true
  }

  dispose(): void {
    this.object.geometry.dispose()
    this.object.material.dispose()
  }
}

/** cubes drawn as their twelve edges, like the cells of an octree */
export class CubesView implements ObjectView {
  readonly object: LineSegments<BufferGeometry, LineBasicMaterial>
  private readonly capacity: number
  private readonly positions: BufferAttribute

  constructor({ capacity, color }: Description.Cubes) {
    this.capacity = capacity
    // eight corners per cube, every cube connects them in the same pattern
    this.positions = new BufferAttribute(new Float32Array(3 * 8 * capacity), 3).setUsage(
      DynamicDrawUsage,
    )
    const edges = Array.from({ length: capacity }, (_, cube) =>
      CUBE_EDGES.map((corner) => 8 * cube + corner),
    ).flat()
    const geometry = new BufferGeometry().setAttribute('position', this.positions).setIndex(edges)
    geometry.setDrawRange(0, 0)
    this.object = new LineSegments(
      geometry,
      new LineBasicMaterial({
        color: toColor(color),
        transparent: true,
        opacity: CUBE_OPACITY,
        depthWrite: false,
        toneMapped: false,
      }),
    )
    // the bounding volume would be computed once, from the initial positions at the origin
    this.object.frustumCulled = false
    this.object.visible = false
  }

  /** @param floats number of cubes, then center and half the edge of every cube */
  update(floats: Float32Array): void {
    const count = shownCount(floats[0], this.capacity)
    const { array: positions } = this.positions
    for (let cube = 0; cube < count; cube++) {
      const first = 1 + FLOATS_PER_CUBE * cube
      const [x = 0, y = 0, z = 0, half = 0] = floats.subarray(first, first + 4)
      for (let corner = 0; corner < 8; corner++) {
        const i = 3 * (8 * cube + corner)
        positions[i] = corner & 1 ? x + half : x - half
        positions[i + 1] = corner & 2 ? y + half : y - half
        positions[i + 2] = corner & 4 ? z + half : z - half
      }
    }
    this.positions.needsUpdate = true
    this.object.geometry.setDrawRange(0, CUBE_EDGES.length * count)
    this.object.visible = true
  }

  dispose(): void {
    this.object.geometry.dispose()
    this.object.material.dispose()
  }
}

/**
 * How many shapes an object with room for the given number of them shows: the number at the start
 * of its floats within its capacity, or none if that's not a valid number
 */
function shownCount(count: number | undefined, capacity: number): number {
  return count !== undefined && Number.isInteger(count) && count > 0 ? Math.min(count, capacity) : 0
}

/**
 * Colors for the values of a color scale. A value between two colors blends them in sRGB like the
 * legend of the scale does, a value beyond the ends takes the color of the nearer end and an
 * unknown value takes the first color
 */
class ColorRamp {
  /** channels of every color of the scale in sRGB, from 0 to 1 */
  private readonly channels: readonly (readonly [number, number, number])[]
  private readonly min: number
  private readonly max: number

  constructor({ colors, min, max }: Description.ColorScale) {
    this.channels = colors.map(({ red, green, blue }) => [red / 255, green / 255, blue / 255])
    this.min = min
    this.max = max
  }

  /** sets the color of a value into the target, in the working color space of three.js */
  colorOf(value: number, target: Color): Color {
    const share = Number.isNaN(value)
      ? 0
      : MathUtils.clamp((value - this.min) / (this.max - this.min), 0, 1)
    // the colors spread evenly over the range, so a share lies between the colors below and above
    const position = share * (this.channels.length - 1)
    const below = Math.min(Math.floor(position), this.channels.length - 2)
    const weight = position - below
    const [r0 = 0, g0 = 0, b0 = 0] = this.channels[below] ?? []
    const [r1 = 0, g1 = 0, b1 = 0] = this.channels[below + 1] ?? []
    return target.setRGB(
      r0 + (r1 - r0) * weight,
      g0 + (g1 - g0) * weight,
      b0 + (b1 - b0) * weight,
      SRGBColorSpace,
    )
  }
}

function createMaterial({ color, finish }: Description.Material, side: Side): MeshStandardMaterial {
  switch (finish) {
    case 'MATTE':
      return new MeshStandardMaterial({ color: toColor(color), roughness: 1, side })
    case 'GLOSSY':
      return new MeshStandardMaterial({
        color: toColor(color),
        roughness: 0.4,
        metalness: 0.1,
        side,
      })
    case 'FABRIC':
      return new MeshPhysicalMaterial({
        color: toColor(color),
        roughness: 0.8,
        sheen: 1,
        sheenRoughness: 0.5,
        sheenColor: lighter(color),
        side,
      })
  }
}

/** scene color into the working color space of three.js */
function toColor({ red, green, blue }: Description.Color): Color {
  return new Color().setRGB(red / 255, green / 255, blue / 255, SRGBColorSpace)
}

/** lighter shade of a scene color, halfway to white in sRGB */
function lighter({ red, green, blue }: Description.Color): Color {
  return toColor({ red: (red + 255) / 2, green: (green + 255) / 2, blue: (blue + 255) / 2 })
}
