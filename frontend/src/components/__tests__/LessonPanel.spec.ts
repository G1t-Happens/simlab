import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { calculatingLesson, lorenzLesson } from '@/__tests__/lessons'
import { lorenz } from '@/__tests__/simulations'

import LessonPanel from '../LessonPanel.vue'

enableAutoUnmount(afterEach)

function stubLesson(response: Response) {
  const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

/** mounts the panel into the document, where it can take the focus */
function mountPanel(props: Partial<InstanceType<typeof LessonPanel>['$props']> = {}) {
  return mount(LessonPanel, {
    props: {
      type: 'lorenz',
      open: true,
      parameters: lorenz.parameters,
      parameterValues: { sigma: 10, rho: 28 },
      readoutValues: {},
      highlightedParameter: undefined,
      highlightedReadout: undefined,
      ...props,
    },
    attachTo: document.body,
  })
}

/** mounts the panel with a lesson that calculates with the readouts x, y and ẋ */
async function mountCalculatingLesson(
  props: Partial<InstanceType<typeof LessonPanel>['$props']> = {},
) {
  stubLesson(Response.json(calculatingLesson))
  const wrapper = mountPanel({ readoutValues: { x: 1.0712, y: 3.2087, xDot: 21.375 }, ...props })
  await vi.waitFor(() => {
    expect(wrapper.find('.calculation').exists()).toBe(true)
  })
  return wrapper
}

/** mounts the panel with the lesson of the Lorenz attractor, once the lesson has rendered */
async function mountLesson(props: Partial<InstanceType<typeof LessonPanel>['$props']> = {}) {
  stubLesson(Response.json(lorenzLesson))
  const wrapper = mountPanel(props)
  await vi.waitFor(() => {
    expect(wrapper.findAll('.code-excerpt')).toHaveLength(2)
  })
  return wrapper
}

function highlighted(wrapper: ReturnType<typeof mountPanel>): string[] {
  return wrapper.findAll('[data-symbol].highlighted').map((mark) => mark.text())
}

describe('LessonPanel', () => {
  it('shows the text with its formulas, the code it explains and the experiments', async () => {
    const fetchMock = stubLesson(Response.json(lorenzLesson))

    const wrapper = mountPanel()

    await vi.waitFor(() => {
      expect(wrapper.findAll('.code-excerpt')).toHaveLength(2)
    })
    expect(fetchMock).toHaveBeenCalledExactlyOnceWith(
      '/api/simulations/lorenz/lesson',
      expect.anything(),
    )
    expect(wrapper.get('.text h3').text()).toBe('The equations')
    expect(wrapper.findAll('.text .katex')).toHaveLength(3)
    expect(
      wrapper.findAll('.code-excerpt .reference').map((reference) => reference.text()),
    ).toEqual(['LorenzSystem#derivative', 'OdeSystem#derivative'])
    expect(wrapper.get('.code-excerpt .line').element.textContent).toBe('    @Override')
    expect(wrapper.get('.experiment').text()).toContain('What do the particles do for')
  })

  it('shows that it loads the lesson', () => {
    stubLesson(Response.json(lorenzLesson))

    expect(mountPanel().get('.loading').text()).toBe('Loading…')
  })

  it('reports a lesson that cannot be loaded', async () => {
    stubLesson(new Response('Bad Gateway', { status: 502 }))

    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe(
      'The lesson could not be loaded: Request failed with HTTP status 502',
    )
  })

  it('is a dialog named by its heading', () => {
    stubLesson(Response.json(lorenzLesson))

    const dialog = mountPanel().get('[role="dialog"]')

    expect(dialog.attributes('aria-labelledby')).toBe(dialog.get('h2').attributes('id'))
    expect(dialog.get('h2').text()).toBe('How it works')
  })

  it('takes the focus whenever it opens', async () => {
    stubLesson(Response.json(lorenzLesson))
    const wrapper = mountPanel({ open: false })
    await flushPromises()

    expect(wrapper.attributes('hidden')).toBeDefined()
    expect(document.activeElement).not.toBe(wrapper.element)

    await wrapper.setProps({ open: true })

    expect(wrapper.attributes('hidden')).toBeUndefined()
    expect(document.activeElement).toBe(wrapper.element)
  })

  it('asks to close with its button and with the Escape key', async () => {
    stubLesson(Response.json(lorenzLesson))
    const wrapper = mountPanel()

    await wrapper.get('button[aria-label="Close"]').trigger('click')
    await wrapper.trigger('keydown', { key: 'Escape' })

    expect(wrapper.emitted('close')).toHaveLength(2)
  })

  it('tells how to find the symbols of its formulas', async () => {
    const wrapper = await mountLesson()

    expect(wrapper.get('.hint').text()).toBe(
      'Point at a symbol in a formula to find it in the code and among the controls.',
    )
  })

  it('lights up the symbol under the pointer everywhere, and names its parameter', async () => {
    const wrapper = await mountLesson()

    await wrapper.get('.text [data-symbol="sigma"]').trigger('mouseover')

    expect(highlighted(wrapper)).toEqual(['σ', 'σ', 'sigma'])
    expect(wrapper.emitted('highlight')).toEqual([['sigma', undefined]])

    await wrapper.get('.lesson').trigger('mouseleave')

    expect(highlighted(wrapper)).toEqual([])
    expect(wrapper.emitted('highlight')).toEqual([
      ['sigma', undefined],
      [undefined, undefined],
    ])
  })

  it('lights up a symbol in the code as well as in the formulas', async () => {
    const wrapper = await mountLesson()

    await wrapper.get('.code-excerpt [data-symbol="y"]').trigger('mouseover')

    expect(highlighted(wrapper)).toEqual(['y', 'y', 'y', 'y'])
  })

  it('names no parameter for a symbol that stands for none', async () => {
    const wrapper = await mountLesson()

    await wrapper.get('.text [data-symbol="y"]').trigger('mouseover')
    await wrapper.get('.text h3').trigger('mouseover')

    expect(wrapper.emitted('highlight')).toEqual([
      [undefined, undefined],
      [undefined, undefined],
    ])
  })

  it('lights up the symbols of the parameter that the controls point at', async () => {
    const wrapper = await mountLesson()

    await wrapper.setProps({ highlightedParameter: 'rho' })

    expect(highlighted(wrapper)).toEqual(['ρ'])

    await wrapper.setProps({ highlightedParameter: undefined })

    expect(highlighted(wrapper)).toEqual([])
  })

  it('runs an experiment with its values', async () => {
    const wrapper = await mountLesson()

    const button = wrapper.get('.experiment button')
    expect(button.text()).toBe('Try ρ = 0.5')

    await button.trigger('click')

    expect(wrapper.emitted('experiment')).toEqual([[{ rho: 0.5 }]])
  })

  it('runs no experiment while it cannot', async () => {
    const wrapper = await mountLesson({ experimentsDisabled: true })

    expect(wrapper.get<HTMLButtonElement>('.experiment button').element.disabled).toBe(true)
  })

  it('calculates with the values of parameters and readouts in place of symbols', async () => {
    const wrapper = await mountCalculatingLesson()

    const values = wrapper
      .findAll('.calculation .katex-html [data-symbol]')
      .map((mark) => mark.text())
    expect(values).toEqual(['10', '3.21', '1.07', '21.4'])
  })

  it('calculates with new values as they come', async () => {
    const wrapper = await mountCalculatingLesson()

    await wrapper.setProps({ parameterValues: { sigma: 20 }, readoutValues: { x: -2 } })

    const values = wrapper
      .findAll('.calculation .katex-html [data-symbol]')
      .map((mark) => mark.text())
    expect(values.slice(0, 3)).toEqual(['20', 'y', '(−2)'])
  })

  it('calculates with no values while it is closed, as nobody sees them', async () => {
    const wrapper = await mountCalculatingLesson({ open: false })

    expect(wrapper.get('.calculation .katex-html [data-symbol="sigma"]').text()).toBe('σ')

    await wrapper.setProps({ open: true })

    expect(wrapper.get('.calculation .katex-html [data-symbol="sigma"]').text()).toBe('10')
  })

  it('tells how to find the symbols of its formulas among the readouts too', async () => {
    const wrapper = await mountCalculatingLesson()

    expect(wrapper.get('.hint').text()).toBe(
      'Point at a symbol in a formula to find it in the code, among the controls and the readouts.',
    )
  })

  it('names the readout of the symbol under the pointer', async () => {
    const wrapper = await mountCalculatingLesson()

    await wrapper.get('.text [data-symbol="x"]').trigger('mouseover')

    expect(wrapper.emitted('highlight')).toEqual([[undefined, 'x']])
    expect(highlighted(wrapper)).toEqual(['x', '1.07'])
  })

  it('lights up the symbols of the readout that the readouts point at', async () => {
    const wrapper = await mountCalculatingLesson()

    await wrapper.setProps({ highlightedReadout: 'y' })

    expect(highlighted(wrapper)).toEqual(['3.21'])
  })

  it('keeps the symbols lit while the values of a calculation change', async () => {
    const wrapper = await mountCalculatingLesson({ highlightedReadout: 'y' })

    await wrapper.setProps({ readoutValues: { x: 1, y: 4.5, xDot: 35 } })

    expect(highlighted(wrapper)).toEqual(['4.50'])
  })
})
