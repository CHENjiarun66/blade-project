import { onMounted, onBeforeUnmount, type Ref } from 'vue'
import { usePwaViewportReset } from './usePwaViewportReset'

// Fit the real viewport on entry/rotation, then recover after native text entry. Never resize for pinch zoom.
export function useCatalogViewport(root: Ref<HTMLElement | undefined>) {
  let recovering = false, keyboardSeen = false, frame = 0, timer: ReturnType<typeof setTimeout> | undefined
  const viewport = window.visualViewport
  function editable() {
    const el = document.activeElement
    return el instanceof HTMLTextAreaElement || (el instanceof HTMLInputElement && !el.readOnly && !['checkbox','radio','button','submit'].includes(el.type)) || (el instanceof HTMLElement && el.isContentEditable)
  }
  function fitHeight() {
    // Use only the rectangle actually exposed by the browser; do not add the native status bar.
    const height = viewport?.height || window.innerHeight
    root.value?.style.setProperty('--catalog-viewport-height', `${Math.round(height)}px`)
  }
  function restore() {
    if (viewport && Math.abs(viewport.scale - 1) > .02) return
    if (!recovering) { if (!editable()) fitHeight(); return }
    const layoutHeight = Math.max(window.innerHeight, document.documentElement.clientHeight)
    // Safari animates keyboard dismissal; wait until the visible viewport expands again.
    if (viewport && viewport.height < layoutHeight - 100) { keyboardSeen = keyboardSeen || editable(); return }
    // iPad's dedicated keyboard-hide button may leave the input focused.
    if (editable() && !keyboardSeen) return
    if (editable() && document.activeElement instanceof HTMLElement) document.activeElement.blur()
    const height = Math.round(viewport?.height || window.innerHeight)
    root.value?.style.setProperty('--catalog-viewport-height', `${height}px`)
    window.scrollTo(0, 0)
    recovering = false; keyboardSeen = false
  }
  function schedule() { cancelAnimationFrame(frame); frame = requestAnimationFrame(restore) }
  function focused() { if (editable()) { recovering = true; keyboardSeen = keyboardSeen || (!!viewport && viewport.height < window.innerHeight - 100) } }
  function blurred() { if (recovering) { schedule(); clearTimeout(timer); timer = setTimeout(schedule, 350) } }
  function resized() {
    if (!editable() && (!viewport || Math.abs(viewport.scale - 1) <= .02)) {
      schedule()
    }
  }
  usePwaViewportReset(schedule)
  const ownedHosts: HTMLElement[] = []
  onMounted(() => {
    for (const host of [document.documentElement, document.body]) {
      if (!host.classList.contains('catalog-host')) { host.classList.add('catalog-host'); ownedHosts.push(host) }
    }
    restore()
    document.addEventListener('focusin', focused); document.addEventListener('focusout', blurred)
    viewport?.addEventListener('resize', schedule); window.addEventListener('resize', resized)
  })
  onBeforeUnmount(() => {
    ownedHosts.forEach(host => host.classList.remove('catalog-host'))
    cancelAnimationFrame(frame); clearTimeout(timer)
    document.removeEventListener('focusin', focused); document.removeEventListener('focusout', blurred)
    viewport?.removeEventListener('resize', schedule); window.removeEventListener('resize', resized)
  })
}
