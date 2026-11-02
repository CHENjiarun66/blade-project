import { onMounted, onBeforeUnmount } from 'vue'

const BASE = 'width=device-width, initial-scale=1.0, viewport-fit=cover'
const FLAT = `${BASE}, minimum-scale=1.0, maximum-scale=1.0`

// WebKit can restore a PWA with a stale scale or a desktop-sized layout viewport.
// Only flatten entry/resume; release the temporary constraint before a user gesture.
export function usePwaViewportReset(settled: () => void) {
  let meta: HTMLMetaElement | null = null, clamped = false, userPinched = false
  let frame = 0, releaseTimer: ReturnType<typeof setTimeout> | undefined, retryTimer: ReturnType<typeof setTimeout> | undefined
  function enabled() {
    const nav = window.navigator as Navigator & { standalone?: boolean }
    return !!nav && (/iP(hone|ad|od)/.test(nav.userAgent) || (nav.platform === 'MacIntel' && nav.maxTouchPoints > 1)) &&
      (!!nav.standalone || !!window.matchMedia?.('(display-mode: standalone)').matches)
  }
  function typing() {
    const el = document.activeElement
    return el instanceof HTMLInputElement && !el.readOnly || el instanceof HTMLTextAreaElement || el instanceof HTMLElement && el.isContentEditable
  }
  function release(recenter = true) {
    cancelAnimationFrame(frame); frame = 0; clearTimeout(releaseTimer)
    if (!clamped) return
    clamped = false; meta?.setAttribute('content', BASE); if (recenter) window.scrollTo(0, 0); settled()
  }
  function check(force = false) {
    if (!meta || document.hidden || typing() || clamped) return
    const scale = window.visualViewport?.scale || 1
    const portrait = window.innerHeight >= window.innerWidth
    const screen = window.screen
    const expected = screen && (portrait ? Math.min(screen.width, screen.height) : Math.max(screen.width, screen.height))
    const badWidth = !!expected && window.innerWidth > expected + 16
    if (!force && !badWidth && (userPinched || Math.abs(scale - 1) <= .02)) return
    clamped = true; meta.setAttribute('content', FLAT)
    frame = requestAnimationFrame(() => { frame = requestAnimationFrame(() => release()) })
    releaseTimer = setTimeout(() => release(), 250) // Also release if WebKit suspends animation frames.
  }
  function resume() { check(); clearTimeout(retryTimer); retryTimer = setTimeout(() => check(), 500) }
  function visibility() { if (document.hidden) { release(false); clearTimeout(retryTimer) } else resume() }
  function touched(event: TouchEvent) { if (event.touches.length > 1) userPinched = true; release(false) }
  function gesture() { userPinched = true; release(false) }
  function cleanup() {
    release(false); clearTimeout(retryTimer)
    window.removeEventListener('pageshow', resume); window.removeEventListener('orientationchange', resume)
    document.removeEventListener('visibilitychange', visibility); document.removeEventListener('touchstart', touched, true)
    document.removeEventListener('gesturestart', gesture); document.removeEventListener('focusout', resume)
  }
  onMounted(() => {
    if (!enabled()) return
    meta = document.querySelector<HTMLMetaElement>('meta[name="viewport"]')
    if (!meta) return
    check(true); retryTimer = setTimeout(() => check(), 500)
    window.addEventListener('pageshow', resume); window.addEventListener('orientationchange', resume)
    document.addEventListener('visibilitychange', visibility); document.addEventListener('touchstart', touched, { capture: true, passive: true })
    document.addEventListener('gesturestart', gesture, { passive: true }); document.addEventListener('focusout', resume)
  })
  onBeforeUnmount(cleanup)
}
