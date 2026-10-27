import { createHighlighterCore } from 'shiki/core'
import { createJavaScriptRegexEngine } from 'shiki/engine/javascript'

import type { CodeBlock } from '@/api/lessons'

/**
 * Highlights Java in the colors of GitHub. Loads nothing but the Java grammar and two themes, and
 * matches their regexes with the engine of the browser instead of WebAssembly
 */
const highlighter = createHighlighterCore({
  langs: [import('shiki/langs/java.mjs')],
  themes: [
    import('shiki/themes/github-light-default.mjs'),
    import('shiki/themes/github-dark-default.mjs'),
  ],
  engine: createJavaScriptRegexEngine(),
})

export type Code = Pick<CodeBlock, 'code' | 'firstLine' | 'occurrences'>

/**
 * Java code as HTML, a line per element of the class `line`, and every identifier that holds a
 * symbol in an element with the attribute `data-symbol` (like `data-symbol="sigma"`). Its colors
 * are CSS variables of both themes, `--shiki-light` and `--shiki-dark`, the page picks between them
 */
export async function highlightJava({ code, firstLine, occurrences }: Code): Promise<string> {
  return (await highlighter).codeToHtml(code, {
    lang: 'java',
    themes: { light: 'github-light-default', dark: 'github-dark-default' },
    defaultColor: false,
    // Shiki counts lines and characters from 0 within the code, lessons from 1 within the file
    decorations: occurrences.map(({ symbol, line, column, length }) => ({
      start: { line: line - firstLine, character: column - 1 },
      end: { line: line - firstLine, character: column - 1 + length },
      properties: { 'data-symbol': symbol },
    })),
  })
}
