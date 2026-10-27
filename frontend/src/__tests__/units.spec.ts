import { describe, expect, it } from 'vitest'

import { withUnit } from '../units'

describe('withUnit', () => {
  it('writes the unit after a space that does not break', () => {
    expect(withUnit('8', 'm/s')).toBe('8\u00a0m/s')
  })

  it('writes degrees of plane angle right after the number', () => {
    expect(withUnit('180', '°')).toBe('180°')
  })

  it('writes a dimensionless number alone', () => {
    expect(withUnit('0.5', '')).toBe('0.5')
  })
})
