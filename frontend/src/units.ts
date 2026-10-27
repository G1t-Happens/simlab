/** units that follow their number without a space: degrees, minutes and seconds of arc */
const UNSPACED_UNITS: readonly string[] = ['°', '′', '″']

/**
 * Writes a number with its unit, like `8 m/s` or `180°`, as the SI Brochure (9th edition, section
 * 5.4.3) asks: with a space in between except before degrees, minutes and seconds of plane angle.
 * The space doesn't break, so the unit stays on the line of its number
 *
 * @param unit empty for a dimensionless number
 */
export function withUnit(number: string, unit: string): string {
  return unit === '' || UNSPACED_UNITS.includes(unit) ? number + unit : `${number}\u00a0${unit}`
}
