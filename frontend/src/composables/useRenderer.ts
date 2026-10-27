import {
  onBeforeUnmount,
  onMounted,
  readonly,
  ref,
  shallowReadonly,
  shallowRef,
  toValue,
  type MaybeRefOrGetter,
  type Ref,
  type ShallowRef,
} from 'vue'

export interface Renderer {
  dispose(): void
}

export interface RendererHandle<T extends Renderer> {
  /** `undefined` before the component is mounted and if it couldn't be created */
  readonly renderer: Readonly<ShallowRef<T | undefined>>
  /** the browser can't render, mostly for lack of WebGL */
  readonly unavailable: Readonly<Ref<boolean>>
}

/**
 * Creates a renderer in a container once the component is mounted and disposes of it before the
 * component unmounts. The renderer lives in a shallow ref, which Vue doesn't make deeply reactive
 *
 * @param container element to render into, available once the component is mounted
 * @param create throws if the browser can't render
 */
export function useRenderer<T extends Renderer>(
  container: MaybeRefOrGetter<HTMLElement | null | undefined>,
  create: (container: HTMLElement) => T,
): RendererHandle<T> {
  const renderer = shallowRef<T>()
  const unavailable = ref(false)

  onMounted(() => {
    const element = toValue(container)
    if (element) {
      try {
        renderer.value = create(element)
      } catch (error) {
        console.error('The browser cannot render', error)
        unavailable.value = true
      }
    }
  })

  onBeforeUnmount(() => {
    renderer.value?.dispose()
    renderer.value = undefined
  })

  return { renderer: shallowReadonly(renderer), unavailable: readonly(unavailable) }
}
