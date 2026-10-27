import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { clothScene, starsScene } from '@/__tests__/scenes'

import XrayControls from '../XrayControls.vue'

function mountControls(props: Partial<InstanceType<typeof XrayControls>['$props']> = {}) {
  return mount(XrayControls, { props: { objects: clothScene.xray, shown: false, ...props } })
}

describe('XrayControls', () => {
  it('offers to show the x-ray', () => {
    const toggle = mountControls().get<HTMLInputElement>('input[type="checkbox"]')

    expect(toggle.element.labels?.[0]?.textContent.trim()).toBe('X-ray')
    expect(toggle.element.checked).toBe(false)
  })

  it('asks to show the x-ray, or no longer', async () => {
    const wrapper = mountControls()
    const toggle = wrapper.get('input')

    await toggle.setValue(true)
    await toggle.setValue(false)

    expect(wrapper.emitted('show')).toEqual([[true], [false]])
  })

  it('explains the x-ray only while it shows', () => {
    expect(mountControls().find('ul').exists()).toBe(false)
    expect(mountControls({ shown: true }).get('ul').attributes('aria-label')).toBe(
      'What the x-ray shows',
    )
  })

  it('explains the colors of lines by their scale, from one end to the other', () => {
    const legend = mountControls({ shown: true }).get('li')

    expect(legend.text()).toContain('Strain of the edges')
    expect(legend.findAll('.ends span').map((end) => end.text())).toEqual([
      '−2\u00a0%',
      'ε',
      '2\u00a0%',
    ])
    expect(legend.get('.ramp').attributes('style')).toBe(
      'background: linear-gradient(to right, rgb(0, 0, 255), rgb(255, 255, 255), rgb(255, 0, 0));',
    )
  })

  it('shows the color of the edges of cubes', () => {
    const legend = mountControls({ objects: starsScene.xray, shown: true }).get('li')

    expect(legend.text()).toBe('Cells')
    expect(legend.get('.swatch').attributes('style')).toBe('border-color: rgb(63, 185, 80);')
  })

  it('can be disabled', () => {
    expect(mountControls({ disabled: true }).get('input').element.disabled).toBe(true)
  })
})
