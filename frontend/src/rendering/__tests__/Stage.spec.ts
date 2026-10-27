import {
  DirectionalLight,
  Group,
  HemisphereLight,
  LineSegments,
  Mesh,
  PerspectiveCamera,
  Points,
  Scene,
  Vector3,
} from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import { describe, expect, it, vi } from 'vitest'

import {
  CLOTH_FRAME,
  CLOTH_XRAY,
  clothScene,
  pointsScene,
  SELECTABLE_FRAME,
  selectableScene,
  starsScene,
} from '@/__tests__/scenes'
import type * as Description from '@/api/scene'

import { Stage, type PointerListener } from '../Stage'
import { VertexDragger } from '../VertexDragger'

vi.mock('../VertexDragger')

const element = document.createElement('div')

const listener: PointerListener = {
  grab: vi.fn<PointerListener['grab']>(),
  drag: vi.fn<PointerListener['drag']>(),
  release: vi.fn<PointerListener['release']>(),
  select: vi.fn<PointerListener['select']>(),
  deselect: vi.fn<PointerListener['deselect']>(),
}

/**
 * An element of 100 by 100 pixels of its own, which no stage of another test listens to, for the
 * pointer to click
 */
function clickableElement(): HTMLElement {
  const clickable = document.createElement('div')
  vi.spyOn(clickable, 'getBoundingClientRect').mockReturnValue(new DOMRect(0, 0, 100, 100))
  // the controls capture the pointer, which jsdom can't
  clickable.setPointerCapture = vi.fn<HTMLElement['setPointerCapture']>()
  clickable.releasePointerCapture = vi.fn<HTMLElement['releasePointerCapture']>()
  return clickable
}

/** clicks the middle of an element, where the camera looks at its target */
function clickTheMiddle(clickable: HTMLElement): void {
  for (const type of ['pointerdown', 'pointerup']) {
    clickable.dispatchEvent(
      new PointerEvent(type, { clientX: 50, clientY: 50, pointerId: 1, isPrimary: true }),
    )
  }
}

/** points a scene shows, the ring that marks the selected one among them */
function pointsIn(scene: Scene): Points[] {
  const points: Points[] = []
  scene.traverse((object) => {
    if (object instanceof Points) {
      points.push(object)
    }
  })
  return points
}

/** lets a stage animate like before every render, returns the scene and the camera it shows */
function animate(stage: Stage): { scene: Scene; camera: PerspectiveCamera } {
  stage.animate()
  return { scene: stage.scene, camera: stage.camera }
}

/** meshes of the cloth scene: the surface, then the sphere */
function meshes(scene: Scene): { surface: Mesh; sphere: Mesh } {
  const [surface, sphere] = scene.children.filter(
    (object): object is Mesh => object instanceof Mesh,
  )
  if (surface === undefined || sphere === undefined) {
    throw new Error('The scene shows no surface and sphere')
  }
  return { surface, sphere }
}

/** lines or cubes of the x-ray of a scene, the only lines in it outside of groups */
function xrayLines(scene: Scene): LineSegments {
  const found = scene.children.find(
    (object): object is LineSegments => object instanceof LineSegments,
  )
  if (found === undefined) {
    throw new Error('The scene shows no lines')
  }
  return found
}

function lights(scene: Scene): Group {
  const found = scene.children.find(
    (object): object is Group =>
      object instanceof Group && object.children.some((light) => light instanceof HemisphereLight),
  )
  if (found === undefined) {
    throw new Error('The scene has no lights')
  }
  return found
}

function createdDragger(): VertexDragger {
  const dragger = vi.mocked(VertexDragger).mock.instances[0]
  if (dragger === undefined) {
    throw new Error('The stage created no dragger')
  }
  return dragger
}

function toVector({ x, y, z }: Description.Vector3): Vector3 {
  return new Vector3(x, y, z)
}

