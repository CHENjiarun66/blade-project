type IconDef = {
  viewBox?: string
  body: string
}

const iconMap: Record<string, IconDef> = {
  add: { body: '<path d="M12 5v14M5 12h14"/>' },
  add_circle: { body: '<circle cx="12" cy="12" r="9"/><path d="M12 8v8M8 12h8"/>' },
  add_photo_alternate: { body: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M8 11h.01M21 15l-5-5L5 21"/>' },
  account_balance_wallet: { body: '<path d="M4 7h15a2 2 0 0 1 2 2v9H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h13"/><path d="M16 13h.01"/>' },
  analytics: { body: '<path d="M4 19V5"/><path d="M4 19h16"/><path d="M8 16V9"/><path d="M12 16V6"/><path d="M16 16v-4"/>' },
  arrow_back: { body: '<path d="M19 12H5"/><path d="M12 19l-7-7 7-7"/>' },
  arrow_forward: { body: '<path d="M5 12h14"/><path d="M12 5l7 7-7 7"/>' },
  category: { body: '<rect x="4" y="4" width="6" height="6" rx="1"/><rect x="14" y="4" width="6" height="6" rx="1"/><rect x="4" y="14" width="6" height="6" rx="1"/><rect x="14" y="14" width="6" height="6" rx="1"/>' },
  checkroom: { body: '<path d="M12 6a2 2 0 1 0-2-2"/><path d="M12 6 4 10v3l4-1v8h8v-8l4 1v-3l-8-4Z"/>' },
  chevron_left: { body: '<path d="m15 18-6-6 6-6"/>' },
  chevron_right: { body: '<path d="m9 18 6-6-6-6"/>' },
  close: { body: '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>' },
  dashboard: { body: '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>' },
  delete: { body: '<path d="M3 6h18"/><path d="M8 6V4h8v2"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v5M14 11v5"/>' },
  download: { body: '<path d="M12 3v12"/><path d="m7 10 5 5 5-5"/><path d="M5 21h14"/>' },
  edit: { body: '<path d="M4 20h4l11-11a2.8 2.8 0 0 0-4-4L4 16v4Z"/><path d="m13 6 5 5"/>' },
  edit_note: { body: '<path d="M4 6h11"/><path d="M4 11h8"/><path d="M4 16h6"/><path d="M14 19h3l4-4a2 2 0 0 0-3-3l-4 4v3Z"/>' },
  expand_more: { body: '<path d="m6 9 6 6 6-6"/>' },
  filter_list: { body: '<path d="M4 6h16"/><path d="M7 12h10"/><path d="M10 18h4"/>' },
  group: { body: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>' },
  help_outline: { body: '<circle cx="12" cy="12" r="9"/><path d="M9.5 9a2.5 2.5 0 0 1 5 0c0 2-2.5 2-2.5 4"/><path d="M12 17h.01"/>' },
  history: { body: '<path d="M3 12a9 9 0 1 0 3-6.7"/><path d="M3 3v6h6"/><path d="M12 7v5l3 2"/>' },
  image: { body: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M8 11h.01"/><path d="m21 15-5-5L5 21"/>' },
  inventory_2: { body: '<path d="M21 8 12 3 3 8l9 5 9-5Z"/><path d="M3 8v8l9 5 9-5V8"/><path d="M12 13v8"/>' },
  logout: { body: '<path d="M10 17l5-5-5-5"/><path d="M15 12H3"/><path d="M21 3v18h-8"/>' },
  notifications: { body: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"/><path d="M10 21h4"/>' },
  palette: { body: '<circle cx="13.5" cy="6.5" r=".5"/><circle cx="17.5" cy="10.5" r=".5"/><circle cx="8.5" cy="7.5" r=".5"/><circle cx="6.5" cy="12.5" r=".5"/><path d="M12 3a9 9 0 0 0 0 18h1.5a2 2 0 0 0 1.8-2.9 2 2 0 0 1 1.8-2.9H19a5 5 0 0 0 5-5 9 9 0 0 0-12-7.2Z"/>' },
  payments: { body: '<rect x="3" y="6" width="18" height="12" rx="2"/><circle cx="12" cy="12" r="3"/><path d="M6 9h.01M18 15h.01"/>' },
  person: { body: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>' },
  person_search: { body: '<circle cx="10" cy="8" r="4"/><path d="M3 21a7 7 0 0 1 11-5.7"/><circle cx="17" cy="17" r="3"/><path d="m20 20-1.5-1.5"/>' },
  playlist_add: { body: '<path d="M4 6h12"/><path d="M4 11h12"/><path d="M4 16h8"/><path d="M17 14v6"/><path d="M14 17h6"/>' },
  print: { body: '<path d="M6 9V3h12v6"/><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/><path d="M6 14h12v7H6z"/>' },
  receipt_long: { body: '<path d="M6 3h12v18l-3-2-3 2-3-2-3 2V3Z"/><path d="M9 8h6M9 12h6M9 16h3"/>' },
  refresh: { body: '<path d="M20 11a8 8 0 1 0-2.3 5.7"/><path d="M20 4v7h-7"/>' },
  save: { body: '<path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z"/><path d="M17 21v-8H7v8"/><path d="M7 3v5h8"/>' },
  search: { body: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>' },
  search_off: { body: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/><path d="M4 4l16 16"/>' },
  settings: { body: '<path d="M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2 3.4-.2-.1a1.7 1.7 0 0 0-1.9.3l-.2.1-3.5-2-3.5 2-.2-.1a1.7 1.7 0 0 0-1.9-.3l-.2.1-2-3.4.1-.1A1.7 1.7 0 0 0 4.6 15L4.5 15v-4l.1-.1a1.7 1.7 0 0 0-.3-1.9L4.2 9l2-3.4.2.1a1.7 1.7 0 0 0 1.9-.3l.2-.1 3.5 2 3.5-2 .2.1a1.7 1.7 0 0 0 1.9.3l.2-.1 2 3.4-.1.1a1.7 1.7 0 0 0-.3 1.9l.1.1v4l-.1.1Z"/>' },
  shopping_bag: { body: '<path d="M6 8h12l1 13H5L6 8Z"/><path d="M9 8a3 3 0 0 1 6 0"/>' },
  straight: { body: '<path d="M12 19V5"/><path d="m5 12 7-7 7 7"/>' },
  straighten: { body: '<rect x="3" y="7" width="18" height="10" rx="2"/><path d="M7 7v4M11 7v3M15 7v4M19 7v3"/>' },
  swords: { body: '<path d="m14.5 17.5 3 3 3-3-3-3"/><path d="M13 19 19 3l2 2-16 6"/><path d="m9.5 17.5-3 3-3-3 3-3"/><path d="M11 19 5 3 3 5l16 6"/>' },
  tune: { body: '<path d="M4 7h10"/><path d="M18 7h2"/><circle cx="16" cy="7" r="2"/><path d="M4 17h2"/><path d="M10 17h10"/><circle cx="8" cy="17" r="2"/>' },
  upload: { body: '<path d="M12 21V9"/><path d="m17 14-5-5-5 5"/><path d="M5 3h14"/>' },
}

const defaultIcon: IconDef = {
  body: '<circle cx="12" cy="12" r="8"/><path d="M12 8v8M8 12h8"/>',
}

function createSvg(iconName: string): SVGElement {
  const icon = iconMap[iconName] || defaultIcon
  const template = document.createElement('template')
  template.innerHTML = `<svg class="material-icon-svg" xmlns="http://www.w3.org/2000/svg" viewBox="${icon.viewBox || '0 0 24 24'}" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">${icon.body}</svg>`
  return template.content.firstElementChild as SVGElement
}

function syncIconElement(element: Element) {
  const rawName = element.textContent?.trim()
  const previousName = (element as HTMLElement).dataset.iconName
  const iconName = rawName || previousName

  if (!iconName) return
  if (rawName === '' && previousName) return

  element.textContent = ''
  ;(element as HTMLElement).dataset.iconName = iconName
  element.appendChild(createSvg(iconName))
}

function syncMaterialIcons(root: ParentNode = document) {
  root.querySelectorAll?.('.material-symbols-outlined').forEach(syncIconElement)
}

export function installMaterialIconFallback() {
  syncMaterialIcons()

  const observer = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      if (mutation.type === 'characterData') {
        const parent = mutation.target.parentElement
        if (parent?.classList.contains('material-symbols-outlined')) {
          syncIconElement(parent)
        }
        continue
      }

      mutation.addedNodes.forEach((node) => {
        if (node.nodeType !== Node.ELEMENT_NODE) return
        const element = node as Element
        if (element.classList.contains('material-symbols-outlined')) {
          syncIconElement(element)
        }
        syncMaterialIcons(element)
      })
    }
  })

  observer.observe(document.body, {
    childList: true,
    characterData: true,
    subtree: true,
  })
}
