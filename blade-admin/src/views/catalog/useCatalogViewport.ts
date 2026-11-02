import { onMounted, onBeforeUnmount, type Ref } from 'vue'

// Fit the real viewport on entry/rotation, then recover after native text entry. Never resize for pinch zoom.
export function useCatalogViewport(root: Ref<HTMLElement | undefined>) {
  let recovering = false, keyboardSeen = false, frame = 0, timer: ReturnType<typeof setTimeout> | undefined
  const viewport = window.visualViewport
  function editable() {
    const el = document.activeElement
    return el instanceof HTMLTextAreaElement || (el instanceof HTMLInputElement && !el.readOnly && !['checkbox','radio','button','submit'].includes(el.type)) || (el instanceof HTMLElement && el.isContentEditable)
  }
  function fitHeight() {
    const standalone = window.matchMedia?.('(display-mode: standalone)').matches || (window.navigator as Navigator & { standalone?: boolean })?.standalone
    // Home-screen Safari can report a dvh smaller than its layout viewport on initial launch.
    const height = standalone ? Math.max(window.innerHeight, document.documentElement.clientHeight, viewport?.height || 0) : viewport?.height || window.innerHeight
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
    const height = Math.round(Math.max(layoutHeight, viewport?.height || 0))
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
  onMounted(() => {
    restore()
    document.addEventListener('focusin', focused); document.addEventListener('focusout', blurred)
    viewport?.addEventListener('resize', schedule); window.addEventListener('resize', resized)
  })
  onBeforeUnmount(() => {
    cancelAnimationFrame(frame); clearTimeout(timer)
    document.removeEventListener('focusin', focused); document.removeEventListener('focusout', blurred)
    viewport?.removeEventListener('resize', schedule); window.removeEventListener('resize', resized)
  })
}
