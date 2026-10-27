import { roundForReading } from '@/numbers'

/** start of the mark of a symbol in TeX, like `\class{sigma}{\sigma}` */
const MARK = '\\class{'

/**
 * What a negative number can follow without parentheses, at the end of the TeX before it: nothing,
 * an equals sign, an opening bracket or brace, a comma, an alignment point, a line break or the
 * start of an environment. After anything else (like an operator) it needs them
 */
const OPENING = /(?:^|[=([{,&]|\\\\|\\begin\{[a-z]+\})$/

/**
 * Writes the values of symbols into a TeX formula, as a calculation of a lesson shows it: every
 * symbol whose mark it finds a finite value for, like `\class{x}{x}` for `{ x: -1.07 }`, turns into
 * its value, rounded like readouts are: `\class{x}{-1.07}`, or `\class{x}{(-1.07)}` where a
 * negative value needs parentheses (as after an operator). The marks stay so the values light up
 * like their symbols do. Symbols without a value stay as they are, with the values written into the
 * marks within them
 *
 * @param values by the names of their symbols
 */
export function substituteValues(
  formula: string,
  values: Readonly<Record<string, number>>,
): string {
  let substituted = ''
  let index = 0
  for (let mark = formula.indexOf(MARK); mark >= 0; mark = formula.indexOf(MARK, index)) {
    const nameStart = mark + MARK.length
    const nameEnd = formula.indexOf('}', nameStart)
    if (nameEnd < 0 || formula[nameEnd + 1] !== '{') {
      // no symbol follows the name, so the mark stays as it is for KaTeX to render
      substituted += formula.slice(index, nameStart)
      index = nameStart
      continue
    }
    const symbolStart = nameEnd + 2
    const symbolEnd = closingBrace(formula, symbolStart)
    const value = values[formula.slice(nameStart, nameEnd)]
    substituted +=
      formula.slice(index, symbolStart) +
      (value !== undefined && Number.isFinite(value)
        ? texNumber(value, OPENING.test(formula.slice(0, mark).trimEnd()))
        : substituteValues(formula.slice(symbolStart, symbolEnd), values))
    index = symbolEnd
  }
  return substituted + formula.slice(index)
}

/**
 * Index of the brace that closes a TeX group starting at the given index right after its opening
 * brace, the end of the formula if no brace closes it. Braces after a backslash (as in `\{`) belong
 * to the text of the formula
 */
function closingBrace(formula: string, start: number): number {
  let depth = 1
  for (let index = start; index < formula.length; index++) {
    switch (formula[index]) {
      case '\\':
        index++
        break
      case '{':
        depth++
        break
      case '}':
        depth--
        if (depth === 0) {
          return index
        }
        break
    }
  }
  return formula.length
}

/**
 * Writes a finite number in TeX, rounded for reading
 *
 * @param opensTerm the number opens a term, so it needs no parentheses if negative
 */
function texNumber(value: number, opensTerm: boolean): string {
  const { negative, digits, exponent } = roundForReading(value)
  // braces around the comma keep TeX from spacing it as punctuation
  const magnitude =
    digits.replace(/,/g, '{,}') + (exponent === 0 ? '' : ` \\cdot 10^{${String(exponent)}}`)
  if (!negative) {
    return magnitude
  }
  return opensTerm ? `-${magnitude}` : `(-${magnitude})`
}
