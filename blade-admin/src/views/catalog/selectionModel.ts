import type { CatalogProductVO } from '@/api/catalog'

export interface SelectionLine {
  product: CatalogProductVO
  checked: boolean
  colorNote: string
  qty: string
  unitPrice: string
  unavailable?: boolean
}
export const MAX_SELECTION = 200
export function quantity(value: string): number | null {
  if (!/^\d+$/.test(value.trim())) return null
  const n = Number(value)
  return Number.isInteger(n) && n > 0 && n <= 999999 ? n : null
}
export function priceCents(value: string): bigint | null {
  const text = value.trim()
  if (!/^\d{1,6}(?:\.\d{0,2})?$/.test(text)) return null
  const [whole = '0', fraction = ''] = text.split('.')
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'))
}
export function decimalPrice(cents: bigint) {
  return `${cents / 100n}.${String(cents % 100n).padStart(2, '0')}`
}
export function money(cents: bigint | null): string {
  if (cents === null) return '待报价'
  return `${(cents / 100n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${String(cents % 100n).padStart(2, '0')}`
}
export function lineAmount(line: SelectionLine): bigint | null {
  const qty = quantity(line.qty), price = priceCents(line.unitPrice)
  return qty === null || price === null ? null : BigInt(qty) * price
}
export function lineStatus(line: SelectionLine) {
  if (line.unavailable) return '款式已不可用'
  if (quantity(line.qty) === null) return line.qty ? '数量格式需调整' : '待填数量'
  if (priceCents(line.unitPrice) === null) return line.unitPrice ? '单价格式需调整' : '待报价'
  if (!line.colorNote.trim()) return '颜色待确认'
  return '已填写'
}
export function selectionTotals(lines: SelectionLine[]) {
  const chosen = lines.filter(line => line.checked)
  return {
    styles: chosen.length,
    qty: chosen.reduce((n, line) => n + (quantity(line.qty) || 0), 0),
    amount: chosen.reduce((n, line) => n + (lineAmount(line) ?? 0n), 0n),
    pending: chosen.filter(line => lineAmount(line) === null).length,
    colorPending: chosen.filter(line => !line.colorNote.trim()).length,
  }
}
export function blankLine(product: CatalogProductVO): SelectionLine {
  return { product, checked: true, colorNote: '', qty: '', unitPrice: '' }
}
