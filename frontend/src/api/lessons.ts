import { isRecord } from './json'

/**
 * The lesson of a simulation: text with formulas, the code of the project it explains, experiments
 * with the simulation and calculations with its live values
 */
export interface Lesson {
  /** symbols the formulas mark, the code, the controls and the readouts show them too */
  readonly symbols: readonly LessonSymbol[]
  readonly blocks: readonly LessonBlock[]
}

/** a symbol that formulas mark, like `\class{sigma}{\sigma}` */
export interface LessonSymbol {
  /** the name in the mark, like `sigma` */
  readonly name: string
  /** parameter of the simulation the symbol stands for, `null` if none */
  readonly parameter: string | null
  /** readout of the simulation the symbol stands for, `null` if none */
  readonly readout: string | null
}

/**
 * Markdown with formulas in TeX between `$` within a line and between `$$` on lines of their own,
 * as GitHub writes them
 */
export interface TextBlock {
  readonly kind: 'Text'
  readonly markdown: string
}

/** where code lies: in the package of one simulation, or in the toolkit every one can use */
export type Origin = 'SIMULATION' | 'TOOLKIT'

/** A method of the project from the comment above it to its closing brace, as the file writes it */
export interface CodeBlock {
  readonly kind: 'Code'
  /** the method as the lesson refers to it, like `RungeKutta4#step` */
  readonly reference: string
  /** below the source root, like `dev/simlab/core/toolkit/ode/RungeKutta4.java` */
  readonly path: string
  /** counted from 1 */
  readonly firstLine: number
  readonly lastLine: number
  readonly code: string
  readonly origin: Origin
  /** the lines on GitHub at the commit the server was built from, `null` if that's unknown */
  readonly url: string | null
  /** where the symbols of the lesson occur in the code */
  readonly occurrences: readonly Occurrence[]
}

/** an identifier in code that holds a symbol of the lesson */
export interface Occurrence {
  readonly symbol: string
  /** line in the file, counted from 1 */
  readonly line: number
  /** first character of the identifier in the line, counted from 1 */
  readonly column: number
  readonly length: number
}

/**
 * An experiment with the simulation: a question to predict the answer to, the parameter values that
 * answer it, and why the simulation behaves as it does. Its text is Markdown with formulas
 */
export interface ExperimentBlock {
  readonly kind: 'Experiment'
  readonly question: string
  /** parameter values the experiment sets, by name. The other parameters take their defaults */
  readonly values: Readonly<Record<string, number>>
  readonly explanation: string
}

/**
 * A formula with the live values of its symbols: the value of every symbol it marks (that of a
 * parameter or a readout) stands in place of the symbol, as long as the value is known
 */
export interface CalculationBlock {
  readonly kind: 'Calculation'
  /** TeX, shown on lines of its own */
  readonly formula: string
}

/** a part of a lesson, told apart by its kind */
export type LessonBlock = TextBlock | CodeBlock | ExperimentBlock | CalculationBlock

/**
 * Narrows parsed JSON to a lesson whose code holds as many lines as its line numbers say, with its
 * symbols at places within these lines
 */
export function isLesson(value: unknown): value is Lesson {
  return (
    isRecord(value) &&
    Array.isArray(value.symbols) &&
    value.symbols.every(isLessonSymbol) &&
    Array.isArray(value.blocks) &&
    value.blocks.every(isLessonBlock)
  )
}

const ORIGINS: readonly unknown[] = ['SIMULATION', 'TOOLKIT'] satisfies Origin[]

function isLessonSymbol(value: unknown): value is LessonSymbol {
  return (
    isRecord(value) &&
    typeof value.name === 'string' &&
    (value.parameter === null || typeof value.parameter === 'string') &&
    (value.readout === null || typeof value.readout === 'string')
  )
}

function isLessonBlock(value: unknown): value is LessonBlock {
  if (!isRecord(value)) {
    return false
  }
  switch (value.kind) {
    case 'Text':
      return typeof value.markdown === 'string'
    case 'Code':
      return isCodeBlock(value)
    case 'Experiment':
      return (
        typeof value.question === 'string' &&
        isRecord(value.values) &&
        Object.values(value.values).every(Number.isFinite) &&
        typeof value.explanation === 'string'
      )
    case 'Calculation':
      return typeof value.formula === 'string'
    default:
      return false
  }
}

function isCodeBlock(value: Record<string, unknown>): boolean {
  return (
    typeof value.reference === 'string' &&
    typeof value.path === 'string' &&
    isPositiveInteger(value.firstLine) &&
    isPositiveInteger(value.lastLine) &&
    typeof value.code === 'string' &&
    ORIGINS.includes(value.origin) &&
    (value.url === null || isSecureUrl(value.url)) &&
    Array.isArray(value.occurrences) &&
    fitsLines(value.code, value.firstLine, value.lastLine, value.occurrences)
  )
}

/** code holds the lines its numbers say, and every occurrence a place in them */
function fitsLines(
  code: string,
  firstLine: number,
  lastLine: number,
  occurrences: readonly unknown[],
): boolean {
  const lines = code.split('\n')
  return (
    lines.length === lastLine - firstLine + 1 &&
    occurrences.every(
      (occurrence) =>
        isOccurrence(occurrence) &&
        occurrence.column + occurrence.length - 1 <=
          (lines[occurrence.line - firstLine]?.length ?? 0),
    )
  )
}

function isOccurrence(value: unknown): value is Occurrence {
  return (
    isRecord(value) &&
    typeof value.symbol === 'string' &&
    isPositiveInteger(value.line) &&
    isPositiveInteger(value.column) &&
    isPositiveInteger(value.length)
  )
}

/** whole number from 1 on, like the number of a line or a column */
function isPositiveInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 1
}

/** HTTPS address, which a link opens without running a script in the page */
function isSecureUrl(value: unknown): value is string {
  return typeof value === 'string' && value.startsWith('https://')
}
