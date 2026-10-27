import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ClockControls from '../ClockControls.vue'

function mountControls(props: Partial<InstanceType<typeof ClockControls>['$props']> = {}) {
  return mount(ClockControls, { props: { paused: false, speed: 1, ...props } })
}

describe('ClockControls', () => {
  it('is a group of the controls of time', () => {
    expect(mountControls().get('[role="group"]').attributes('aria-label')).toBe('Time')
  })

  it('pauses a simulation that plays, and takes no steps while it does', async () => {
    const wrapper = mountControls()
    const [toggle, step] = wrapper.findAll('button')

    expect(toggle?.text()).toBe('Pause')
    expect(step?.element.disabled).toBe(true)

    await toggle?.trigger('click')

    expect(wrapper.emitted('pause')).toHaveLength(1)
  })

  it('plays a paused simulation, or advances it a step at a time', async () => {
    const wrapper = mountControls({ paused: true })
    const [toggle, step] = wrapper.findAll('button')

    await step?.trigger('click')
    await toggle?.trigger('click')

    expect(toggle?.text()).toBe('Play')
    expect(wrapper.emitted('step')).toHaveLength(1)
    expect(wrapper.emitted('play')).toHaveLength(1)
  })

  it('offers real time and slow motion, and shows the speed of the room', () => {
    const select = mountControls({ speed: 0.25 }).get('select')

    expect(select.findAll('option').map((option) => option.text())).toEqual([
      '1×',
      '½×',
      '¼×',
      '⅛×',
    ])
    expect(select.element.value).toBe('0.25')
  })

  it('asks for another speed', async () => {
    const wrapper = mountControls()

    await wrapper.get('select').setValue('0.125')

    expect(wrapper.emitted('speed')).toEqual([[0.125]])
  })

  it('labels the choice of the speed', () => {
    const wrapper = mountControls()

    expect(wrapper.get('label').attributes('for')).toBe(wrapper.get('select').attributes('id'))
    expect(wrapper.get('label').text()).toBe('Speed')
  })

  it('can be disabled as a whole', () => {
    const wrapper = mountControls({ paused: true, disabled: true })

    expect(wrapper.findAll('button').map((button) => button.element.disabled)).toEqual([true, true])
    expect(wrapper.get('select').element.disabled).toBe(true)
  })
})
