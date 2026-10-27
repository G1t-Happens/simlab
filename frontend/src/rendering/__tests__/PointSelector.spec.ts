import { DataTexture, PerspectiveCamera } from 'three'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type * as Description from '@/api/scene'

import { PointSelector, type SelectListener } from '../PointSelector'
import { PointCloudView } from '../views'

/** selectable points, as many as the frame holds coordinates for */
function cloud(count: number): PointCloudView {
  const description: Description.PointCloud = {
    kind: 'PointCloud',
    count,
    firstColor: { red: 255, green: 255, blue: 255 },
    lastColor: { red: 255, green: 255, blue: 255 },
    trailLength: 0,
    glowing: false,
    selectable: true,
  }
  return new PointCloudView(description)
}

/**
 * A pointer event at a point (x, y) of the plane z = 0, which the camera one unit in front of it
 * with a field of view of 90° shows across the element of 100 by 100 pixels
 */
function pointer(type: string, x: number, y: number, init: PointerEventInit = {}): PointerEvent {
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

describe('PointSelector', () => {
  let element: HTMLElement
  let listener: { [K in keyof SelectListener]: ReturnType<typeof vi.fn<SelectListener[K]>> }
  let first: PointCloudView
  let second: PointCloudView
  let selector: PointSelector

  /** clicks a point of the plane z = 0: presses and releases the pointer there */
  function click(x: number, y: number, init: PointerEventInit = {}): void {
    element.dispatchEvent(pointer('pointerdown', x, y, init))
    element.dispatchEvent(pointer('pointerup', x, y, init))
  }

  beforeEach(() => {
    element = document.createElement('div')
    vi.spyOn(element, 'getBoundingClientRect').mockReturnValue(new DOMRect(0, 0, 100, 100))
    const camera = new PerspectiveCamera(90, 1, 0.1, 10)
    camera.position.set(0, 0, 1)
    camera.updateMatrixWorld()
    listener = { select: vi.fn<SelectListener['select']>(), deselect: vi.fn<() => void>() }
    first = cloud(2)
    second = cloud(1)
    first.update(Float32Array.of(-0.5, 0.5, 0, 0, 0, 0))
    second.update(Float32Array.of(0.5, -0.5, 0))
    selector = new PointSelector(
      element,
      camera,
      [
        { view: first, firstPoint: 0 },
        { view: second, firstPoint: 5 },
      ],
      listener,
    )
  })

  afterEach(() => {
    selector.dispose()
  })

  it('selects the point closest to a click, numbered across the point clouds', () => {
    click(0.02, -0.02)
    click(0.48, -0.5)

    expect(listener.select.mock.calls).toEqual([[1], [5]])
  })

  it('marks the selected point with a ring that follows it from frame to frame', () => {
    click(-0.5, 0.5)

    const position = selector.marker.geometry.getAttribute('position')
    expect(selector.marker.visible).toBe(true)
    expect([position.getX(0), position.getY(0), position.getZ(0)]).toEqual([-0.5, 0.5, 0])

    first.update(Float32Array.of(-0.25, 0.25, 0.5, 0, 0, 0))
    selector.update()

    expect([position.getX(0), position.getY(0), position.getZ(0)]).toEqual([-0.25, 0.25, 0.5])
  })

  it('draws the ring as a circle that fades towards its middle and its outside', () => {
    const { map } = selector.marker.material
    if (!(map instanceof DataTexture)) {
      throw new Error('The ring has no texture of data')
    }
    const { data, width } = map.image
    const opacity = (row: number, column: number) => data?.[4 * (row * width + column) + 3] ?? 0
    const middleRow = Array.from({ length: width }, (_, column) => opacity(width / 2, column))
    const brightest = middleRow.indexOf(Math.max(...middleRow))

    expect(opacity(width / 2, width / 2)).toBe(0)
    expect(opacity(0, 0)).toBe(0)
    // across the texture from -1 to 1 the ring lies 0.8 from the middle
    expect(Math.abs((2 * brightest + 1) / width - 1)).toBeCloseTo(0.8, 1)
  })

  it('lets go of the selected point with a click beside every point', () => {
    click(-0.5, 0.5)

    click(0.5, 0.5)

    expect(listener.deselect).toHaveBeenCalledOnce()
    expect(selector.marker.visible).toBe(false)
  })

  it('lets go of nothing with a click beside every point while none is selected', () => {
    click(0.5, 0.5)

    expect(listener.select).not.toHaveBeenCalled()
    expect(listener.deselect).not.toHaveBeenCalled()
  })

  it('takes a drag for turning the camera rather than for a click', () => {
    element.dispatchEvent(pointer('pointerdown', 0, 0))
    element.dispatchEvent(pointer('pointerup', 0.5, 0))

    expect(listener.select).not.toHaveBeenCalled()
  })

  it('ignores clicks of other buttons and pointers', () => {
    click(0, 0, { button: 2 })
    click(0, 0, { isPrimary: false })

    expect(listener.select).not.toHaveBeenCalled()
  })

  it('selects no points that no frame has shown yet', () => {
    const hidden = cloud(1)
    const camera = new PerspectiveCamera(90, 1, 0.1, 10)
    camera.position.set(0, 0, 1)
    camera.updateMatrixWorld()
    selector.dispose()
    selector = new PointSelector(element, camera, [{ view: hidden, firstPoint: 0 }], listener)

    click(0, 0)

    expect(listener.select).not.toHaveBeenCalled()
  })

  it('selects no points behind the camera', () => {
    first.update(Float32Array.of(0, 0, 2, 0, 0, 2))
    second.update(Float32Array.of(0, 0, 2))

    click(0, 0)

    expect(listener.select).not.toHaveBeenCalled()
  })

  it('stops listening to the pointer when disposed', () => {
    selector.dispose()

    click(0, 0)

    expect(listener.select).not.toHaveBeenCalled()
  })
})
