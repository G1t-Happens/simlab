import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { BackendHealth } from '@/composables/useBackendHealth'

import BackendStatus from '../BackendStatus.vue'

describe('BackendStatus', () => {
  it.each<[BackendHealth, string, string]>([
    ['PENDING', 'Backend checking…', 'neutral'],
    ['UP', 'Backend UP', 'positive'],
    ['DOWN', 'Backend DOWN', 'negative'],
    ['OUT_OF_SERVICE', 'Backend OUT OF SERVICE', 'negative'],
    ['UNKNOWN', 'Backend UNKNOWN', 'warning'],
    ['UNREACHABLE', 'Backend unreachable', 'negative'],
  ])('announces %s as "%s"', (health, text, tone) => {
    const wrapper = mount(BackendStatus, { props: { health } })

    const status = wrapper.get('[role="status"]')
    expect(status.text()).toBe(text)
    expect(status.attributes('data-tone')).toBe(tone)
  })
})
