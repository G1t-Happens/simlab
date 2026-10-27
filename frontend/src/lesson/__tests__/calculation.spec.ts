import { describe, expect, it } from 'vitest'

import { substituteValues } from '../calculation'

describe('substituteValues', () => {
  it('writes the values of symbols in place of the symbols, within their marks', () => {
    expect(
      substituteValues('\\class{sigma}{\\sigma} \\cdot (\\class{y}{y} - \\class{x}{x})', {
        sigma: 10,
        x: 1.0712,
        y: 3.2087,
      }),
    ).toBe('\\class{sigma}{10} \\cdot (\\class{y}{3.21} - \\class{x}{1.07})')
  })

  it('writes negative values in parentheses after operators and other values', () => {
    expect(
      substituteValues('2 \\cdot \\class{x}{x} - \\class{y}{y} \\class{z}{z}', {
        x: -3.21,
        y: -1,
        z: -2,
      }),
    ).toBe('2 \\cdot \\class{x}{(-3.21)} - \\class{y}{(-1)} \\class{z}{(-2)}')
  })

  it.each([
    ['the formula', '\\class{x}{x} + 1', '\\class{x}{-1} + 1'],
    ['a line', 'y \\\\ \\class{x}{x}', 'y \\\\ \\class{x}{-1}'],
    ['an environment', '\\begin{aligned} \\class{x}{x}', '\\begin{aligned} \\class{x}{-1}'],
    ['the other side of an equation', 'y &= \\class{x}{x}', 'y &= \\class{x}{-1}'],
    ['a bracket', '(\\class{x}{x})', '(\\class{x}{-1})'],
    ['an argument', '\\max(0,\\class{x}{x})', '\\max(0,\\class{x}{-1})'],
  ])('writes negative values that open %s without parentheses', (_, formula, substituted) => {
    expect(substituteValues(formula, { x: -1 })).toBe(substituted)
  })

  it('writes large numbers as powers of ten and groups the digits of whole numbers', () => {
    expect(substituteValues('\\class{a}{I} < \\class{b}{n}', { a: 619_502_400, b: 35_199 })).toBe(
      '\\class{a}{6.20 \\cdot 10^{8}} < \\class{b}{35{,}199}',
    )
  })

  it('keeps the symbols whose values are unknown', () => {
    expect(
      substituteValues('\\class{x}{x} + \\class{y}{y} + \\class{z}{z}', {
        x: Number.NaN,
        y: Number.POSITIVE_INFINITY,
      }),
    ).toBe('\\class{x}{x} + \\class{y}{y} + \\class{z}{z}')
  })

  it('replaces a symbol together with the marks within it', () => {
    expect(
      substituteValues('\\class{speed}{\\max(0, \\class{wind}{v})} = \\class{wind}{v}', {
        speed: 2.43,
        wind: 2,
      }),
    ).toBe('\\class{speed}{2.43} = \\class{wind}{2}')
  })

  it('writes values into the marks within a symbol whose value is unknown', () => {
    expect(substituteValues('\\class{speed}{\\max(0, \\class{wind}{v})}', { wind: 2 })).toBe(
      '\\class{speed}{\\max(0, \\class{wind}{2})}',
    )
  })

  it('replaces a symbol whose TeX holds groups and escaped braces', () => {
    expect(substituteValues('\\class{half}{\\tfrac{h}{2} \\}} + 1', { half: 0.0025 })).toBe(
      '\\class{half}{0.00250} + 1',
    )
  })

  it('leaves marks alone that no symbol follows', () => {
    expect(substituteValues('\\class{x} y', { x: 1 })).toBe('\\class{x} y')
    expect(substituteValues('\\class{x', { x: 1 })).toBe('\\class{x')
  })
})
