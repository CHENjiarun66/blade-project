/**
 * 格式化日期，只显示年-月-日
 * @param dateStr ISO格式日期字符串，如 "2026-04-19T15:23:42"
 */
export function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return '-'
  return dateStr.split('T')[0]
}
