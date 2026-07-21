/**
 * Keeps HTML number-input values non-negative while typing.
 * Allows empty string and intermediate decimals; strips a leading minus.
 */
export function sanitizeNonNegativeNumberInput(raw: string): string {
  if (raw.trim() === '') return ''
  const cleaned = raw.replace(/^-/, '')
  if (cleaned === '' || cleaned === '.') return cleaned
  if (!/^\d*\.?\d*$/.test(cleaned)) return cleaned.replace(/[^\d.]/g, '')
  const n = Number(cleaned)
  if (Number.isFinite(n) && n < 0) return String(Math.abs(n))
  return cleaned
}
