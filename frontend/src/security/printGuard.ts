import { ref } from 'vue'
import { ElMessage } from 'element-plus'

export const PRINT_BLOCKED_MESSAGE = '联系方式不可打印'

export const printing = ref(false)

let lastNotice = 0

function notifyPrintBlocked() {
  const now = Date.now()
  if (now - lastNotice < 700) return
  lastNotice = now
  ElMessage.warning({
    message: PRINT_BLOCKED_MESSAGE,
    duration: 2200,
    showClose: true,
    grouping: true,
    offset: 72,
  })
}

export function installPrintGuard() {
  const media = window.matchMedia('print')
  const sync = () => {
    const active = media.matches
    if (active && !printing.value) notifyPrintBlocked()
    printing.value = active
  }
  sync()
  media.addEventListener('change', sync)
  window.addEventListener('beforeprint', () => {
    printing.value = true
    notifyPrintBlocked()
  })
  window.addEventListener('afterprint', () => {
    printing.value = media.matches
  })
  document.addEventListener('keydown', (event) => {
    if (event.altKey || event.shiftKey || event.isComposing) return
    if (!(event.ctrlKey || event.metaKey)) return
    if (event.key.toLowerCase() !== 'p' && event.code !== 'KeyP') return
    notifyPrintBlocked()
  }, true)
}
