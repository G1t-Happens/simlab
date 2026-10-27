import { describe, expect, it, vi } from 'vitest'

import { renderFormula, renderMarkdown } from '../markdown'

/** renders Markdown into the body of a document, where tests look for elements */
function render(markdown: string): HTMLElement {
  return new DOMParser().parseFromString(renderMarkdown(markdown), 'text/html').body
}

describe('renderMarkdown', () => {
  it('renders formulas within lines and on lines of their own, with MathML', () => {
    const html = render('Lorenz chose $\\sigma = 10$:\n\n$$\n\\dot x = \\sigma (y - x)\n$$')

    const [inline, display] = html.querySelectorAll('.katex')
    expect(inline?.closest('p')?.textContent).toContain('Lorenz chose')
    expect(inline?.querySelector('annotation')?.textContent).toBe('\\sigma = 10')
    expect(display?.parentElement?.classList).toContain('katex-display')
    expect(display?.querySelector('math')?.getAttribute('display')).toBe('block')
  })

  it('marks the symbols that formulas mark with MathJax’s command for a class', () => {
    const warn = vi.spyOn(console, 'warn')

    const html = render('$\\dot y = x (\\class{rho}{\\rho} - z) - \\class{y2}{y}$')

    const marks = [...html.querySelectorAll<HTMLElement>('.katex-html [data-symbol]')]
    expect(marks.map((mark) => [mark.dataset.symbol, mark.textContent])).toEqual([
      ['rho', 'ρ'],
      ['y2', 'y'],
    ])
    expect(warn).not.toHaveBeenCalled()
  })

  it.each([
    ['a mark with a name that is no symbol', '$\\class{sigma rho}{\\sigma}$'],
    ['a mark with a further attribute', '$\\class{sigma,onclick=alert(1)}{\\sigma}$'],
    ['data that is no mark of a symbol', '$\\htmlData{title=x}{\\sigma}$'],
    ['a class', '$\\htmlClass{lesson-panel}{\\sigma}$'],
    ['a style', '$\\htmlStyle{position: fixed}{\\sigma}$'],
    ['an id', '$\\htmlId{app}{\\sigma}$'],
  ])('renders no attributes that %s asks for', (_, markdown) => {
    const html = render(markdown)

    const attributes = [...html.querySelectorAll('.katex-html, .katex-html *')].flatMap((element) =>
      element.getAttributeNames(),
    )
    expect(attributes.filter((name) => !['class', 'style', 'aria-hidden'].includes(name))).toEqual(
      [],
    )
    expect(html.querySelector('.lesson-panel, [style*="position"]')).toBeNull()
  })

  it('keeps dollars that open no formula as text', () => {
    const html = render('It costs $5 and $ 6.')

    expect(html.querySelector('.katex')).toBeNull()
    expect(html.textContent).toBe('It costs $5 and $ 6.\n')
  })

  it('places the headings of a lesson one level below the heading of the panel', () => {
    const html = render('## Equations\n\n### Fixed points')

    expect([...html.children].map((heading) => heading.tagName)).toEqual(['H3', 'H4'])
  })

  it('opens links in a new tab, as screen readers announce', () => {
    const html = render('See [Lorenz (1963)](https://doi.org/10.1175/x).')

    const link = html.querySelector('a')
    expect(link?.getAttribute('href')).toBe('https://doi.org/10.1175/x')
    expect(link?.getAttribute('target')).toBe('_blank')
    expect(link?.textContent).toBe('Lorenz (1963) (opens in a new tab)')
    expect(link?.querySelector('.visually-hidden')?.textContent).toBe(' (opens in a new tab)')
  })

  it('keeps raw HTML as text', () => {
    const html = render('<img src="x" onerror="alert(1)">')

    expect(html.querySelector('img')).toBeNull()
    expect(html.textContent).toContain('<img src="x" onerror="alert(1)">')
  })

  it('renders no links that formulas ask for', () => {
    const html = render('$\\href{javascript:alert(1)}{x} \\url{https://example.com}$')

    expect(html.querySelector('a')).toBeNull()
  })
})

describe('renderFormula', () => {
  it('renders a formula on lines of its own, with its marks of symbols and MathML', () => {
    const html = new DOMParser().parseFromString(
      renderFormula('\\class{x}{(-1.07)} \\cdot 2'),
      'text/html',
    ).body

    expect(html.querySelector('.katex-display')).not.toBeNull()
    expect(html.querySelector('math')?.getAttribute('display')).toBe('block')
    expect(html.querySelector<HTMLElement>('.katex-html [data-symbol]')?.dataset.symbol).toBe('x')
    expect(html.querySelector('.katex-html [data-symbol]')?.textContent).toBe('(−1.07)')
  })

  it('trusts no command but the marks of symbols', () => {
    const html = new DOMParser().parseFromString(
      renderFormula('\\htmlId{app}{x} \\href{javascript:alert(1)}{y}'),
      'text/html',
    ).body

    expect(html.querySelector('#app, a')).toBeNull()
  })
})
