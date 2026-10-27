import {
  BufferAttribute,
  BufferGeometry,
  Color,
  Points,
  PointsMaterial,
  Vector3,
  type Camera,
  type DataTexture,
} from 'three'

import { radialTexture } from './textures'
import type { PointCloudView } from './views'

export interface SelectListener {
  /** the pointer selected a point, numbered across all point clouds of the scene */
  select(point: number): void
  deselect(): void
}

export interface SelectableCloud {
  readonly view: PointCloudView
  /** number of the cloud's first point among the points of all point clouds */
  readonly firstPoint: number
}

/** farthest a point may lie from a click on the screen to be selected, in CSS pixels */
const PICK_RADIUS = 8

/** farthest the pointer may move between pressing and releasing in a click, in CSS pixels */
const CLICK_TOLERANCE = 4

/** size of the ring that marks the selected point, in pixels */
const MARKER_SIZE = 20

/** a yellow that stands out against blue, violet and pink points */
const MARKER_COLOR = 0xffd33d

/** texels along each edge of the ring texture */
const RING_TEXTURE_SIZE = 64

/** as a share of half the size of the marker */
const RING_RADIUS = 0.8

/** distance from the radius at which the ring fades out, as a share of half the marker size */
const RING_HALF_WIDTH = 0.12

/**
 * Lets the pointer select a point of the selectable point clouds by clicking it, and marks the
 * selected point with a ring. A click is a press and release of the primary button in about the
 * same place, so dragging still turns the camera. A click beside every point lets go of the
 * selected one
 */
export class PointSelector {
  /** ring around the selected point, the stage adds it to its scene */
  readonly marker: Points<BufferGeometry, PointsMaterial>
  private readonly markerPosition = new BufferAttribute(new Float32Array(3), 3)
  private readonly projected = new Vector3()
  private pressed:
    { readonly pointerId: number; readonly x: number; readonly y: number } | undefined
  private selected: { readonly view: PointCloudView; readonly index: number } | undefined

  constructor(
    private readonly element: HTMLElement,
    private readonly camera: Camera,
    private readonly clouds: readonly SelectableCloud[],
    private readonly listener: SelectListener,
  ) {
    this.marker = new Points(
      new BufferGeometry().setAttribute('position', this.markerPosition),
      new PointsMaterial({
        size: MARKER_SIZE,
        sizeAttenuation: false,
        color: new Color(MARKER_COLOR),
        map: ringTexture(),
        transparent: true,
        // the ring shows in front of the points that crowd around the selected one
        depthTest: false,
        toneMapped: false,
      }),
    )
    this.marker.frustumCulled = false
    this.marker.renderOrder = 1
    this.marker.visible = false
    element.addEventListener('pointerdown', this.onPointerDown)
    element.addEventListener('pointerup', this.onPointerUp)
  }

  /** moves the ring to where the latest frame put the selected point */
  update(): void {
    if (this.selected) {
      const { view, index } = this.selected
      this.markerPosition.array.set(view.coordinates.subarray(3 * index, 3 * index + 3))
      this.markerPosition.needsUpdate = true
      this.marker.visible = view.object.visible
    }
  }

  dispose(): void {
    this.element.removeEventListener('pointerdown', this.onPointerDown)
    this.element.removeEventListener('pointerup', this.onPointerUp)
    this.marker.geometry.dispose()
    this.marker.material.map?.dispose()
    this.marker.material.dispose()
  }

  private readonly onPointerDown = (event: PointerEvent): void => {
    this.pressed =
      event.isPrimary && event.button === 0
        ? { pointerId: event.pointerId, x: event.clientX, y: event.clientY }
        : undefined
  }

  private readonly onPointerUp = (event: PointerEvent): void => {
    const { pressed } = this
    this.pressed = undefined
    if (
      pressed?.pointerId === event.pointerId &&
      Math.hypot(event.clientX - pressed.x, event.clientY - pressed.y) <= CLICK_TOLERANCE
    ) {
      this.click(event.clientX, event.clientY)
    }
  }

  /** selects the point closest to a click, or lets go of the selected point if none is close */
  private click(clientX: number, clientY: number): void {
    const nearest = this.nearestPoint(clientX, clientY)
    if (nearest) {
      this.selected = { view: nearest.cloud.view, index: nearest.index }
      this.update()
      this.listener.select(nearest.cloud.firstPoint + nearest.index)
    } else if (this.selected) {
      this.selected = undefined
      this.marker.visible = false
      this.listener.deselect()
    }
  }

  /**
   * Point closest to a position on the screen within the pick radius, among the points that are
   * shown and in front of the camera
   */
  private nearestPoint(
    clientX: number,
    clientY: number,
  ): { readonly cloud: SelectableCloud; readonly index: number } | undefined {
    const bounds = this.element.getBoundingClientRect()
    let nearest: { readonly cloud: SelectableCloud; readonly index: number } | undefined
    let nearestDistanceSquared = PICK_RADIUS * PICK_RADIUS
    for (const cloud of this.clouds) {
      if (!cloud.view.object.visible) {
        continue
      }
      const { coordinates } = cloud.view
      for (let index = 0; 3 * index < coordinates.length; index++) {
        // normalized device coordinates, from -1 to 1 within the view of the camera
        const { x, y, z } = this.projected.fromArray(coordinates, 3 * index).project(this.camera)
        if (Math.abs(z) > 1) {
          continue
        }
        const dx = bounds.left + ((x + 1) / 2) * bounds.width - clientX
        const dy = bounds.top + ((1 - y) / 2) * bounds.height - clientY
        const distanceSquared = dx * dx + dy * dy
        if (distanceSquared < nearestDistanceSquared) {
          nearest = { cloud, index }
          nearestDistanceSquared = distanceSquared
        }
      }
    }
    return nearest
  }
}

/**
 * Texture of the ring: white in a circle near the edges, from where the opacity falls off to the
 * inside and the outside, transparent elsewhere
 */
function ringTexture(): DataTexture {
  return radialTexture(RING_TEXTURE_SIZE, (distance) =>
    Math.max(0, 1 - Math.abs(distance - RING_RADIUS) / RING_HALF_WIDTH),
  )
}
