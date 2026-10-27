import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { ChoiceParameter, ParameterInfo } from '@/api/simulations'

import ParameterControls from '../ParameterControls.vue'

const parameters: ParameterInfo[] = [
  {
    kind: 'Range',
    name: 'sigma',
    label: 'σ',
    unit: '',
    min: 0.1,
    max: 30,
    defaultValue: 10,
    effect: 'LIVE',
  },
  {
    kind: 'Range',
    name: 'rho',
    label: 'ρ',
    unit: '',
    min: 0,
    max: 60,
    defaultValue: 28,
    effect: 'LIVE',
  },
]

const order: ChoiceParameter = {
  kind: 'Choice',
  name: 'order',
  label: 'p',
  options: [
    { value: 1, label: 'Euler' },
    { value: 4, label: 'Runge-Kutta 4' },
  ],
  defaultValue: 4,
  effect: 'LIVE',
}

describe('ParameterControls', () => {
  it('shows a labelled slider for every parameter', () => {
    const wrapper = mount(ParameterControls, {
      props: { parameters, values: { sigma: 10, rho: 20.5 }, highlighted: undefined },
    })

    const rho = wrapper.get<HTMLInputElement>('input[min="0"]')
    expect(wrapper.findAll('input[type="range"]')).toHaveLength(2)
    expect(rho.attributes()).toMatchObject({ min: '0', max: '60', step: 'any' })
    expect(rho.element.value).toBe('20.5')
    expect(wrapper.get(`label[for="${rho.attributes('id') ?? ''}"]`).text()).toBe('ρ rho')
    expect(wrapper.get(`output[for="${rho.attributes('id') ?? ''}"]`).text()).toBe('20.50')
  })

  it('shows the unit after the value', () => {
    const wrapper = mount(ParameterControls, {
      props: {
        parameters: [
          {
            kind: 'Range',
            name: 'wind',
            label: 'v',
            unit: 'm/s',
            min: 0,
            max: 8,
            defaultValue: 3,
            effect: 'LIVE',
          },
        ],
        values: { wind: 2.5 },
        highlighted: undefined,
      },
    })

    expect(wrapper.get('output').text()).toBe('2.50\u00a0m/s')
  })

  it('shows as many decimals as the smallest value of the range needs', () => {
    const wrapper = mount(ParameterControls, {
      props: {
        parameters: [
          {
            kind: 'Range',
            name: 'step',
            label: 'h',
            unit: '',
            min: 0.001,
            max: 0.2,
            defaultValue: 0.005,
            effect: 'LIVE',
          },
        ],
        values: { step: 0.005 },
        highlighted: undefined,
      },
    })

    expect(wrapper.get('output').text()).toBe('0.005')
  })

  it('reports a moved slider with the new value', async () => {
    const wrapper = mount(ParameterControls, {
      props: { parameters, values: { sigma: 10, rho: 28 }, highlighted: undefined },
    })

    await wrapper.get('input[min="0"]').setValue('42')

    expect(wrapper.emitted('change')).toEqual([['rho', 42]])
  })

  it('offers the options of a choice, with the chosen one checked', () => {
    const wrapper = mount(ParameterControls, {
      props: { parameters: [order], values: { order: 4 }, highlighted: undefined },
    })

    const options = wrapper.findAll<HTMLInputElement>('input[type="radio"]')
    expect(wrapper.get('legend').text()).toBe('p order')
    expect(options.map((option) => option.element.labels?.[0]?.textContent.trim())).toEqual([
      'Euler',
      'Runge-Kutta 4',
    ])
    expect(options.map((option) => option.element.checked)).toEqual([false, true])
    expect(new Set(options.map((option) => option.attributes('name'))).size).toBe(1)
  })

  it('reports a chosen option with its value', async () => {
    const wrapper = mount(ParameterControls, {
      props: { parameters: [order], values: { order: 4 }, highlighted: undefined },
    })

    await wrapper.get('input[value="1"]').setValue(true)

    expect(wrapper.emitted('change')).toEqual([['order', 1]])
  })

  it('lights up the parameter whose symbol the lesson points at', () => {
    const wrapper = mount(ParameterControls, {
      props: {
        parameters: [...parameters, order],
        values: { sigma: 10, rho: 28 },
        highlighted: 'rho',
      },
    })

    expect(
      wrapper.findAll('.highlighted').map((parameter) => parameter.get('label').text()),
    ).toEqual(['ρ rho'])
  })

  it('reports the parameter that the pointer or the focus is at', async () => {
    const wrapper = mount(ParameterControls, {
      props: {
        parameters: [...parameters, order],
        values: { sigma: 10, rho: 28 },
        highlighted: undefined,
      },
    })
    const [sigma, rho, choice] = wrapper.findAll('.parameter')

    await rho?.trigger('mouseenter')
    await rho?.trigger('mouseleave')
    await sigma?.get('input').trigger('focusin')
    await sigma?.get('input').trigger('focusout')
    await choice?.get('input').trigger('focusin')

    expect(wrapper.emitted('highlight')).toEqual([
      ['rho'],
      [undefined],
      ['sigma'],
      [undefined],
      ['order'],
    ])
  })

  it('can be disabled', () => {
    const wrapper = mount(ParameterControls, {
      props: {
        parameters: [...parameters, order],
        values: { sigma: 10, rho: 28, order: 4 },
        highlighted: undefined,
        disabled: true,
      },
    })

    for (const input of wrapper.findAll<HTMLInputElement>('input[type="range"]')) {
      expect(input.element.disabled).toBe(true)
    }
    expect(wrapper.get<HTMLFieldSetElement>('fieldset').element.disabled).toBe(true)
  })
})
