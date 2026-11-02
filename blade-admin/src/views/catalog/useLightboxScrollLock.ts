import { onBeforeUnmount, type Ref } from 'vue'

export function useLightboxScrollLock(page: Ref<HTMLElement | undefined>) {
  let restore: (() => void) | undefined
  function blockTouch(event: TouchEvent) {
    if (event.touches.length === 1 && Math.abs((window.visualViewport?.scale || 1) - 1) <= .02 && event.cancelable) event.preventDefault()
  }
  function blockWheel(event: WheelEvent) { if (!event.ctrlKey && event.cancelable) event.preventDefault() }
  function unlock() { restore?.(); restore = undefined }
  function lock() {
    if (restore) return
    const x = window.scrollX, y = window.scrollY
    const elements = [document.documentElement, document.body, ...Array.from(page.value?.querySelectorAll<HTMLElement>('.grid-area,.panel-scroll') || [])]
    const saved = elements.map(element => ({ element, overflow: element.style.overflow, overscroll: element.style.overscrollBehavior, top: element.scrollTop, left: element.scrollLeft }))
    const bodyStyle = document.body.style
    const position = { position: bodyStyle.position, top: bodyStyle.top, left: bodyStyle.left, width: bodyStyle.width }
    elements.forEach(element => { element.style.overflow = 'hidden'; element.style.overscrollBehavior = 'none' })
    Object.assign(bodyStyle, { position: 'fixed', top: `${-y}px`, left: `${-x}px`, width: '100%' })
    // Capture outside the dialog too: Safari can target the backdrop when the finger leaves the photo.
    document.addEventListener('touchmove', blockTouch, { passive: false })
    document.addEventListener('wheel', blockWheel, { passive: false })
    restore = () => {
      document.removeEventListener('touchmove', blockTouch); document.removeEventListener('wheel', blockWheel)
      Object.assign(bodyStyle, position)
      saved.forEach(({ element, overflow, overscroll, top, left }) => { element.style.overflow = overflow; element.style.overscrollBehavior = overscroll; element.scrollTop = top; element.scrollLeft = left })
      window.scrollTo(x, y)
    }
  }
  onBeforeUnmount(unlock)
  return { lock, unlock }
}
