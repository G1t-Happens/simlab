import {
  Plane,
  Raycaster,
  Vector2,
  Vector3,
  type Camera,
  type Intersection,
  type Mesh,
} from 'three'

export interface DragListener {
  grab(vertex: number, position: Vector3): void
  drag(vertex: number, position: Vector3): void
  release(vertex: number): void
}

/**
 * Lets the pointer grab the vertex of a mesh closest to where it presses the mesh, and drag the
 * vertex within the plane through it that faces the camera.
 *
 * Create the dragger before other pointer listeners on the element (like orbit controls), it keeps
 * a press on the mesh from reaching them
 */
export class VertexDragger {
  private readonly raycaster = new Raycaster()
  private readonly pointer = new Vector2()
  private readonly plane = new Plane()
  private readonly offset = new Vector3()
  private readonly target = new Vector3()
  private held: { readonly vertex: number; readonly pointerId: number } | undefined
  private moved = false

  constructor(
    private readonly element: HTMLElement,
    private readonly camera: Camera,
    private readonly mesh: Mesh,
    private readonly listener: DragListener,
  ) {
    element.addEventListener('pointerdown', this.onPointerDown)
    element.addEventListener('pointermove', this.onPointerMove)
    element.addEventListener('pointerup', this.onPointerUp)
    element.addEventListener('pointercancel', this.onPointerUp)
  }

  /**
   * Reports where the pointer dragged the vertex since the last call, if anywhere. Called once per
   * animation frame it sends no more drags than the screen can show
   */
  flush(): void {
    if (this.held && this.moved) {
      this.moved = false
      this.listener.drag(this.held.vertex, this.target)
    }
  }

  /** releases the vertex the pointer holds and stops listening to the pointer */
  dispose(): void {
    this.element.removeEventListener('pointerdown', this.onPointerDown)
    this.element.removeEventListener('pointermove', this.onPointerMove)
    this.element.removeEventListener('pointerup', this.onPointerUp)
    this.element.removeEventListener('pointercancel', this.onPointerUp)
    if (this.held) {
      this.release()
    }
  }

  private readonly onPointerDown = (event: PointerEvent): void => {
    if (this.held || !event.isPrimary || event.button !== 0) {
      return
    }
    const hit = this.intersect(event)
    if (!hit?.face) {
      return
    }
    const { a, b, c } = hit.face
    const vertex = [a, b, c].reduce((closest, candidate) =>
      this.positionOf(candidate).distanceToSquared(hit.point) <
      this.positionOf(closest).distanceToSquared(hit.point)
        ? candidate
        : closest,
    )
    const position = this.positionOf(vertex)
    this.plane.setFromNormalAndCoplanarPoint(this.camera.getWorldDirection(new Vector3()), position)
    // the pointer keeps its distance to the vertex, wherever on the triangle it pressed
    const pressed = this.raycaster.ray.intersectPlane(this.plane, new Vector3()) ?? position
    this.offset.subVectors(position, pressed)

    event.stopImmediatePropagation()
    this.element.setPointerCapture(event.pointerId)
    this.element.style.cursor = 'grabbing'
    this.held = { vertex, pointerId: event.pointerId }
    this.listener.grab(vertex, position)
  }

  private readonly onPointerMove = (event: PointerEvent): void => {
    if (this.held === undefined) {
      // pressed buttons mean the camera turns, a pointer over the mesh doesn't change that
      if (event.buttons === 0) {
        this.element.style.cursor = this.intersect(event) ? 'grab' : ''
      }
    } else if (event.pointerId === this.held.pointerId) {
      this.aim(event)
      if (this.raycaster.ray.intersectPlane(this.plane, this.target)) {
        this.target.add(this.offset)
        this.moved = true
      }
    }
  }

  private readonly onPointerUp = (event: PointerEvent): void => {
    if (event.pointerId === this.held?.pointerId) {
      this.flush()
      this.release()
      this.element.style.cursor = this.intersect(event) ? 'grab' : ''
    }
  }

  private release(): void {
    if (this.held) {
      const { vertex } = this.held
      this.held = undefined
      this.moved = false
      this.listener.release(vertex)
    }
  }

  /** casts a ray from the camera through the pointer and returns where it first hits the mesh */
  private intersect(event: PointerEvent): Intersection | undefined {
    this.aim(event)
    return this.raycaster.intersectObject(this.mesh, false)[0]
  }

  private aim(event: PointerEvent): void {
    const bounds = this.element.getBoundingClientRect()
    this.pointer.set(
      ((event.clientX - bounds.left) / bounds.width) * 2 - 1,
      -((event.clientY - bounds.top) / bounds.height) * 2 + 1,
    )
    this.raycaster.setFromCamera(this.pointer, this.camera)
  }

  private positionOf(vertex: number): Vector3 {
    return this.mesh.localToWorld(
      new Vector3().fromBufferAttribute(this.mesh.geometry.getAttribute('position'), vertex),
    )
  }
}
