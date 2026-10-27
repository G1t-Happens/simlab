import {
  Color,
  DirectionalLight,
  Group,
  HemisphereLight,
  PerspectiveCamera,
  Scene,
  Vector3,
} from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'

import { frameFloats, sceneFrameFloats, sceneXrayFloats } from '@/api/scene'
import type * as Description from '@/api/scene'

import { PointSelector, type SelectableCloud, type SelectListener } from './PointSelector'
import { VertexDragger, type DragListener } from './VertexDragger'
import { createView, MeshView, PointCloudView, type ObjectView } from './views'

const BACKGROUND = new Color(0x0d1117)

const Y_AXIS = new Vector3(0, 1, 0)

/** how far the mouse wheel zooms in and out, relative to the distance the camera starts at */
const ZOOM_RANGE = 2.5

/** learns what the pointer does with the scene: drags the vertices of a mesh and selects points */
export type PointerListener = DragListener & SelectListener

/** view of an object, with the place of the floats the object takes up in every frame */
interface Part {
  readonly view: ObjectView
  /** index of the object's first float in a frame */
  readonly offset: number
  readonly floats: number
}

/**
 * The objects of a scene with a camera, controls and lights to show them. Frames move the objects
 * and show the x-ray objects if they hold their floats. The pointer turns the camera around its
 * target, drags the vertices of a grabbable mesh and selects the points of selectable point clouds
 */
export class Stage {
  /** the objects and the lights, as three.js renders them */
  readonly scene = new Scene()
  readonly camera: PerspectiveCamera
  /** objects of the scene glow, which takes bloom to let their light bleed around them */
  readonly glowing: boolean
  private readonly controls: OrbitControls
  private readonly dragger: VertexDragger | undefined
  private readonly selector: PointSelector | undefined
  /** objects the stage renders, in the order of their floats in frames */
  private readonly parts: readonly Part[]
  /** x-ray objects, their floats follow those of the objects in frames that show it */
  private readonly xrayParts: readonly Part[]
  private readonly frameLength: number
  private readonly xrayLength: number
  /** whether the latest frame showed the x-ray */
  private xrayShown = false
  private readonly keyLight = new DirectionalLight(0xffffff, 2.4)
  /** lifts the sides the key light doesn't reach, without casting shadows */
  private readonly fillLight = new DirectionalLight(0xc9d1d9, 0.8)

  /**
   * @param element where the pointer turns the camera, drags vertices and selects points
   * @param listener learns what the pointer does with the vertices of a grabbable mesh and with the
   *     points of selectable point clouds
   */
  constructor(description: Description.Scene, element: HTMLElement, listener: PointerListener) {
    const { position, target, up, orbiting } = description.camera
    const upward = toVector(up).normalize()
    const distance = toVector(position).distanceTo(toVector(target))
    // near and far enough for everything the camera sees from within its zoom range
    this.camera = new PerspectiveCamera(40, 1, distance / 50, distance * 10)
    this.camera.up.copy(upward)
    this.camera.position.copy(toVector(position))

    const floor = description.objects.find((object) => object.kind === 'Floor')
    this.frameLength = sceneFrameFloats(description)
    this.xrayLength = sceneXrayFloats(description)
    this.parts = createParts(description.objects, upward, 0)
    this.xrayParts = createParts(description.xray, upward, this.frameLength)
    this.glowing = description.objects.some(
      (object) => object.kind === 'PointCloud' && object.glowing,
    )
    this.scene.background = BACKGROUND
    this.scene.add(
      this.lights(upward, floor),
      ...[...this.parts, ...this.xrayParts].map(({ view }) => view.object),
    )

    const views = this.parts.map(({ view }) => view)
    const grabbable = views.find(isGrabbable)
    // created first, so the dragger keeps a press on the mesh from turning the camera
    this.dragger = grabbable
      ? new VertexDragger(element, this.camera, grabbable.object, listener)
      : undefined
    const selectable = selectableClouds(views)
    this.selector =
      selectable.length > 0
        ? new PointSelector(element, this.camera, selectable, listener)
        : undefined
    if (this.selector) {
      this.scene.add(this.selector.marker)
    }
    this.controls = new OrbitControls(this.camera, element)
    this.controls.target.copy(toVector(target))
    this.controls.enableDamping = true
    this.controls.autoRotate = orbiting
    this.controls.autoRotateSpeed = 0.5
    this.controls.minDistance = distance / ZOOM_RANGE
    this.controls.maxDistance = distance * ZOOM_RANGE
    if (floor) {
      // the camera stays above the floor
      this.controls.maxPolarAngle = Math.PI / 2 - 0.05
    }
  }

