import { describe, expect, it } from 'vitest'

import {
  calculatingLesson,
  conduction,
  derivative,
  lorenzLesson,
  system,
} from '@/__tests__/lessons'

import { isLesson } from '../lessons'

describe('isLesson', () => {
  it('accepts a lesson of symbols, text, code with and without links, and experiments', () => {
    expect(isLesson(lorenzLesson)).toBe(true)
    expect(isLesson({ symbols: [], blocks: [] })).toBe(true)
  })

  it('accepts a lesson that calculates with the values of parameters and readouts', () => {
    expect(isLesson(calculatingLesson)).toBe(true)
  })

  it.each([
    ['without symbols', { symbols: undefined, blocks: [] }],
    ['without blocks', { symbols: [] }],
    ['with a symbol without name', { symbols: [{ parameter: 'rho', readout: null }], blocks: [] }],
    [
      'with a symbol of an odd parameter',
      { symbols: [{ name: 'rho', parameter: 1, readout: null }], blocks: [] },
    ],
    [
      'with a symbol of an odd readout',
      { symbols: [{ name: 'x', parameter: null, readout: 1 }], blocks: [] },
    ],
    [
      'with a symbol that leaves its readout open',
      { symbols: [{ name: 'x', parameter: null }], blocks: [] },
    ],
    ['with a calculation without formula', { blocks: [{ kind: 'Calculation' }] }],
    ['with a number among its blocks', { blocks: [42] }],
    [
      'with a block of an unknown kind',
      { blocks: [{ kind: 'Video', url: 'https://example.com' }] },
    ],
    ['with text without Markdown', { blocks: [{ kind: 'Text' }] }],
    ['with code without reference', { blocks: [{ ...derivative, reference: undefined }] }],
    ['with code from line 0', { blocks: [{ ...system, firstLine: 0, lastLine: 0 }] }],
    ['with code of a fraction of a line', { blocks: [{ ...system, lastLine: 8.5 }] }],
    ['with code of fewer lines than it claims', { blocks: [{ ...derivative, lastLine: 27 }] }],
    ['with code of more lines than it claims', { blocks: [{ ...derivative, lastLine: 25 }] }],
    ['with code from an unknown origin', { blocks: [{ ...system, origin: 'ELSEWHERE' }] }],
    ['with a link that runs a script', { blocks: [{ ...system, url: 'javascript:alert(1)' }] }],
    ['with a link that is not encrypted', { blocks: [{ ...system, url: 'http://github.com' }] }],
    ['with code without occurrences', { blocks: [{ ...system, occurrences: undefined }] }],
    [
      'with a symbol in a line before the code',
      { blocks: [{ ...system, occurrences: [{ symbol: 'y', line: 7, column: 1, length: 1 }] }] },
    ],
    [
      'with a symbol in a line after the code',
      { blocks: [{ ...system, occurrences: [{ symbol: 'y', line: 9, column: 1, length: 1 }] }] },
    ],
    [
      'with a symbol beyond the end of its line',
      { blocks: [{ ...system, occurrences: [{ symbol: 'y', line: 8, column: 57, length: 2 }] }] },
    ],
    [
      'with a symbol in column 0',
      { blocks: [{ ...system, occurrences: [{ symbol: 'y', line: 8, column: 0, length: 1 }] }] },
    ],
    ['with an experiment without question', { blocks: [{ ...conduction, question: undefined }] }],
    ['with an experiment without values', { blocks: [{ ...conduction, values: undefined }] }],
    ['with an experiment that sets text', { blocks: [{ ...conduction, values: { rho: 'low' } }] }],
    [
      'with an experiment that sets no number',
      { blocks: [{ ...conduction, values: { rho: Number.NaN } }] },
    ],
    [
      'with an experiment without explanation',
      { blocks: [{ ...conduction, explanation: undefined }] },
    ],
  ])('rejects a lesson %s', (_, lesson) => {
    expect(isLesson({ symbols: [], ...lesson })).toBe(false)
  })
})
