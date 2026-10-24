import { ElMessage } from 'element-plus'

export const COPY_BLOCKED_MESSAGE = '联系方式不可复制'

const SHIELD = '.shield'

let lastNotice = 0
let shieldIntentUntil = 0
let adjustingSelection = false

function elementOf(node: EventTarget | Node | null): Element | null {
  if (node instanceof Element) return node
  if (node instanceof Node) return node.parentElement
  return null
}

export function isShieldTarget(target: EventTarget | null): boolean {
  return !!elementOf(target)?.closest(SHIELD)
}

function armShieldIntent() {
  shieldIntentUntil = Date.now() + 2500
}

function shieldIntentActive() {
  return Date.now() < shieldIntentUntil
}

function selectionContainsShield(selection: Selection): boolean {
  for (let index = 0; index < selection.rangeCount; index += 1) {
    const range = selection.getRangeAt(index)
    if (isShieldTarget(range.startContainer) || isShieldTarget(range.endContainer)) return true
    if (range.collapsed) continue
    try {
      if (range.cloneContents().querySelector('.shield, .contact-value')) return true
    } catch {
      // Selection inside an input can throw. It is not a contact shield.
    }
  }
  return false
}

export function selectionHitsShield(): boolean {
  const selection = window.getSelection()
  if (!selection || selection.rangeCount === 0) return false
  return selectionContainsShield(selection)
}

function releaseShieldSelection() {
  if (adjustingSelection) return
  const selection = window.getSelection()
  if (!selection || selection.rangeCount === 0 || !selectionContainsShield(selection)) return
  armShieldIntent()
  adjustingSelection = true
  selection.removeAllRanges()
  adjustingSelection = false
}

export function copyWouldTouchShield(target: EventTarget | null): boolean {
  return isShieldTarget(target) || selectionHitsShield() || shieldIntentActive()
}

function notifyBlocked() {
  const now = Date.now()
  if (now - lastNotice < 700) return
  lastNotice = now
  ElMessage.warning({
    message: COPY_BLOCKED_MESSAGE,
    duration: 2200,
    showClose: true,
    grouping: true,
    offset: 72,
  })
}

function blockClipboard(event: ClipboardEvent) {
  if (!copyWouldTouchShield(event.target)) return
  event.preventDefault()
  notifyBlocked()
}

function blockShortcut(event: KeyboardEvent) {
  if (event.altKey || event.shiftKey || event.isComposing) return
  if (!(event.ctrlKey || event.metaKey)) return
  const key = event.key.toLowerCase()
  const code = event.code
  const copyOrCut = key === 'c' || key === 'x' || code === 'KeyC' || code === 'KeyX'
  if (!copyOrCut) return
  if (!copyWouldTouchShield(event.target)) return
  event.preventDefault()
  notifyBlocked()
}

function blockSelect(event: Event) {
  if (!isShieldTarget(event.target)) return
  armShieldIntent()
  event.preventDefault()
}

function blockPointer(event: Event) {
  if (isShieldTarget(event.target)) armShieldIntent()
  else shieldIntentUntil = 0
}

function blockDrag(event: DragEvent) {
  if (!isShieldTarget(event.target) && !selectionHitsShield()) return
  event.preventDefault()
  try {
    event.dataTransfer?.clearData()
  } catch {
    // Some browsers reject clearData before a payload exists.
  }
}

function blockMenu(event: Event) {
  if (!isShieldTarget(event.target)) return
  event.preventDefault()
}

export function installClipboardGuard() {
  document.addEventListener('pointerdown', blockPointer, true)
  document.addEventListener('selectstart', blockSelect, true)
  document.addEventListener('selectionchange', releaseShieldSelection)
  document.addEventListener('copy', blockClipboard, true)
  document.addEventListener('cut', blockClipboard, true)
  document.addEventListener('keydown', blockShortcut, true)
  document.addEventListener('dragstart', blockDrag, true)
  document.addEventListener('contextmenu', blockMenu, true)
}