  /**
   * Shows the state of the objects in a frame: the floats of one object after another, followed by
   * those of the x-ray objects if the frame shows it. Surfaces turn translucent meanwhile so the
   * x-ray shows through them
   *
   * @throws RangeError if the frame holds another number of floats than the scene lays out
   */
  update(frame: Float32Array): void {
    const xrayShown = this.xrayLength > 0 && frame.length === this.frameLength + this.xrayLength
    if (!xrayShown && frame.length !== this.frameLength) {
      const withXray =
        this.xrayLength > 0
          ? `, or ${String(this.frameLength + this.xrayLength)} with the x-ray`
          : ''
      throw new RangeError(
        `Expected a frame of ${String(this.frameLength)} floats${withXray}, ` +
          `but got ${String(frame.length)}`,
      )
    }
    const parts = xrayShown ? [...this.parts, ...this.xrayParts] : this.parts
    for (const { view, offset, floats } of parts) {
      view.update(frame.subarray(offset, offset + floats))
    }
    if (xrayShown !== this.xrayShown) {
      this.showXray(xrayShown)
    }
    this.selector?.update()
  }

  setAspect(aspect: number): void {
    this.camera.aspect = aspect
    this.camera.updateProjectionMatrix()
  }

  /** before each render: moves the camera as the pointer and the orbit ask, and passes drags on */
  animate(): void {
    this.dragger?.flush()
    this.controls.update()
  }

  /** releases a held vertex, stops listening to the pointer and frees the resources */
  dispose(): void {
    this.dragger?.dispose()
    this.selector?.dispose()
    this.controls.dispose()
    for (const { view } of [...this.parts, ...this.xrayParts]) {
      view.dispose()
    }
    this.keyLight.dispose()
    this.fillLight.dispose()
  }

  /**
   * Shows the x-ray objects (the next frames update them) and lets them show through the surfaces,
   * or hides them again
   */
  private showXray(shown: boolean): void {
    this.xrayShown = shown
    for (const { view } of this.xrayParts) {
      view.object.visible = shown
    }
    for (const { view } of this.parts) {
      if (view instanceof MeshView) {
        view.setTranslucent(shown)
      }
    }
  }

  /**
   * Lights shining from above, as up points: a sky that fades into the ground, a key light that
   * casts shadows onto the floor if there is one, and a fill light
   */
  private lights(up: Vector3, floor: Description.Floor | undefined): Group {
    const lights = new Group()
    // placed with y pointing up, then turned so y points up in the scene
    this.keyLight.position.set(3, 6, 2)
    this.fillLight.position.set(4, 2, 3)
    lights.add(this.keyLight, this.fillLight, new HemisphereLight(0xb1c5e0, 0x3d444d, 0.8))
    lights.quaternion.setFromUnitVectors(Y_AXIS, up)
    if (floor) {
      castShadows(this.keyLight, floor.radius)
    }
    return lights
  }
}

/**
 * Views of the scene objects the stage renders, with the places of their floats in frames. Readouts
 * show beside the scene, not in it
 *
 * @param up unit vector that points up in the scene
 * @param start index of the first float of the objects in a frame
 */
function createParts(
  objects: readonly Description.SceneObject[],
  up: Vector3,
  start: number,
): Part[] {
  const parts: Part[] = []
  let offset = start
  for (const object of objects) {
    const floats = frameFloats(object)
    if (object.kind !== 'Readouts') {
      parts.push({ view: createView(object, up), offset, floats })
    }
    offset += floats
  }
  return parts
}

/**
 * Point clouds whose points the pointer can select, with the numbers of their first points among
 * the points of all point clouds
 */
function selectableClouds(views: readonly ObjectView[]): SelectableCloud[] {
  const clouds: SelectableCloud[] = []
  let firstPoint = 0
  for (const view of views) {
    if (view instanceof PointCloudView) {
      if (view.selectable) {
        clouds.push({ view, firstPoint })
      }
      firstPoint += view.coordinates.length / 3
    }
  }
  return clouds
}

/** lets a light cast shadows onto a floor of the given radius and everything above it */
function castShadows(light: DirectionalLight, radius: number): void {
  // twice the radius away the light sees everything above the floor in front of it
  light.position.setLength(2 * radius)
  light.castShadow = true
  light.shadow.mapSize.set(2048, 2048)
  light.shadow.camera.left = -radius
  light.shadow.camera.right = radius
  light.shadow.camera.top = radius
  light.shadow.camera.bottom = -radius
  light.shadow.normalBias = radius / 200
}

function isGrabbable(view: ObjectView): view is MeshView {
  return view instanceof MeshView && view.grabbable
}

function toVector({ x, y, z }: Description.Vector3): Vector3 {
  return new Vector3(x, y, z)
}
