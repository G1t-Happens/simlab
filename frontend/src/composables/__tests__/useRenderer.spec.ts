import { mount } from '@vue/test-utils'
import { defineComponent, h, useTemplateRef } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import { useRenderer, type Renderer } from '../useRenderer'

function mountWithRenderer(create: (container: HTMLElement) => Renderer) {
  let handle: ReturnType<typeof useRenderer> | undefined
  const wrapper = mount(
    defineComponent({
      setup() {
        const container = useTemplateRef<HTMLElement>('container')
        handle = useRenderer(container, create)
        return () => h('div', { ref: 'container' })
      },
    }),
  )
  if (handle === undefined) {
    throw new Error('The component did not set up')
  }
  return { wrapper, ...handle }
}

describe('useRenderer', () => {
  it('creates the renderer in the container once the component is mounted', () => {
    const dispose = vi.fn<() => void>()
    const create = vi.fn<(container: HTMLElement) => Renderer>((container) => ({
      container,
      dispose,
    }))

    const { wrapper, renderer, unavailable } = mountWithRenderer(create)

    expect(create).toHaveBeenCalledExactlyOnceWith(wrapper.element)
    expect(renderer.value).toEqual({ container: wrapper.element, dispose })
    expect(unavailable.value).toBe(false)
  })

  it('disposes of the renderer before the component unmounts', () => {
    const dispose = vi.fn<() => void>()
    const { wrapper, renderer } = mountWithRenderer(() => ({ dispose }))

    wrapper.unmount()

    expect(dispose).toHaveBeenCalledOnce()
    expect(renderer.value).toBeUndefined()
  })

  it('reports that the browser cannot render', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined)

    const { renderer, unavailable } = mountWithRenderer(() => {
      throw new Error('Error creating WebGL context.')
    })

    expect(renderer.value).toBeUndefined()
    expect(unavailable.value).toBe(true)
    expect(consoleError).toHaveBeenCalledOnce()
  })
})
