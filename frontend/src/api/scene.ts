import { isRecord } from './json'

/**
 * What a simulation shows, as its room describes it: the objects in view, the camera that shows
 * them, and the objects of its x-ray. Every frame holds the floats of one object after another in
 * the order of the objects (see {@link frameFloats}), frames that show the x-ray hold the floats of
 * its objects after those
 */
export interface Scene {
  readonly camera: Camera
  readonly objects: readonly SceneObject[]
  /**
   * Objects that show what goes on inside the simulation while the viewer looks through it, none if
   * it offers no x-ray. Neither readouts nor objects the pointer grabs or selects
   */
  readonly xray: readonly SceneObject[]
}

export interface Vector3 {
  readonly x: number
  readonly y: number
  readonly z: number
}

/** sRGB, channels from 0 to 255 */
export interface Color {
  readonly red: number
  readonly green: number
  readonly blue: number
}

/** how a surface reflects light: diffuse, shiny or with the sheen of cloth */
export type Finish = 'MATTE' | 'GLOSSY' | 'FABRIC'

export interface Material {
  readonly color: Color
  readonly finish: Finish
}

/** Where the camera starts out. The pointer turns it around its target, the mouse wheel zooms */
export interface Camera {
  readonly position: Vector3
  readonly target: Vector3
  /** points up in the scene and stays up on the screen */
  readonly up: Vector3
  /** slowly circles its target by itself */
  readonly orbiting: boolean
}

/**
 * Points that move like particles or stars, drawn as dots with fading trails or as glowing stars.
 * Frames hold x, y and z of every point. The points of a scene are numbered across all of its
 * point clouds, in the order of the scene
 */
export interface PointCloud {
  readonly kind: 'PointCloud'
  readonly count: number
  /** the points in between blend from the first to the last color */
  readonly firstColor: Color
  readonly lastColor: Color
  /** number of the latest frames the trail of a point connects, 0 for no trails */
  readonly trailLength: number
  /** shine like stars, with light that adds up where they crowd and glows */
  readonly glowing: boolean
  /** the pointer can select a point, whose values the simulation then reports */
  readonly selectable: boolean
}

/** A surface of triangles whose vertices move, frames hold x, y and z of every vertex */
export interface Mesh {
  readonly kind: 'Mesh'
  readonly vertexCount: number
  /** three vertex indices per triangle, counterclockwise as seen from the front */
  readonly triangles: readonly number[]
  readonly material: Material
  readonly grabbable: boolean
}

/** A sphere that moves and changes its size, frames hold its center x, y, z, then its radius */
export interface Sphere {
  readonly kind: 'Sphere'
  readonly material: Material
}

/** The ground: a disk around the origin that faces up. Takes up no floats in frames */
export interface Floor {
  readonly kind: 'Floor'
  readonly radius: number
}

/** A number the simulation reports with every frame, like a coordinate of a particle */
export interface Readout {
  /** identifies the readout, like `xDot`. Lessons tie their symbols to readouts by name */
  readonly name: string
  /** short human-readable name, like a symbol */
  readonly label: string
  /** like `m/s`, empty for dimensionless readouts */
  readonly unit: string
}

/**
 * Numbers the simulation reports with every frame, shown beside the scene and in the formulas of
 * the lesson. Frames hold their values, NaN for unknown ones (like those of the selected point
 * while there's none)
 */
export interface Readouts {
  readonly kind: 'Readouts'
  readonly readouts: readonly Readout[]
}

/**
 * Colors for the values of a quantity, and what the legend explaining them says. The colors spread
 * evenly from the smallest to the largest value, a value in between blends the two colors next to
 * it and a value beyond the ends takes the color of the nearer end
 */
export interface ColorScale {
  /** the quantity in the legend, like a symbol */
  readonly label: string
  /** like `%`, empty for dimensionless values */
  readonly unit: string
  /** value of the first color */
  readonly min: number
  /** value of the last color */
  readonly max: number
  /** at least two, from the color of the smallest value to that of the largest */
  readonly colors: readonly Color[]
}

/** start and end coordinates plus the value */
export const FLOATS_PER_LINE = 7

/**
 * Straight lines between moving points, each colored by a value of its own. Frames hold the number
 * of lines they show, then start x, y, z, end x, y, z and the value of one line after another, as
 * many as the capacity. Those beyond the number are ignored
 */
export interface Lines {
  readonly kind: 'Lines'
  readonly capacity: number
  /** what the lines show, for the legend */
  readonly label: string
  readonly scale: ColorScale
  /** the lines point from their start to their end, like arrows */
  readonly directed: boolean
}

/** center coordinates and half the edge */
export const FLOATS_PER_CUBE = 4

/**
 * Cubes drawn as their edges, like the cells of an octree. Frames hold the number of cubes they
 * show, then center x, y, z and half the edge of one cube after another, as many as the capacity.
 * Those beyond the number are ignored
 */
export interface Cubes {
  readonly kind: 'Cubes'
  readonly capacity: number
  /** what the cubes show, for the legend */
  readonly label: string
  /** color of the edges */
  readonly color: Color
}

/** something the browser shows, told apart by its kind */
export type SceneObject = PointCloud | Mesh | Sphere | Floor | Readouts | Lines | Cubes

export function frameFloats(object: SceneObject): number {
  switch (object.kind) {
    case 'PointCloud':
      return 3 * object.count
    case 'Mesh':
      return 3 * object.vertexCount
    case 'Sphere':
      return 4
    case 'Floor':
      return 0
    case 'Readouts':
      return object.readouts.length
    case 'Lines':
      return 1 + FLOATS_PER_LINE * object.capacity
    case 'Cubes':
      return 1 + FLOATS_PER_CUBE * object.capacity
  }
}

