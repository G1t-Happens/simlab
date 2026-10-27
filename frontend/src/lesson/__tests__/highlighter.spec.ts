import { describe, expect, it } from 'vitest'

import type { Occurrence } from '@/api/lessons'

import { highlightJava } from '../highlighter'

/** highlights Java into the body of a document, where tests look for elements */
async function highlight(
  code: string,
  occurrences: readonly Occurrence[] = [],
  firstLine = 1,
): Promise<HTMLElement> {
  const html = await highlightJava({ code, firstLine, occurrences })
  return new DOMParser().parseFromString(html, 'text/html').body
}

describe('highlightJava', () => {
  it('writes an element per line, as the code indents it', async () => {
    const html = await highlight('    double x = 1;\n\n    // Done.')

    expect([...html.querySelectorAll('.line')].map((line) => line.textContent)).toEqual([
      '    double x = 1;',
      '',
      '    // Done.',
    ])
  })

  it('colors every token for the light and for the dark theme', async () => {
    const html = await highlight('double x = 1;')

    const keyword = [...html.querySelectorAll('.line span')].find(
      (token) => token.textContent === 'double',
    )
    expect(keyword?.getAttribute('style')).toMatch(/^--shiki-light:#\w{6};--shiki-dark:#\w{6}$/)
  })

  it('marks the identifiers that hold symbols, numbered by the lines of their file', async () => {
    const html = await highlight(
      '    double y = sigma * x;\n    return sigma;',
      [
        { symbol: 'sigma', line: 11, column: 16, length: 5 },
        { symbol: 'sigma', line: 12, column: 12, length: 5 },
      ],
      11,
    )

    const marks = [...html.querySelectorAll<HTMLElement>('[data-symbol]')]
    expect(marks.map((mark) => [mark.dataset.symbol, mark.textContent])).toEqual([
      ['sigma', 'sigma'],
      ['sigma', 'sigma'],
    ])
    expect(marks.map((mark) => mark.closest('.line')?.textContent)).toEqual([
      '    double y = sigma * x;',
      '    return sigma;',
    ])
  })

  it('escapes the code', async () => {
    const html = await highlight('String page = "<script>alert(1)</script>";')

    expect(html.querySelector('script')).toBeNull()
    expect(html.textContent).toContain('<script>alert(1)</script>')
  })
})
