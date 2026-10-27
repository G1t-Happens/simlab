import {
  BufferAttribute,
  BufferGeometry,
  DoubleSide,
  Mesh,
  MeshBasicMaterial,
  PerspectiveCamera,
  Vector3,
} from 'three'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { VertexDragger, type DragListener } from '../VertexDragger'

/**
 * A square of four vertices from (-1, -1, 0) to (1, 1, 0), split along the diagonal from vertex 0
 * to vertex 2, and a camera one unit in front of it with a field of view of 90°: on the element of
 * 100 by 100 pixels the square fills the view and every pixel maps to a point of the square
 */
function square(): Mesh {
  const geometry = new BufferGeometry()
    .setAttribute(
      'position',
      new BufferAttribute(Float32Array.of(-1, -1, 0, 1, -1, 0, 1, 1, 0, -1, 1, 0), 3),
    )
    .setIndex([0, 1, 2, 0, 2, 3])
  return new Mesh(geometry, new MeshBasicMaterial({ side: DoubleSide }))
}

function pointer(type: string, x: number, y: number, init: PointerEventInit = {}): PointerEvent {
  // pixels from the top left corner for a point (x, y) of the square
  return new PointerEvent(type, {
    clientX: (x + 1) * 50,
    clientY: (1 - y) * 50,
    pointerId: 1,
    isPrimary: true,
    button: 0,
    bubbles: true,
    ...init,
  })
}

describe('VertexDragger', () => {
  let element: HTMLElement
  let listener: { [K in keyof DragListener]: ReturnType<typeof vi.fn<DragListener[K]>> }
  let dragger: VertexDragger

  beforeEach(() => {
    element = document.createElement('div')
    vi.spyOn(element, 'getBoundingClientRect').mockReturnValue(new DOMRect(0, 0, 100, 100))
    element.setPointerCapture = vi.fn<HTMLElement['setPointerCapture']>()
    const camera = new PerspectiveCamera(90, 1, 0.1, 10)
    camera.position.set(0, 0, 1)
    camera.updateMatrixWorld()
    listener = {
      grab: vi.fn<DragListener['grab']>(),
      drag: vi.fn<DragListener['drag']>(),
      release: vi.fn<DragListener['release']>(),
    }
    dragger = new VertexDragger(element, camera, square(), listener)
  })

  afterEach(() => {
    dragger.dispose()
  })

  it('grabs the vertex closest to where the pointer presses the mesh', () => {
    element.dispatchEvent(pointer('pointerdown', 0.8, 0.6))

    expect(listener.grab).toHaveBeenCalledExactlyOnceWith(2, new Vector3(1, 1, 0))
    expect(element.setPointerCapture).toHaveBeenCalledWith(1)
    expect(element.style.cursor).toBe('grabbing')
  })

  it('drags the vertex along with the pointer, once per flush', () => {
    element.dispatchEvent(pointer('pointerdown', 0.8, 0.6))
    element.dispatchEvent(pointer('pointermove', 0.4, 0.2))
    element.dispatchEvent(pointer('pointermove', 0, 0))

    dragger.flush()
    dragger.flush()

    // the vertex keeps its distance to the pointer of (0.2, 0.4)
    expect(listener.drag).toHaveBeenCalledOnce()
    const [vertex, position] = listener.drag.mock.calls[0] ?? []
    expect(vertex).toBe(2)
    expect(position?.x).toBeCloseTo(0.2)
    expect(position?.y).toBeCloseTo(0.4)
    expect(position?.z).toBeCloseTo(0)
  })

  it('reports the last drag before releasing the vertex', () => {
    element.dispatchEvent(pointer('pointerdown', 0.8, 0.6))
    element.dispatchEvent(pointer('pointermove', 0, 0))

    element.dispatchEvent(pointer('pointerup', 0, 0))

    expect(listener.drag).toHaveBeenCalledOnce()
    expect(listener.release).toHaveBeenCalledExactlyOnceWith(2)
    expect(listener.drag.mock.invocationCallOrder[0]).toBeLessThan(
      listener.release.mock.invocationCallOrder[0] ?? 0,
    )
  })

  it('keeps a press on the mesh from reaching later listeners, and lets others through', () => {
    const later = vi.fn<(event: Event) => void>()
    element.addEventListener('pointerdown', later)

    element.dispatchEvent(pointer('pointerdown', 0.8, 0.6))
    element.dispatchEvent(pointer('pointerup', 0.8, 0.6))
    element.dispatchEvent(pointer('pointerdown', 0.8, 0.6, { button: 2 }))

    expect(later).toHaveBeenCalledOnce()
  })

  it('ignores presses beside the mesh and of other buttons or pointers', () => {
    element.dispatchEvent(pointer('pointerdown', 1.5, 0))
    element.dispatchEvent(pointer('pointerdown', 0.8, 0.6, { button: 2 }))
    element.dispatchEvent(pointer('pointerdown', 0.8, 0.6, { isPrimary: false }))

    expect(listener.grab).not.toHaveBeenCalled()
  })

  it('shows that the mesh can be grabbed while the pointer hovers over it', () => {
    element.dispatchEvent(pointer('pointermove', 0.5, 0.5, { buttons: 0 }))
    expect(element.style.cursor).toBe('grab')

    element.dispatchEvent(pointer('pointermove', 1.5, 0.5, { buttons: 0 }))
    expect(element.style.cursor).toBe('')
  })

  it('releases the vertex and stops listening when disposed', () => {
    element.dispatchEvent(pointer('pointerdown', 0.8, 0.6))

    dragger.dispose()
    element.dispatchEvent(pointer('pointerdown', 0.8, 0.6))

    expect(listener.release).toHaveBeenCalledExactlyOnceWith(2)
    expect(listener.grab).toHaveBeenCalledOnce()
  })
})
