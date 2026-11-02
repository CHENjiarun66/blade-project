import { quantity, priceCents, decimalPrice } from './selectionModel'
import type { NumberMode } from './numericEntry'
export function keypadValue(current: string, key: string, mode: NumberMode, replace = false): string {
  if (key === 'clear') return ''
  if (key === 'backspace') return replace ? '' : current.slice(0, -1)
  const value = replace ? '' : current
  if (key === '.') return mode === 'price' && !value.includes('.') ? (value || '0') + '.' : value
  if (!/^\d{1,2}$/.test(key)) return current
  let next = value === '0' && !key.startsWith('0') ? key : value + key
  next = next.replace(/^0+(?=\d)/, '')
  const pattern = mode === 'price' ? /^\d{1,6}(?:\.\d{0,2})?$/ : /^\d{1,6}$/
  return pattern.test(next) ? next : value
}
export function validKeypadValue(value: string, mode: NumberMode) {
  return value === '' || (mode === 'quantity' ? quantity(value) !== null : priceCents(value) !== null)
}
export function committedKeypadValue(value: string, mode: NumberMode) {
  if (!value) return ''
  return mode === 'quantity' ? String(quantity(value)) : decimalPrice(priceCents(value)!)
}
