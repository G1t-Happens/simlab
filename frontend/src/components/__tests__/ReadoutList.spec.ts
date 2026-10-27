import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { Readout } from '@/api/scene'

import ReadoutList from '../ReadoutList.vue'

const READOUTS: readonly Readout[] = [
  { name: 'x', label: 'x', unit: '' },
  { name: 'speed', label: '|w|', unit: 'm/s' },
  { name: 'pairs', label: 'N(N−1)/2', unit: '' },
]

function mountList(props: Partial<InstanceType<typeof ReadoutList>['$props']> = {}) {
  return mount(ReadoutList, {
    props: {
      readouts: READOUTS,
      values: { x: -1.0712, speed: 2.4301, pairs: 619_502_400 },
      highlighted: undefined,
      ...props,
    },
  })
}

function shown(wrapper: ReturnType<typeof mountList>): string[][] {
  return wrapper
    .findAll('.readout')
    .map((readout) => [readout.get('dt').text(), readout.get('dd').text()])
}

describe('ReadoutList', () => {
  it('lists every readout with its value rounded for reading and its unit', () => {
    expect(shown(mountList())).toEqual([
      ['x', '−1.07'],
      ['|w| speed', '2.43\u00a0m/s'],
      ['N(N−1)/2 pairs', '6.20\u00a0·\u00a010⁸'],
    ])
  })

  it('shows a dash for values that are unknown', () => {
    const wrapper = mountList({ values: { x: Number.NaN, speed: 2 } })

    expect(shown(wrapper).map(([, value]) => value)).toEqual(['–', '2\u00a0m/s', '–'])
  })

  it('lights up the readout that its symbol lights up', () => {
    const wrapper = mountList({ highlighted: 'speed' })

    expect(
      wrapper.findAll('.readout.highlighted').map((readout) => readout.get('dt').text()),
    ).toEqual(['|w| speed'])
  })

  it('tells where the pointer is among the readouts', async () => {
    const wrapper = mountList()

    await wrapper.findAll('.readout')[1]?.trigger('mouseenter')
    await wrapper.findAll('.readout')[1]?.trigger('mouseleave')

    expect(wrapper.emitted('highlight')).toEqual([['speed'], [undefined]])
  })
})
