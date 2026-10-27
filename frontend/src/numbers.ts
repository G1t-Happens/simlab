/** significant digits of the numbers the readouts and the calculations of lessons show */
const SIGNIFICANT_DIGITS = 3

/** whole numbers below this one show all their digits */
const WHOLE_LIMIT = 1e6

/** groups the digits of a whole number in threes, the English way, like 35,199 */
const GROUPED = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 })

/** multiplication sign before a power of ten, between non-breaking spaces */
const TIMES = '\u00a0·\u00a0'

const SUPERSCRIPTS: Readonly<Record<string, string>> = {
  '-': '⁻',
  '0': '⁰',
  '1': '¹',
  '2': '²',
  '3': '³',
  '4': '⁴',
  '5': '⁵',
  '6': '⁶',
  '7': '⁷',
  '8': '⁸',
  '9': '⁹',
}

/** a number rounded for reading, in parts that text and TeX each write their own way */
export interface RoundedNumber {
  readonly negative: boolean
  /** digits of the magnitude, like `35,199` or `6.20` */
  readonly digits: string
  /** power of ten that multiplies the digits, 0 if none does */
  readonly exponent: number
}

/**
 * Rounds a finite number for reading: a whole number below a million keeps all its digits in groups
 * of three, every other number keeps three significant digits and from 1,000 on or below 0.001 it's
 * written as a power of ten, like 6.20 · 10⁸
 */
export function roundForReading(value: number): RoundedNumber {
  const magnitude = Math.abs(value)
  const negative = value < 0
  if (Number.isInteger(magnitude) && magnitude < WHOLE_LIMIT) {
    return { negative, digits: GROUPED.format(magnitude), exponent: 0 }
  }
  // the exponent after rounding, which rounding can raise, e.g. from 999.6 to 1.00 · 10³
  const [significand = '', exponent = ''] = magnitude
    .toExponential(SIGNIFICANT_DIGITS - 1)
    .split('e')
  const power = Number(exponent)
  if (power >= -3 && power < 3) {
    return { negative, digits: magnitude.toPrecision(SIGNIFICANT_DIGITS), exponent: 0 }
  }
  return { negative, digits: significand, exponent: power }
}

/**
 * Writes a finite number as text rounded for reading, like `21.4`, `−1.07`, `35,199` or
 * `6.20 · 10⁸`, with a minus sign and non-breaking spaces
 */
export function formatNumber(value: number): string {
  const { negative, digits, exponent } = roundForReading(value)
  const power = exponent === 0 ? '' : `${TIMES}10${superscript(exponent)}`
  return `${negative ? '−' : ''}${digits}${power}`
}

function superscript(exponent: number): string {
  return String(exponent).replace(/[-\d]/g, (character) => SUPERSCRIPTS[character] ?? character)
}
