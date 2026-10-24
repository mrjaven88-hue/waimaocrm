export const WATERMARK_MARK = '禁对外传'

export function watermarkText(user: { displayName?: string | null; username?: string | null } | null | undefined): string {
  const name = user?.displayName?.trim() ?? ''
  const username = user?.username?.trim() ?? ''
  const who = [name, username].filter(Boolean).join(' ')
  if (!who) return `澄远 CRM ${WATERMARK_MARK}`
  return `${who} ${WATERMARK_MARK}`
}
