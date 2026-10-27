import { isRecord } from './json'
import { isLesson, type Lesson } from './lessons'

/**
 * When a new value of a parameter takes effect: at once while the simulation runs, or when it
 * starts over (e.g. for the arrangement it starts from)
 */
export type Effect = 'LIVE' | 'RESTART'

interface ParameterBase {
  readonly name: string
  /** short human-readable name, like a Greek letter */
  readonly label: string
  readonly defaultValue: number
  readonly effect: Effect
}

/** any number between two bounds */
export interface RangeParameter extends ParameterBase {
  readonly kind: 'Range'
  /** like `m/s`, empty for dimensionless parameters */
  readonly unit: string
  readonly min: number
  readonly max: number
}

export interface ParameterOption {
  readonly value: number
  /** human-readable name of the option, like `Euler` */
  readonly label: string
}

/** one of a few numbers, each of which names an option */
export interface ChoiceParameter extends ParameterBase {
  readonly kind: 'Choice'
  /** at least two, in the order to offer them */
  readonly options: readonly ParameterOption[]
}

/** a parameter with the values the server accepts, told apart by its kind */
export type ParameterInfo = RangeParameter | ChoiceParameter

export interface SimulationInfo {
  /** identifies the simulation, like `lorenz`, also in the address of its page */
  readonly type: string
  readonly name: string
  readonly description: string
  /** unit of the simulated time like `s`, empty if the time has no unit */
  readonly timeUnit: string
  readonly parameters: readonly ParameterInfo[]
}

/** an open room, the browser joins it through the room's WebSocket */
export interface Room {
  readonly id: string
  readonly type: string
}

/** the server answered with an error status */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export async function fetchSimulations(): Promise<SimulationInfo[]> {
  const body = await requestJson('/api/simulations')
  if (!(Array.isArray(body) && body.every(isSimulationInfo))) {
    throw new Error('The server answered with an invalid list of simulations')
  }
  return body
}

export async function fetchLesson(type: string): Promise<Lesson> {
  const body = await requestJson(`/api/simulations/${encodeURIComponent(type)}/lesson`)
  if (!isLesson(body)) {
    throw new Error('The server answered with an invalid lesson')
  }
  return body
}

/**
 * Opens a room for a simulation, which closes again unless the browser joins it soon
 *
 * @param parameters values that replace the defaults of parameters, by name
 */
export async function openRoom(type: string, parameters?: Record<string, number>): Promise<Room> {
  const body = await requestJson('/api/rooms', { type, parameters })
  if (!isRoom(body)) {
    throw new Error('The server answered with an invalid room')
  }
  return body
}

/** GET, or POST if there's a body. Returns the parsed response */
async function requestJson(url: string, body?: unknown): Promise<unknown> {
  const response = await fetch(
    url,
    body === undefined
      ? { headers: { Accept: 'application/json' } }
      : {
          method: 'POST',
          headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
          body: JSON.stringify(body),
        },
  )
  if (!response.ok) {
    throw new ApiError(response.status, await errorMessage(response))
  }
  return response.json()
}

/** detail of a problem document (RFC 9457), which the server sends with errors */
async function errorMessage(response: Response): Promise<string> {
  const problem: unknown = await response.json().catch(() => undefined)
  if (isRecord(problem) && typeof problem.detail === 'string') {
    return problem.detail
  }
  return `Request failed with HTTP status ${String(response.status)}`
}

const EFFECTS: readonly unknown[] = ['LIVE', 'RESTART'] satisfies Effect[]

function isParameterInfo(value: unknown): value is ParameterInfo {
  if (!(
    isRecord(value) &&
    typeof value.name === 'string' &&
    typeof value.label === 'string' &&
    typeof value.defaultValue === 'number' &&
    EFFECTS.includes(value.effect)
  )) {
    return false
  }
  switch (value.kind) {
    case 'Range':
      return (
        typeof value.unit === 'string' &&
        typeof value.min === 'number' &&
        typeof value.max === 'number'
      )
    case 'Choice':
      return (
        Array.isArray(value.options) &&
        value.options.length >= 2 &&
        value.options.every(isParameterOption)
      )
    default:
      return false
  }
}

function isParameterOption(value: unknown): value is ParameterOption {
  return isRecord(value) && typeof value.value === 'number' && typeof value.label === 'string'
}

function isSimulationInfo(value: unknown): value is SimulationInfo {
  return (
    isRecord(value) &&
    typeof value.type === 'string' &&
    typeof value.name === 'string' &&
    typeof value.description === 'string' &&
    typeof value.timeUnit === 'string' &&
    Array.isArray(value.parameters) &&
    value.parameters.every(isParameterInfo)
  )
}

function isRoom(value: unknown): value is Room {
  return isRecord(value) && typeof value.id === 'string' && typeof value.type === 'string'
}
