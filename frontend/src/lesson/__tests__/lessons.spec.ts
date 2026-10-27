import { describe, expect, it, vi } from 'vitest'

import { substituteValues } from '../calculation'
import { renderFormula, renderMarkdown } from '../markdown'

/** Markdown of the lessons by path, each next to the code of its simulation in sim-core */
const LESSONS = import.meta.glob<string>(
  '../../../../backend/sim-core/src/main/resources/dev/simlab/core/simulations/*/lesson.md',
  { query: '?raw', import: 'default', eager: true },
)

/** head of a lesson, YAML between lines of three dashes that declares its symbols */
const HEAD = /^---\n[\s\S]*?\n---\n/

/** an experiment, YAML in a fence, whose question and explanation hold formulas too */
const EXPERIMENT = /^```experiment\n([\s\S]*?)^```$/gm

/** a calculation, TeX in a fence, shown on lines of its own */
const CALCULATION = /^```calculation\n([\s\S]*?)^```$/gm

/** mark of a symbol in TeX, with the name of the symbol as its group */
const MARK = /\\class\{([^{}]*)\}/g

/**
 * Text of a lesson without its head, with the YAML of its experiments as text and its calculations
 * as formulas on lines of their own, so Markdown renders all of their formulas
 */
function text(lesson: string): string {
  return lesson.replace(HEAD, '').replace(EXPERIMENT, '$1').replace(CALCULATION, '$$$$\n$1$$$$')
}

function calculations(lesson: string): string[] {
  return [...lesson.matchAll(CALCULATION)].map(([, formula = '']) => formula)
}

/** parses HTML into the body of a document, where tests look for elements */
function parse(html: string): HTMLElement {
  return new DOMParser().parseFromString(html, 'text/html').body
}

/** expects HTML with formulas that KaTeX rendered without errors or unknown commands */
function expectRenderedFormulas(html: HTMLElement): void {
  expect(html.querySelectorAll('.katex').length).toBeGreaterThan(0)
  const errors = [...html.querySelectorAll('.katex-error')]
  expect(errors.map((error) => error.getAttribute('title'))).toEqual([])
  // KaTeX writes commands it doesn't know or trust in red
  const unsupported = [...html.querySelectorAll('mstyle[mathcolor="#cc0000"]')]
  expect(unsupported.map((command) => command.textContent)).toEqual([])
}

describe('the lessons of the simulations', () => {
  it('exist', () => {
    expect(Object.keys(LESSONS)).not.toHaveLength(0)
  })

  it.each(Object.entries(LESSONS))(
    'hold formulas that KaTeX renders without errors or warnings: %s',
    (_, lesson) => {
      const warn = vi.spyOn(console, 'warn')

      expectRenderedFormulas(parse(renderMarkdown(text(lesson))))
      expect(warn).not.toHaveBeenCalled()
    },
  )

  it.each(Object.entries(LESSONS))(
    'hold calculations that KaTeX renders with negative values in place of their symbols: %s',
    (_, lesson) => {
      const warn = vi.spyOn(console, 'warn')

      for (const formula of calculations(lesson)) {
        const values = Object.fromEntries(
          [...formula.matchAll(MARK)].map(([, name = '']) => [name, -12.3]),
        )
        const html = parse(renderFormula(substituteValues(formula, values)))

        expectRenderedFormulas(html)
        expect(html.querySelector('.katex-html')?.textContent).toContain('(−12.3)')
      }
      expect(calculations(lesson)).not.toHaveLength(0)
      expect(warn).not.toHaveBeenCalled()
    },
  )
})
