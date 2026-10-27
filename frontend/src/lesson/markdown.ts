import { tex } from '@mdit/plugin-tex'
import katex, { type TrustContext } from 'katex'
import 'katex/dist/katex.min.css'
import MarkdownIt from 'markdown-it'

const SYMBOL_NAME = /^[a-z][a-zA-Z0-9]*$/

/**
 * Formulas mark the symbols of a lesson with MathJax's class command, like `\class{sigma}{\sigma}`,
 * so they stay TeX that MathJax renders as well. KaTeX writes such a symbol into an element with
 * the attribute `data-symbol="sigma"`
 */
const MACROS = { '\\class': '\\htmlData{symbol=#1}{#2}' }

/** trusts nothing but the marks of symbols: a single attribute `data-symbol` with a name */
function isMarkOfSymbol(context: TrustContext): boolean {
  return (
    context.command === '\\htmlData' &&
    Object.keys(context.attributes).length === 1 &&
    SYMBOL_NAME.test(context.attributes['data-symbol'] ?? '')
  )
}

/**
 * Renders a TeX formula with KaTeX. KaTeX trusts no command that could add HTML of its own except
 * the marks of symbols, so the result is safe to insert into the page. It writes the formula as
 * MathML as well, which screen readers read out
 *
 * @param displayMode the formula stands on lines of its own rather than within a line
 */
function renderTex(formula: string, displayMode: boolean): string {
  return katex.renderToString(formula, {
    displayMode,
    throwOnError: false,
    // KaTeX adds the macros a formula defines to the object, so every formula gets a copy
    macros: { ...MACROS },
    trust: isMarkOfSymbol,
    // KaTeX warns about every command of its HTML extension, which the marks use on purpose
    strict: (errorCode) => (errorCode === 'htmlExtension' ? 'ignore' : 'warn'),
  })
}

/**
 * Renders the Markdown of lessons, with TeX formulas between `$` and `$$` as GitHub writes them.
 * Raw HTML stays text, so the result is as safe to insert into the page as the formulas are
 */
const markdown = new MarkdownIt({ html: false }).use(tex, { render: renderTex })

// the lesson panel has a heading of level 2, so the headings of a lesson start one level below
markdown.core.ruler.push('lesson_headings', (state) => {
  for (const token of state.tokens) {
    if (token.type === 'heading_open' || token.type === 'heading_close') {
      token.tag = `h${String(Number(token.tag.slice(1)) + 1)}`
    }
  }
})

// links (like to the works a lesson cites) open in a new tab so the simulation runs on
markdown.renderer.rules.link_open = (tokens, index, options, _, renderer) => {
  tokens[index]?.attrSet('target', '_blank')
  return renderer.renderToken(tokens, index, options)
}
markdown.renderer.rules.link_close = (tokens, index, options, _, renderer) =>
  '<span class="visually-hidden"> (opens in a new tab)</span>' +
  renderer.renderToken(tokens, index, options)

export function renderMarkdown(text: string): string {
  return markdown.render(text)
}

/** a TeX formula on lines of its own as HTML, like the calculations of a lesson hold it */
export function renderFormula(formula: string): string {
  return renderTex(formula, true)
}
