/** narrows parsed JSON to an object whose properties can then be checked one by one */
export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
