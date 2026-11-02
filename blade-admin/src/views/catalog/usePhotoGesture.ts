import { onMounted, onBeforeUnmount, type Ref } from 'vue'
import { gestureAxis, swipeOffset, swipeTarget, recentVelocity, type GestureAxis } from './galleryGesture'

type Point = { clientX: number; clientY: number }
type Options = { root: Ref<HTMLElement | undefined>; track: Ref<HTMLElement | undefined>; index: () => number; count: () => number; lightbox: () => boolean; setIndex: (index: number) => void; dragging: Ref<boolean> }
export function usePhotoGesture(options: Options) {
  let active: { kind: 'touch' | 'pointer'; id: number; x: number; y: number; dx: number; dy: number; width: number; index: number; axis: GestureAxis; originOffset: number; samples: { x: number; time: number }[] } | null = null
  let frame = 0, blockedUntil = 0
  const zoomed = () => Math.abs((window.visualViewport?.scale || 1) - 1) > .02
  function begin(kind: 'touch' | 'pointer', id: number, point: Point, time: number, target: EventTarget | null) {
    reset()
    blockedUntil = 0
    const root = options.root.value
    if (!root || zoomed() || options.count() < 2 || (target instanceof Element && target.closest('button,a,input'))) return
    // A hidden card must not acquire the gesture owned by the top-layer image viewer.
    if (document.querySelector('dialog[open]') && !root.closest('dialog[open]')) return
    active = { kind, id, x: point.clientX, y: point.clientY, dx: 0, dy: 0, width: root.getBoundingClientRect().width, index: options.index(), axis: 'pending', originOffset: 0, samples: [{ x: point.clientX, time }] }
    document.addEventListener('touchstart', anotherTouch, { passive: true })
  }
  function update(point: Point, time: number) {
    const a = active
    if (!a) return
    a.dx = point.clientX - a.x; a.dy = point.clientY - a.y
    a.samples.push({ x: point.clientX, time }); a.samples = a.samples.slice(-12)
    const previous = a.axis
    a.axis = gestureAxis(a.dx, a.dy, a.axis, options.lightbox())
    if (a.axis !== 'horizontal') return
    if (previous !== 'horizontal') {
      const transform = options.track.value && getComputedStyle(options.track.value).transform
      // Continue from the current visual position when a second swipe interrupts settling.
      a.originOffset = transform && transform !== 'none' ? new DOMMatrixReadOnly(transform).m41 + a.index * a.width : 0
      options.dragging.value = true
    }
    if (!frame) frame = requestAnimationFrame(() => {
      frame = 0
      if (active === a && options.track.value) options.track.value.style.transform = `translate3d(calc(${-a.index * 100}% + ${a.originOffset + swipeOffset(a.dx, a.index, options.count(), a.width)}px),0,0)`
    })
  }
  function finish(cancelled: boolean) {
    const a = active; active = null
    cancelAnimationFrame(frame); frame = 0; options.dragging.value = false
    document.removeEventListener('touchstart', anotherTouch)
    document.removeEventListener('pointermove', pointerMove); document.removeEventListener('pointerup', pointerEnd)
    document.removeEventListener('pointercancel', pointerCancel)
    if (!a) return
    if (Math.hypot(a.dx, a.dy) >= 6 || cancelled) blockedUntil = performance.now() + 450
    const lastTime = a.samples[a.samples.length - 1]?.time || 0
    const target = cancelled ? a.index : swipeTarget(a.axis, a.dx, recentVelocity(a.samples, lastTime), a.width, a.index, options.count())
    if (options.track.value) options.track.value.style.transform = `translate3d(${-target * 100}%,0,0)`
    if (!cancelled) options.setIndex(target)
  }
  function pointerStart(event: PointerEvent) {
    if (event.pointerType === 'touch' && 'ontouchstart' in window) return
    if (event.isPrimary === false || event.button !== 0) { reset(); return }
    begin('pointer', event.pointerId, event, event.timeStamp, event.target)
    if (!active) return
    document.addEventListener('pointermove', pointerMove, { passive: false })
    document.addEventListener('pointerup', pointerEnd); document.addEventListener('pointercancel', pointerCancel)
  }
  function pointerMove(event: PointerEvent) {
    if (active?.kind !== 'pointer' || event.pointerId !== active.id) return
    update(event, event.timeStamp)
    if (active?.axis === 'horizontal' && event.cancelable) event.preventDefault()
  }
  function pointerEnd(event: PointerEvent) { if (active?.kind === 'pointer' && event.pointerId === active.id) { update(event, event.timeStamp); finish(false) } }
  function pointerCancel(event: PointerEvent) { if (active?.kind === 'pointer' && event.pointerId === active.id) reset() }
  function touchStart(event: TouchEvent) { if (event.touches.length !== 1) { reset(); return }; const t = event.touches[0]!; begin('touch', t.identifier, t, event.timeStamp, event.target) }
  function anotherTouch(event: TouchEvent) { if (event.touches.length > 1) reset() }
  function touchMove(event: TouchEvent) {
    if (active?.kind !== 'touch') return
    if (event.touches.length !== 1 || zoomed()) { reset(); return }
    const point = Array.from(event.touches).find(t => t.identifier === active!.id)
    if (!point) return
    update(point, event.timeStamp)
    // The non-passive Touch listener decides direction before native scrolling takes over.
    if (active?.axis === 'horizontal' && event.cancelable) event.preventDefault()
  }
  function touchEnd(event: TouchEvent) {
    if (active?.kind !== 'touch') return
    const point = Array.from(event.changedTouches).find(t => t.identifier === active!.id)
    if (!point) return
    update(point, event.timeStamp)
    if (active?.axis === 'horizontal' && event.cancelable) event.preventDefault()
    finish(false)
  }
  function reset() { finish(true) }
  function visibility() { if (document.hidden) reset() }
  function guardClick(event: MouseEvent) { if (event.detail && performance.now() < blockedUntil) { blockedUntil = 0; event.preventDefault(); event.stopImmediatePropagation() } }
  onMounted(() => {
    const root = options.root.value
    root?.addEventListener('touchstart', touchStart, { passive: true })
    root?.addEventListener('touchmove', touchMove, { passive: false })
    root?.addEventListener('touchend', touchEnd, { passive: false }); root?.addEventListener('touchcancel', reset)
    window.addEventListener('resize', reset); window.addEventListener('blur', reset); document.addEventListener('visibilitychange', visibility)
  })
  onBeforeUnmount(() => {
    reset()
    const root = options.root.value
    root?.removeEventListener('touchstart', touchStart); root?.removeEventListener('touchmove', touchMove)
    root?.removeEventListener('touchend', touchEnd); root?.removeEventListener('touchcancel', reset)
    window.removeEventListener('resize', reset); window.removeEventListener('blur', reset); document.removeEventListener('visibilitychange', visibility)
  })
  return { pointerStart, guardClick, reset }
}
