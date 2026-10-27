import { describe, expect, it } from 'vitest'

import { formatNumber, roundForReading } from '../numbers'

describe('roundForReading', () => {
  it.each([
    [10, { negative: false, digits: '10', exponent: 0 }],
    [35_199, { negative: false, digits: '35,199', exponent: 0 }],
    [-1_234, { negative: true, digits: '1,234', exponent: 0 }],
    [0, { negative: false, digits: '0', exponent: 0 }],
    [-0, { negative: false, digits: '0', exponent: 0 }],
  ])('keeps all the digits of the whole number %d below a million', (value, rounded) => {
    expect(roundForReading(value)).toEqual(rounded)
  })

  it.each([
    [21.375, { negative: false, digits: '21.4', exponent: 0 }],
    [8 / 3, { negative: false, digits: '2.67', exponent: 0 }],
    [-0.0525, { negative: true, digits: '0.0525', exponent: 0 }],
    [0.5, { negative: false, digits: '0.500', exponent: 0 }],
    [999.4, { negative: false, digits: '999', exponent: 0 }],
    [0.001234, { negative: false, digits: '0.00123', exponent: 0 }],
  ])('keeps three significant digits of %d', (value, rounded) => {
    expect(roundForReading(value)).toEqual(rounded)
  })

  it.each([
    [619_502_400, { negative: false, digits: '6.20', exponent: 8 }],
    [32_881_600, { negative: false, digits: '3.29', exponent: 7 }],
    [1_234.5, { negative: false, digits: '1.23', exponent: 3 }],
    [999.6, { negative: false, digits: '1.00', exponent: 3 }],
    [-0.000_525, { negative: true, digits: '5.25', exponent: -4 }],
  ])('writes %d as a power of ten', (value, rounded) => {
    expect(roundForReading(value)).toEqual(rounded)
  })
})

describe('formatNumber', () => {
  it.each([
    [21.375, '21.4'],
    [-1.07, '−1.07'],
    [35_199, '35,199'],
    [619_502_400, '6.20\u00a0·\u00a010⁸'],
    [-0.000_525, '−5.25\u00a0·\u00a010⁻⁴'],
  ])('writes %d as %s', (value, text) => {
    expect(formatNumber(value)).toBe(text)
  })
})
