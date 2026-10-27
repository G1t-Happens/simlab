import { Vector2, type Camera, type Scene, type WebGLRenderer } from 'three'
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js'
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js'
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js'
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js'

/** how strongly bright regions bleed light into their surroundings */
const STRENGTH = 0.6

/** how far the bloom spreads, from 0 to 1 */
const RADIUS = 0.3

/** brightness above which regions bloom, the dark background stays below it */
const THRESHOLD = 0.8

/**
 * Renders a scene with bloom: light from bright regions (like crowds of glowing stars) bleeds into
 * their surroundings. The scene is rendered in high dynamic range, blurred where it's bright, then
 * tone mapped as the renderer would render it directly
 */
export class BloomRenderer {
  private readonly composer: EffectComposer
  private readonly renderPass: RenderPass
  private readonly bloomPass: UnrealBloomPass
  private readonly outputPass = new OutputPass()

  constructor(renderer: WebGLRenderer, scene: Scene, camera: Camera) {
    this.composer = new EffectComposer(renderer)
    this.renderPass = new RenderPass(scene, camera)
    this.bloomPass = new UnrealBloomPass(
      renderer.getSize(new Vector2()),
      STRENGTH,
      RADIUS,
      THRESHOLD,
    )
    this.composer.addPass(this.renderPass)
    this.composer.addPass(this.bloomPass)
    this.composer.addPass(this.outputPass)
  }

  render(): void {
    this.composer.render()
  }

  /** fits the bloom to a canvas of the given size in CSS pixels, like the renderer does */
  setSize(width: number, height: number): void {
    this.composer.setSize(width, height)
  }

  /** frees the render targets and materials of the passes, which the composer keeps */
  dispose(): void {
    this.renderPass.dispose()
    this.bloomPass.dispose()
    this.outputPass.dispose()
    this.composer.dispose()
  }
}
