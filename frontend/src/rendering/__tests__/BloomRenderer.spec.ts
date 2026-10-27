import { PerspectiveCamera, Scene, Vector2, WebGLRenderer } from 'three'
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js'
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js'
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js'
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js'
import { describe, expect, it, vi } from 'vitest'

import { BloomRenderer } from '../BloomRenderer'

// jsdom has no WebGL, so neither the renderer nor the passes can render. This renderer only knows
// its size
vi.mock('three', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  WebGLRenderer: class {
    getSize(target: Vector2): Vector2 {
      return target.set(640, 480)
    }
  },
}))
vi.mock(import('three/addons/postprocessing/EffectComposer.js'))
vi.mock(import('three/addons/postprocessing/OutputPass.js'))
vi.mock(import('three/addons/postprocessing/RenderPass.js'))
vi.mock(import('three/addons/postprocessing/UnrealBloomPass.js'))

function instanceOf<T>(type: { readonly mock: { readonly instances: T[] } }): T {
  const [instance, ...others] = type.mock.instances
  if (instance === undefined || others.length > 0) {
    throw new Error(`Expected one instance, but got ${String(type.mock.instances.length)}`)
  }
  return instance
}

describe('BloomRenderer', () => {
  const scene = new Scene()
  const camera = new PerspectiveCamera()

  it('renders the scene, lets bright regions bloom and then maps the tones, in this order', () => {
    new BloomRenderer(new WebGLRenderer(), scene, camera).render()

    const composer = instanceOf(vi.mocked(EffectComposer))
    expect(RenderPass).toHaveBeenCalledExactlyOnceWith(scene, camera)
    expect(UnrealBloomPass).toHaveBeenCalledExactlyOnceWith(
      new Vector2(640, 480),
      expect.any(Number),
      expect.any(Number),
      expect.any(Number),
    )
    expect(vi.mocked(composer.addPass).mock.calls).toEqual([
      [instanceOf(vi.mocked(RenderPass))],
      [instanceOf(vi.mocked(UnrealBloomPass))],
      [instanceOf(vi.mocked(OutputPass))],
    ])
    expect(composer.render).toHaveBeenCalledOnce()
  })

  it('fits the bloom to the size of the canvas', () => {
    new BloomRenderer(new WebGLRenderer(), scene, camera).setSize(800, 600)

    expect(instanceOf(vi.mocked(EffectComposer)).setSize).toHaveBeenCalledExactlyOnceWith(800, 600)
  })

  it('frees the passes, which the composer keeps, and the composer', () => {
    new BloomRenderer(new WebGLRenderer(), scene, camera).dispose()

    expect(instanceOf(vi.mocked(RenderPass)).dispose).toHaveBeenCalledOnce()
    expect(instanceOf(vi.mocked(UnrealBloomPass)).dispose).toHaveBeenCalledOnce()
    expect(instanceOf(vi.mocked(OutputPass)).dispose).toHaveBeenCalledOnce()
    expect(instanceOf(vi.mocked(EffectComposer)).dispose).toHaveBeenCalledOnce()
  })
})
