import { enableAutoUnmount, mount } from '@vue/test-utils'
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'

import { calculatingLesson, lorenzLesson } from '@/__tests__/lessons'
import { clothScene } from '@/__tests__/scenes'
import { cloth, galaxies, lorenz } from '@/__tests__/simulations'

import SimulationPage from '../SimulationPage.vue'

enableAutoUnmount(afterEach)

// the page loads the lesson panel with KaTeX and Shiki when it first opens, which can take longer
// than a test waits for the panel. Loaded before, it opens as fast as it renders
beforeAll(async () => {
  await import('@/components/LessonPanel.vue')
})

/** mounts the page into the document, where its elements can take the focus */
function mountPage(props: Partial<InstanceType<typeof SimulationPage>['$props']> = {}) {
  return mount(SimulationPage, {
    props: {
      simulation: cloth,
      time: 't = 1.5 s',
      values: { wind: 3, gravity: 9.81 },
      status: 'open',
      failure: undefined,
      ...props,
    },
    attachTo: document.body,
  })
}

describe('SimulationPage', () => {
  it('introduces the simulation and labels its scene', () => {
    const wrapper = mountPage()

    expect(wrapper.get('h1').text()).toBe('Cloth')
    expect(wrapper.get('.intro p').text()).toBe('A cloth hangs from two corners.')
    expect(wrapper.get('[role="img"]').attributes('aria-label')).toBe('Cloth, live in 3D')
    expect(wrapper.get('.time').text()).toBe('t = 1.5 s')
  })

  it('shows a title of its own until the server described the simulation', () => {
    const wrapper = mountPage({ simulation: undefined })

    expect(wrapper.get('h1').text()).toBe('Simulation')
    expect(wrapper.find('.intro p').exists()).toBe(false)
    expect(wrapper.find('.lesson-button').exists()).toBe(false)
    expect(wrapper.find('input').exists()).toBe(false)
  })

  it('opens the lesson of the simulation, which loads when it opens first', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(Response.json(lorenzLesson))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mountPage()
    const button = wrapper.get('.lesson-button')

    expect(button.text()).toBe('How it works')
    expect(button.attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)

    await button.trigger('click')
    await vi.waitFor(() => {
      expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    })

    expect(button.attributes('aria-expanded')).toBe('true')
    expect(fetchMock).toHaveBeenCalledExactlyOnceWith(
      '/api/simulations/cloth/lesson',
      expect.anything(),
    )
  })

  it('closes the lesson with the focus back on its button, and keeps it for the next time', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(Response.json(lorenzLesson))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mountPage()
    const button = wrapper.get('.lesson-button')
    await button.trigger('click')
    await vi.waitFor(() => {
      expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    })

    await wrapper.get('[role="dialog"] [aria-label="Close"]').trigger('click')

    expect(wrapper.get('[role="dialog"]').attributes('hidden')).toBeDefined()
    expect(button.attributes('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(button.element)

    await button.trigger('click')

    expect(wrapper.get('[role="dialog"]').attributes('hidden')).toBeUndefined()
    expect(fetchMock).toHaveBeenCalledOnce()
  })

  it('lights up the control of a parameter together with its symbols in the lesson', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(Response.json(lorenzLesson)))
    const wrapper = mountPage({ simulation: lorenz, values: { sigma: 10, rho: 28 } })
    await wrapper.get('.lesson-button').trigger('click')
    await vi.waitFor(() => {
      expect(wrapper.findAll('.code-excerpt')).toHaveLength(2)
    })
    const highlighted = () => ({
      controls: wrapper
        .findAll('.parameter.highlighted')
        .map((control) => control.get('label').text()),
      symbols: wrapper.findAll('[data-symbol].highlighted').map((mark) => mark.text()),
    })

    await wrapper.get('.text [data-symbol="sigma"]').trigger('mouseover')

    expect(highlighted()).toEqual({ controls: ['σ sigma'], symbols: ['σ', 'σ', 'sigma'] })

    await wrapper.get('.lesson').trigger('mouseleave')
    await wrapper.findAll('.parameter')[1]?.trigger('mouseenter')

    expect(highlighted()).toEqual({ controls: ['ρ rho'], symbols: ['ρ'] })
  })

  it('passes the experiments of the lesson on', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(Response.json(lorenzLesson)))
    const wrapper = mountPage({ simulation: lorenz, values: { sigma: 10, rho: 28 } })
    await wrapper.get('.lesson-button').trigger('click')
    await vi.waitFor(() => {
      expect(wrapper.find('.experiment button').exists()).toBe(true)
    })

    await wrapper.get('.experiment button').trigger('click')

    expect(wrapper.emitted('experiment')).toEqual([[{ rho: 0.5 }]])
  })

  it('exposes the element that the scene renders into', () => {
    const wrapper = mountPage()

    expect(wrapper.vm.canvasHost).toBe(wrapper.get('[role="img"]').element)
  })

  it('explains how to turn the view, how to pull what can be grabbed and to select points', () => {
    expect(mountPage().get('.hint').text()).toBe('Drag to turn the view. The mouse wheel zooms.')
    expect(mountPage({ grabbable: true }).get('.hint').text()).toBe(
      'Drag the surface to pull it, and drag anywhere else to turn the view. ' +
        'The mouse wheel zooms.',
    )
    expect(mountPage({ selectable: true }).get('.hint').text()).toBe(
      'Click a point to read its values, and drag to turn the view. The mouse wheel zooms.',
    )
  })

  it('pauses, plays and steps the simulation, and slows it down', async () => {
    const wrapper = mountPage({ paused: true })
    const clock = wrapper.get('[role="group"][aria-label="Time"]')

    await clock.get('button:first-of-type').trigger('click')
    await clock.get('button:last-of-type').trigger('click')
    await clock.get('select').setValue('0.5')
    await wrapper.setProps({ paused: false })
    await clock.get('button:first-of-type').trigger('click')

    expect(wrapper.emitted('play')).toHaveLength(1)
    expect(wrapper.emitted('step')).toHaveLength(1)
    expect(wrapper.emitted('speed')).toEqual([[0.5]])
    expect(wrapper.emitted('pause')).toHaveLength(1)
  })

  it('keeps the clock still while the connection is not open', () => {
    const clock = mountPage({ status: 'connecting' }).get('[role="group"][aria-label="Time"]')

    expect(clock.get<HTMLButtonElement>('button').element.disabled).toBe(true)
    expect(clock.get('select').element.disabled).toBe(true)
  })

  it('offers the x-ray of a simulation that has one, and asks to show it', async () => {
    const wrapper = mountPage({ xray: clothScene.xray, xrayShown: false })

    await wrapper.get('.xray-controls input').setValue(true)

    expect(wrapper.emitted('xray')).toEqual([[true]])
  })

  it('offers no x-ray for a simulation without one', () => {
    expect(mountPage().find('.xray-controls').exists()).toBe(false)
  })

  it('keeps the x-ray as it is while the connection is not open', () => {
    const wrapper = mountPage({ xray: clothScene.xray, status: 'connecting' })

    expect(wrapper.get<HTMLInputElement>('.xray-controls input').element.disabled).toBe(true)
  })

  it('lists the readouts of the simulation with their latest values', () => {
    const wrapper = mountPage({
      simulation: lorenz,
      values: { sigma: 10, rho: 28 },
      readouts: [
        { name: 'x', label: 'x', unit: '' },
        { name: 'xDot', label: 'ẋ', unit: '' },
      ],
      readoutValues: { x: 3.2087, xDot: Number.NaN },
    })

    const section = wrapper.get('section.readouts')
    expect(section.attributes('aria-labelledby')).toBe(section.get('h2').attributes('id'))
    expect(section.get('h2').text()).toBe('Readouts')
    expect(section.findAll('dd').map((value) => value.text())).toEqual(['3.21', '–'])
  })

  it('lists no readouts of a simulation that reports none', () => {
    expect(mountPage().find('section.readouts').exists()).toBe(false)
  })

  it('lights up a readout with its symbols in the lesson, and the other way round', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>().mockResolvedValue(Response.json(calculatingLesson)),
    )
    const wrapper = mountPage({
      simulation: lorenz,
      values: { sigma: 10, rho: 28 },
      readouts: [
        { name: 'x', label: 'x', unit: '' },
        { name: 'y', label: 'y', unit: '' },
      ],
      readoutValues: { x: 1.0712, y: 3.2087, xDot: 21.375 },
    })
    await wrapper.get('.lesson-button').trigger('click')
    await vi.waitFor(() => {
      expect(wrapper.find('.calculation').exists()).toBe(true)
    })
    const highlighted = () => ({
      readouts: wrapper.findAll('.readout.highlighted').map((readout) => readout.get('dt').text()),
      symbols: wrapper.findAll('[data-symbol].highlighted').map((mark) => mark.text()),
    })

    await wrapper.findAll('.readout')[1]?.trigger('mouseenter')

    expect(highlighted()).toEqual({ readouts: ['y'], symbols: ['3.21'] })

    await wrapper.findAll('.readout')[1]?.trigger('mouseleave')
    await wrapper.get('.text [data-symbol="x"]').trigger('mouseover')

    expect(highlighted()).toEqual({ readouts: ['x'], symbols: ['x', '1.07'] })
  })

  it.each([
    ['connecting', 'Connecting…', true],
    ['open', 'Live', false],
    ['closed', 'Disconnected', true],
    ['lost', 'Connection lost', true],
  ] as const)(
    'shows a %s connection, with controls enabled only if open',
    (status, label, disabled) => {
      const wrapper = mountPage({ status })

      expect(wrapper.get('.connection').text()).toBe(label)
      expect(wrapper.get<HTMLInputElement>('input').element.disabled).toBe(disabled)
      expect(wrapper.get<HTMLButtonElement>('.actions button:first-of-type').element.disabled).toBe(
        disabled,
      )
    },
  )

  it('reports changed parameters, resets and restarts', async () => {
    const wrapper = mountPage()

    await wrapper.get('input').setValue('5')
    await wrapper.get('.actions button:first-of-type').trigger('click')
    await wrapper.get('.actions button:last-of-type').trigger('click')

    expect(wrapper.emitted('change')).toEqual([['wind', 5]])
    expect(wrapper.emitted('reset')).toHaveLength(1)
    expect(wrapper.emitted('restart')).toHaveLength(1)
  })

  it('groups the parameters that apply on restart, which can change before it', () => {
    const wrapper = mountPage({
      simulation: galaxies,
      values: { pace: 15, ratio: 1, inclination: 60 },
      status: 'connecting',
    })

    const group = wrapper.get('section')
    expect(group.attributes('aria-labelledby')).toBe(group.get('h2').attributes('id'))
    expect(group.get('h2').text()).toBe('Applies on restart')
    expect(group.findAll('label').map((label) => label.text())).toEqual([
      'q ratio',
      'i inclination',
    ])
    for (const slider of group.findAll<HTMLInputElement>('input')) {
      expect(slider.element.disabled).toBe(false)
    }
    expect(wrapper.get<HTMLInputElement>('input').element.disabled).toBe(true)
  })

  it('reports changes of parameters that apply on restart', async () => {
    const wrapper = mountPage({
      simulation: galaxies,
      values: { pace: 15, ratio: 1, inclination: 60 },
    })

    await wrapper.get('section input').setValue('0.5')

    expect(wrapper.emitted('change')).toEqual([['ratio', 0.5]])
  })

  it('urges a restart once parameters that apply on restart have changed', () => {
    const values = { pace: 15, ratio: 0.5, inclination: 60 }
    const idle = mountPage({ simulation: galaxies, values })
    const pending = mountPage({ simulation: galaxies, values, restartPending: true })

    const restart = pending.get('.actions button:last-of-type')
    expect(idle.get('.actions button:last-of-type').classes()).not.toContain('pending')
    expect(idle.find('.pending-note').exists()).toBe(false)
    expect(restart.classes()).toContain('pending')
    expect(pending.get(`#${restart.attributes('aria-describedby') ?? ''}`).text()).toBe(
      'Restart to apply the changes.',
    )
  })

  it('reports a failure and a scene that the browser cannot display', () => {
    const wrapper = mountPage({ failure: 'The server is not reachable.', sceneUnavailable: true })

    expect(wrapper.get('[role="alert"]').text()).toBe('The server is not reachable.')
    expect(wrapper.get('.notice').text()).toBe('This browser cannot display 3D graphics.')
  })
})
