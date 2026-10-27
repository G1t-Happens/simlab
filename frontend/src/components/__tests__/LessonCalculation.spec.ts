import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { rateCalculation } from '@/__tests__/lessons'

import LessonCalculation from '../LessonCalculation.vue'

function mountCalculation(values: Readonly<Record<string, number>>) {
  return mount(LessonCalculation, { props: { formula: rateCalculation.formula, values } })
}

function marks(wrapper: ReturnType<typeof mountCalculation>): string[] {
  return wrapper.findAll('.katex-html [data-symbol]').map((mark) => mark.text())
}

describe('LessonCalculation', () => {
  it('shows its formula with the values of its symbols, which stay marked', () => {
    const wrapper = mountCalculation({ sigma: 10, x: 1.0712, y: 3.2087, xDot: 21.375 })

    expect(wrapper.get('figcaption').text()).toBe('Live calculation')
    expect(marks(wrapper)).toEqual(['10', '3.21', '1.07', '21.4'])
  })

  it('shows the symbols whose values are unknown', () => {
    const wrapper = mountCalculation({ sigma: 10, x: Number.NaN, y: Number.NaN })

    // KaTeX writes the dot of ẋ after the x
    expect(marks(wrapper)).toEqual(['10', 'y', 'x', 'x˙'])
  })

  it('shows new values as they come', async () => {
    const wrapper = mountCalculation({ sigma: 10 })

    await wrapper.setProps({ values: { sigma: 28 } })

    expect(marks(wrapper)[0]).toBe('28')
  })
})
