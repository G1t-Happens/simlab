import { NeutralToneMapping, WebGLRenderer } from 'three'

import type * as Description from '@/api/scene'

import { BloomRenderer } from './BloomRenderer'
import { Stage, type PointerListener } from './Stage'

/**
 * Renders the scene of a simulation into a canvas that fills its container, with bloom if objects
 * of the scene glow. Frames move the objects, the pointer turns the camera, drags the vertices of a
 * grabbable mesh and selects the points of selectable point clouds
 */
export class SceneRenderer {
  private readonly renderer: WebGLRenderer
  private readonly resizeObserver: ResizeObserver
  private stage: Stage | undefined
  private bloom: BloomRenderer | undefined
  private width = 1
  private height = 1

  /**
   * @param listener learns what the pointer does with the vertices of a grabbable mesh and with the
   *     points of selectable point clouds
   * @throws if the browser can't create a WebGL context
   */
  constructor(
    container: HTMLElement,
    private readonly listener: PointerListener,
  ) {
    this.renderer = new WebGLRenderer({ antialias: true })
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    this.renderer.toneMapping = NeutralToneMapping
    this.renderer.shadowMap.enabled = true

    container.append(this.renderer.domElement)
    this.resizeObserver = new ResizeObserver(([entry]) => {
      if (entry) {
        this.resize(entry.contentRect.width, entry.contentRect.height)
      }
    })
    this.resizeObserver.observe(container)
    this.renderer.setAnimationLoop(() => {
      this.render()
    })
  }

  /** shows a scene in place of the previous one, moving objects appear with the next frame */
  show(scene: Description.Scene): void {
    this.disposeStage()
    this.stage = new Stage(scene, this.renderer.domElement, this.listener)
    this.stage.setAspect(this.width / this.height)
    if (this.stage.glowing) {
      this.bloom = new BloomRenderer(this.renderer, this.stage.scene, this.stage.camera)
    }
  }

  update(frame: Float32Array): void {
    this.stage?.update(frame)
  }

  dispose(): void {
    this.renderer.setAnimationLoop(null)
    this.resizeObserver.disconnect()
    this.disposeStage()
    this.renderer.dispose()
    this.renderer.domElement.remove()
  }

  private render(): void {
    if (this.stage) {
      this.stage.animate()
      if (this.bloom) {
        this.bloom.render()
      } else {
        this.renderer.render(this.stage.scene, this.stage.camera)
      }
    }
  }

  private resize(width: number, height: number): void {
    if (width > 0 && height > 0) {
      this.width = width
      this.height = height
      this.renderer.setSize(width, height, false)
      this.stage?.setAspect(width / height)
      this.bloom?.setSize(width, height)
    }
  }

  private disposeStage(): void {
    this.stage?.dispose()
    this.stage = undefined
    this.bloom?.dispose()
    this.bloom = undefined
  }
}
