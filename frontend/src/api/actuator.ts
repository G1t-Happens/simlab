import { isRecord } from './json'

const HEALTH_ENDPOINT = '/actuator/health'

/** health statuses predefined by Spring Boot Actuator */
const HEALTH_STATUSES = ['UP', 'DOWN', 'OUT_OF_SERVICE', 'UNKNOWN'] as const

export type HealthStatus = (typeof HEALTH_STATUSES)[number]

/**
 * Fetches the aggregated health status of the backend.
 *
 * Actuator answers with `503 Service Unavailable` if the backend is down or out of service and
 * reports the status in the body either way. Custom statuses are reported as `UNKNOWN`
 *
 * @throws if the backend can't be reached or doesn't answer with a health document
 */
export async function fetchHealthStatus(signal?: AbortSignal): Promise<HealthStatus> {
  // a cached answer would hide that the backend went away or came back
  const response = await fetch(HEALTH_ENDPOINT, {
    headers: { Accept: 'application/json' },
    cache: 'no-store',
    signal,
  })
  if (!response.ok && response.status !== 503) {
    throw new Error(`Health check failed with HTTP status ${String(response.status)}`)
  }
  const body: unknown = await response.json()
  if (!isHealthDocument(body)) {
    throw new Error('Health check did not return a health document')
  }
  return isHealthStatus(body.status) ? body.status : 'UNKNOWN'
}

function isHealthDocument(value: unknown): value is { status: string } {
  return isRecord(value) && typeof value.status === 'string'
}

function isHealthStatus(value: string): value is HealthStatus {
  return (HEALTH_STATUSES as readonly string[]).includes(value)
}