/** floats in every frame of a scene that doesn't show the x-ray */
export function sceneFrameFloats(scene: Scene): number {
  return totalFloats(scene.objects)
}

/** floats the x-ray of a scene adds to the frames that show it */
export function sceneXrayFloats(scene: Scene): number {
  return totalFloats(scene.xray)
}

function totalFloats(objects: readonly SceneObject[]): number {
  return objects.reduce((sum, object) => sum + frameFloats(object), 0)
}

/** readouts in the order of their values in frames, none if the scene has none */
export function sceneReadouts(scene: Scene): readonly Readout[] {
  return scene.objects.find((object) => object.kind === 'Readouts')?.readouts ?? []
}

/** values of the readouts in a frame of a scene by name, NaN for unknown values */
export function readoutValues(scene: Scene, frame: Float32Array): Readonly<Record<string, number>> {
  let offset = 0
  for (const object of scene.objects) {
    if (object.kind === 'Readouts') {
      const start = offset
      return Object.fromEntries(
        object.readouts.map(({ name }, index) => [name, frame[start + index] ?? Number.NaN]),
      )
    }
    offset += frameFloats(object)
  }
  return {}
}

/** narrows parsed JSON to a scene whose objects are of known kinds and consistent */
export function isScene(value: unknown): value is Scene {
  return (
    isRecord(value) &&
    isCamera(value.camera) &&
    Array.isArray(value.objects) &&
    value.objects.every(isSceneObject) &&
    value.objects.filter((object) => object.kind === 'Readouts').length <= 1 &&
    Array.isArray(value.xray) &&
    value.xray.every((object) => isSceneObject(object) && !isAlwaysInView(object))
  )
}

/** readouts and objects the pointer grabs or selects are always in view */
function isAlwaysInView(object: SceneObject): boolean {
  switch (object.kind) {
    case 'Readouts':
      return true
    case 'Mesh':
      return object.grabbable
    case 'PointCloud':
      return object.selectable
    default:
      return false
  }
}

const FINISHES: readonly unknown[] = ['MATTE', 'GLOSSY', 'FABRIC'] satisfies Finish[]

function isSceneObject(value: unknown): value is SceneObject {
  if (!isRecord(value)) {
    return false
  }
  switch (value.kind) {
    case 'PointCloud':
      return (
        isCount(value.count) &&
        value.count > 0 &&
        isColor(value.firstColor) &&
        isColor(value.lastColor) &&
        isCount(value.trailLength) &&
        value.trailLength !== 1 &&
        typeof value.glowing === 'boolean' &&
        typeof value.selectable === 'boolean'
      )
    case 'Mesh':
      return (
        isCount(value.vertexCount) &&
        isTriangles(value.triangles, value.vertexCount) &&
        isMaterial(value.material) &&
        typeof value.grabbable === 'boolean'
      )
    case 'Sphere':
      return isMaterial(value.material)
    case 'Floor':
      return isFiniteNumber(value.radius) && value.radius > 0
    case 'Readouts':
      return isReadoutList(value.readouts)
    case 'Lines':
      return (
        isCount(value.capacity) &&
        value.capacity > 0 &&
        isNonEmptyString(value.label) &&
        isColorScale(value.scale) &&
        typeof value.directed === 'boolean'
      )
    case 'Cubes':
      return (
        isCount(value.capacity) &&
        value.capacity > 0 &&
        isNonEmptyString(value.label) &&
        isColor(value.color)
      )
    default:
      return false
  }
}

function isColorScale(value: unknown): value is ColorScale {
  return (
    isRecord(value) &&
    isNonEmptyString(value.label) &&
    typeof value.unit === 'string' &&
    isFiniteNumber(value.min) &&
    isFiniteNumber(value.max) &&
    value.min < value.max &&
    Array.isArray(value.colors) &&
    value.colors.length >= 2 &&
    value.colors.every(isColor)
  )
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value !== ''
}

/** at least one readout, each with a name of its own */
function isReadoutList(value: unknown): value is Readout[] {
  return (
    Array.isArray(value) &&
    value.length > 0 &&
    value.every(isReadout) &&
    new Set(value.map(({ name }) => name)).size === value.length
  )
}

function isReadout(value: unknown): value is Readout {
  return (
    isRecord(value) &&
    isNonEmptyString(value.name) &&
    isNonEmptyString(value.label) &&
    typeof value.unit === 'string'
  )
}

function isCamera(value: unknown): value is Camera {
  return (
    isRecord(value) &&
    isVector3(value.position) &&
    isVector3(value.target) &&
    isVector3(value.up) &&
    typeof value.orbiting === 'boolean'
  )
}

function isVector3(value: unknown): value is Vector3 {
  return (
    isRecord(value) && isFiniteNumber(value.x) && isFiniteNumber(value.y) && isFiniteNumber(value.z)
  )
}

function isMaterial(value: unknown): value is Material {
  return isRecord(value) && isColor(value.color) && FINISHES.includes(value.finish)
}

function isColor(value: unknown): value is Color {
  return isRecord(value) && isChannel(value.red) && isChannel(value.green) && isChannel(value.blue)
}

function isChannel(value: unknown): boolean {
  return isCount(value) && value <= 255
}

/** whole triangles of indices of existing vertices */
function isTriangles(value: unknown, vertexCount: number): value is number[] {
  return (
    Array.isArray(value) &&
    value.length > 0 &&
    value.length % 3 === 0 &&
    value.every((index) => isCount(index) && index < vertexCount)
  )
}

function isCount(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0
}

function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}
