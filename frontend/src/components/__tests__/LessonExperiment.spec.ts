import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { cloth, lorenz } from '@/__tests__/simulations'

import LessonExperiment from '../LessonExperiment.vue'

/** mounts an experiment that sets the wind of the cloth, whose other parameter is gravity */
function mountExperiment(props: Partial<InstanceType<typeof LessonExperiment>['$props']> = {}) {
  return mount(LessonExperiment, {
    props: {
      question: '<p>What if the wind blows harder?</p>',
      explanation: '<p>The force grows with the square of its speed.</p>',
      values: { wind: 6 },
      parameters: cloth.parameters,
      ...props,
    },
  })
}

describe('LessonExperiment', () => {
  it('asks its question, to be answered before it runs', () => {
    const wrapper = mountExperiment()

    const group = wrapper.get('[role="group"]')
    expect(wrapper.get(`#${group.attributes('aria-labelledby') ?? ''}`).text()).toBe(
      'What if the wind blows harder?',
    )
    expect(wrapper.get('.label').text()).toBe('Experiment')
    expect(wrapper.findAll('.hint')[0]?.text()).toBe('Predict what happens, then try it.')
  })

  it('names its values as the controls do, with their units', () => {
    const wrapper = mountExperiment({ values: { wind: 6, gravity: 1.62 } })

    expect(wrapper.get('button').text()).toBe('Try v = 6\u00a0m/s, g = 1.62\u00a0m/s²')
  })

  it('names the options that it chooses', () => {
    const wrapper = mountExperiment({
      values: { rho: 20, order: 1 },
      parameters: lorenz.parameters,
    })

    expect(wrapper.get('button').text()).toBe('Try ρ = 20, p = Euler')
  })

  it('tells that it sets the parameters that it does not name to their defaults', () => {
    expect(mountExperiment().text()).toContain('The other parameters return to their defaults.')
    expect(mountExperiment({ values: { wind: 6, gravity: 1.62 } }).text()).not.toContain(
      'The other parameters',
    )
  })

  it('explains what happens once asked to', () => {
    const wrapper = mountExperiment()

    const details = wrapper.get<HTMLDetailsElement>('details')
    expect(details.element.open).toBe(false)
    expect(details.get('summary').text()).toBe('What happens')
    expect(details.get('.explanation').text()).toBe('The force grows with the square of its speed.')
  })

  it('asks to run when its button is clicked', async () => {
    const wrapper = mountExperiment()

    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('run')).toEqual([[]])
  })

  it('can be disabled', () => {
    const wrapper = mountExperiment({ disabled: true })

    expect(wrapper.get<HTMLButtonElement>('button').element.disabled).toBe(true)
  })
})