describe('Stage', () => {
  it('shows the scene from where its camera starts, looking at its target', () => {
    const position = toVector(clothScene.camera.position)
    const target = toVector(clothScene.camera.target)
    const stage = new Stage(clothScene, element, listener)

    animate(stage)
    const { camera } = animate(stage)

    expect(camera.position.distanceTo(position)).toBeCloseTo(0)
    expect(camera.up).toEqual(toVector(clothScene.camera.up))
    const sight = target.sub(position).normalize()
    expect(camera.getWorldDirection(new Vector3()).distanceTo(sight)).toBeCloseTo(0)
  })

  it('circles its target by itself if the scene asks for it', () => {
    const target = toVector(pointsScene.camera.target)
    const stage = new Stage(pointsScene, element, listener)
    const start = animate(stage).camera.position.clone()

    const { camera } = animate(stage)

    expect(camera.position.distanceTo(start)).toBeGreaterThan(0)
    expect(camera.position.distanceTo(target)).toBeCloseTo(start.distanceTo(target))
  })

  it('fits the camera to the ratio of width to height of the canvas', () => {
    const stage = new Stage(clothScene, element, listener)

    stage.setAspect(2)

    expect(animate(stage).camera.aspect).toBe(2)
  })

  it('lights the scene from above, as up points', () => {
    const { scene } = animate(new Stage(pointsScene, element, listener))

    const up = new Vector3(0, 1, 0).applyQuaternion(lights(scene).quaternion)
    expect(up.distanceTo(toVector(pointsScene.camera.up))).toBeCloseTo(0)
  })

  it('casts shadows onto a floor, if there is one', () => {
    const castsShadows = (description: Description.Scene) =>
      lights(animate(new Stage(description, element, listener)).scene).children.some(
        (light) => light instanceof DirectionalLight && light.castShadow,
      )

    expect(castsShadows(clothScene)).toBe(true)
    expect(castsShadows(pointsScene)).toBe(false)
  })

  it('moves each object of the scene with its floats from every frame', () => {
    const stage = new Stage(clothScene, element, listener)

    stage.update(Float32Array.from(CLOTH_FRAME))

    const { surface, sphere } = meshes(animate(stage).scene)
    expect(surface.geometry.getAttribute('position').array).toEqual(
      Float32Array.from(CLOTH_FRAME.slice(0, 12)),
    )
    expect(sphere.position.toArray()).toEqual([0, 1, -1])
    expect(sphere.scale.x).toBe(0.5)
  })

  it('rejects a frame that does not fit the scene', () => {
    const stage = new Stage(clothScene, element, listener)

    expect(() => {
      stage.update(new Float32Array(30))
    }).toThrow(new RangeError('Expected a frame of 16 floats, or 31 with the x-ray, but got 30'))
    expect(() => {
      new Stage(pointsScene, element, listener).update(new Float32Array(7))
    }).toThrow(new RangeError('Expected a frame of 6 floats, but got 7'))
  })

  it('hides the x-ray while frames do not show it', () => {
    const stage = new Stage(clothScene, element, listener)

    stage.update(Float32Array.from(CLOTH_FRAME))

    const { scene } = animate(stage)
    expect(xrayLines(scene).visible).toBe(false)
    expect(meshes(scene).surface.material).toMatchObject({ transparent: false, opacity: 1 })
  })

  it('shows the x-ray with the floats after those of the objects, through the surfaces', () => {
    const stage = new Stage(clothScene, element, listener)

    stage.update(Float32Array.from([...CLOTH_FRAME, ...CLOTH_XRAY]))

    const { scene } = animate(stage)
    const { surface, sphere } = meshes(scene)
    const lines = xrayLines(scene)
    expect(lines.visible).toBe(true)
    expect(lines.geometry.getAttribute('position').array).toEqual(
      Float32Array.from([...CLOTH_XRAY.slice(1, 7), ...CLOTH_XRAY.slice(8, 14)]),
    )
    expect(surface.geometry.getAttribute('position').array).toEqual(
      Float32Array.from(CLOTH_FRAME.slice(0, 12)),
    )
    expect(surface.material).toMatchObject({ transparent: true, depthWrite: false })
    expect(sphere.material).toMatchObject({ transparent: false })
  })

  it('hides the x-ray again once frames no longer show it', () => {
    const stage = new Stage(clothScene, element, listener)
    stage.update(Float32Array.from([...CLOTH_FRAME, ...CLOTH_XRAY]))

    stage.update(Float32Array.from(CLOTH_FRAME))

    const { scene } = animate(stage)
    expect(xrayLines(scene).visible).toBe(false)
    expect(meshes(scene).surface.material).toMatchObject({ transparent: false, depthWrite: true })
  })

  it('shows no readouts in the scene, but skips their floats in frames', () => {
    const stage = new Stage(selectableScene, element, listener)

    stage.update(Float32Array.from(SELECTABLE_FRAME))

    const [points] = pointsIn(animate(stage).scene)
    expect(points?.geometry.getAttribute('position').array).toEqual(
      Float32Array.from(SELECTABLE_FRAME.slice(0, 6)),
    )
  })

  it('lets the pointer select the points of selectable clouds, numbered across all clouds', () => {
    const [selectable] = selectableScene.objects
    if (selectable?.kind !== 'PointCloud') {
      throw new Error('The selectable scene starts with its points')
    }
    const description: Description.Scene = {
      ...selectableScene,
      objects: [{ ...selectable, selectable: false }, selectable],
    }
    const clickable = clickableElement()
    const stage = new Stage(description, clickable, listener)
    // the points of the first cloud lie at the target of the camera too, but can't be selected
    stage.update(Float32Array.of(0, 0, 0, 0, 0, 0, 5, 0, 5, 0, 0, 0))
    animate(stage).camera.updateMatrixWorld()

    clickTheMiddle(clickable)

    expect(listener.select).toHaveBeenCalledExactlyOnceWith(3)
    expect(pointsIn(stage.scene).filter((points) => points.visible)).toHaveLength(3)
  })

  it('lets the pointer select nothing in a scene without selectable points', () => {
    const clickable = clickableElement()
    const stage = new Stage(pointsScene, clickable, listener)
    stage.update(new Float32Array(6))
    animate(stage).camera.updateMatrixWorld()

    clickTheMiddle(clickable)

    expect(listener.select).not.toHaveBeenCalled()
    expect(pointsIn(stage.scene)).toHaveLength(1)
  })

  it('lets the pointer drag the vertices of a grabbable mesh, flushed whenever it animates', () => {
    const stage = new Stage(clothScene, element, listener)

    const { scene, camera } = animate(stage)

    expect(VertexDragger).toHaveBeenCalledExactlyOnceWith(
      element,
      camera,
      meshes(scene).surface,
      listener,
    )
    expect(createdDragger().flush).toHaveBeenCalledOnce()
  })

  it('lets the pointer grab nothing in a scene without a grabbable mesh', () => {
    animate(new Stage(pointsScene, element, listener))

    expect(VertexDragger).not.toHaveBeenCalled()
  })

  it('glows if points of the scene glow', () => {
    expect(new Stage(starsScene, element, listener).glowing).toBe(true)
    expect(new Stage(pointsScene, element, listener).glowing).toBe(false)
    expect(new Stage(clothScene, element, listener).glowing).toBe(false)
  })

  it('frees the objects of the x-ray when disposed', () => {
    const stage = new Stage(starsScene, element, listener)
    const disposeCubes = vi.spyOn(xrayLines(stage.scene).geometry, 'dispose')

    stage.dispose()

    expect(disposeCubes).toHaveBeenCalledOnce()
  })

  it('lets go of the pointer when disposed', () => {
    const disposeControls = vi.spyOn(OrbitControls.prototype, 'dispose')
    const stage = new Stage(clothScene, element, listener)

    stage.dispose()

    expect(createdDragger().dispose).toHaveBeenCalledOnce()
    expect(disposeControls).toHaveBeenCalledOnce()
  })
})
