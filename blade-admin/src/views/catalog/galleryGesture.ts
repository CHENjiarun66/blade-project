export type GestureAxis = 'pending' | 'vertical' | 'horizontal'
export function gestureAxis(dx: number, dy: number, previous: GestureAxis): GestureAxis {
  if (previous !== 'pending') return previous
  const x = Math.abs(dx), y = Math.abs(dy)
  if (y >= 8 && y >= x * 1.05) return 'vertical'
  if (x >= 12 && x >= y * 1.5) return 'horizontal'
  if (Math.hypot(x, y) >= 20) return 'vertical'
  return 'pending'
}
export function swipeTarget(axis: GestureAxis, dx: number, velocity: number, width: number, index: number, count: number) {
  if (axis !== 'horizontal' || count <= 1) return index
  const threshold = Math.max(44, Math.min(88, width * .18))
  const flick = Math.abs(dx) >= 26 && Math.abs(velocity) >= .55 && Math.sign(dx) === Math.sign(velocity)
  if (Math.abs(dx) < threshold && !flick) return index
  return Math.max(0, Math.min(count - 1, index + (dx < 0 ? 1 : -1)))
}
export function swipeOffset(dx: number, index: number, count: number, width: number) {
  const edge = (index === 0 && dx > 0) || (index === count - 1 && dx < 0)
  return edge ? Math.sign(dx) * Math.min(Math.abs(dx) * .22, width * .12) : Math.max(-width * .96, Math.min(width * .96, dx))
}
export function recentVelocity(samples: { x: number; time: number }[], now: number) {
  const recent = samples.filter(s => now - s.time <= 100)
  const first = recent[0], last = recent[recent.length - 1]
  return first && last && last.time > first.time ? (last.x - first.x) / (last.time - first.time) : 0
}
