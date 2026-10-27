import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { derivative, system } from '@/__tests__/lessons'

import CodeExcerpt from '../CodeExcerpt.vue'

const html = '<pre class="shiki"><code><span class="line">void derivative();</span></code></pre>'

describe('CodeExcerpt', () => {
  it('labels code of the simulation and links its lines on GitHub', () => {
    const wrapper = mount(CodeExcerpt, { props: { block: derivative, html } })

    expect(wrapper.get('.origin').text()).toBe('Simulation')
    expect(wrapper.get('.reference').text()).toBe('LorenzSystem#derivative')
    const link = wrapper.get('a')
    expect(link.attributes()).toMatchObject({ href: derivative.url, target: '_blank' })
    expect(link.text()).toBe(
      'dev/simlab/core/simulations/lorenz/LorenzSystem.java:23–26 ' +
        '(on GitHub, opens in a new tab)',
    )
  })

  it('names the file and the lines of code of the toolkit that links nowhere', () => {
    const wrapper = mount(CodeExcerpt, { props: { block: system, html } })

    expect(wrapper.get('.origin').text()).toBe('Toolkit')
    expect(wrapper.find('a').exists()).toBe(false)
    expect(wrapper.get('.location').text()).toBe('dev/simlab/core/toolkit/ode/OdeSystem.java:8–8')
  })

  it('shows the highlighted code, numbered from its first line in the file', () => {
    const wrapper = mount(CodeExcerpt, { props: { block: derivative, html } })

    const code = wrapper.get<HTMLElement>('.code')
    expect(code.element.style.counterReset).toBe('line 22')
    expect(code.get('.line').text()).toBe('void derivative();')
  })
})
