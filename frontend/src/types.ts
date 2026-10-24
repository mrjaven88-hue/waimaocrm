export interface UserProfile {
  id: number
  username: string
  displayName: string
  role: 'SALESPERSON' | 'MANAGER'
  roleLabel: string
}

export interface MaskedContact {
  phoneMasked?: string
  emailMasked?: string
  whatsappMasked?: string
}

export interface UnmaskState {
  active: boolean
  sessionId?: number
  expiresAt?: string
}

export interface RevealedContact {
  phone?: string
  email?: string
  whatsapp?: string
}

export interface Lead {
  id: number
  companyName: string
  country: string
  contactName: string
  stage: string
  stageLabel: string
  productInterest?: string
  source?: string
  notes?: string
  ownerId: number
  ownerName?: string
  createdAt: string
  updatedAt: string
  contact?: MaskedContact
  unmask?: UnmaskState
  revealed?: RevealedContact
}

export interface ImportResult {
  imported: number
  duplicated: number
  failed: number
  errors?: { index: number; code: string; message: string }[]
}

export interface AuditRow {
  id: number
  actorName: string
  actorUsername: string
  action: string
  actionLabel: string
  targetType: string
  targetId?: number
  reason?: string
  createdAt: string
  expiresAt?: string
  ttlMinutes?: number
  sessionId?: number
}

export interface LeadForm {
  companyName: string
  country: string
  contactName: string
  phone: string
  email: string
  whatsapp: string
  productInterest: string
  source: string
  notes: string
}

export function blankLeadForm(): LeadForm {
  return {
    companyName: '',
    country: '',
    contactName: '',
    phone: '',
    email: '',
    whatsapp: '',
    productInterest: '',
    source: '',
    notes: '',
  }
}

const SIDE_CHANNEL = /@|\d{8,}/
const EXPIRY_STAMP = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/

function scrubMasked(contact: MaskedContact | undefined): MaskedContact | undefined {
  if (!contact) return undefined
  return {
    phoneMasked: contact.phoneMasked,
    emailMasked: contact.emailMasked,
    whatsappMasked: contact.whatsappMasked,
  }
}

export function sanitizeLead(
  lead: Lead,
  role: string | undefined,
  options?: { allowRevealed?: boolean },
): { lead: Lead; leaked: boolean } {
  const hasRevealed = lead.revealed != null
  const leaked = hasRevealed && role !== 'MANAGER'
  const keepRevealed = options?.allowRevealed === true && role === 'MANAGER' && lead.unmask?.active === true && hasRevealed
  const next: Lead = {
    ...lead,
    contact: scrubMasked(lead.contact),
    unmask: lead.unmask ? { ...lead.unmask } : undefined,
  }
  delete (next as Lead & { phone?: unknown }).phone
  delete (next as Lead & { email?: unknown }).email
  delete (next as Lead & { whatsapp?: unknown }).whatsapp
  if (keepRevealed && lead.revealed) {
    next.revealed = {
      phone: lead.revealed.phone,
      email: lead.revealed.email,
      whatsapp: lead.revealed.whatsapp,
    }
    return { lead: next, leaked: false }
  }
  delete next.revealed
  return { lead: next, leaked }
}

function redactSideChannel(value?: string): string | undefined {
  if (!value) return value
  if (SIDE_CHANNEL.test(value)) return '内容已隐藏'
  return value
}

export function presentAudit(row: AuditRow & { metadata?: Pick<AuditRow, 'expiresAt' | 'ttlMinutes' | 'sessionId'> }): AuditRow {
  const expiresAt = row.expiresAt ?? row.metadata?.expiresAt
  const ttlMinutes = row.ttlMinutes ?? row.metadata?.ttlMinutes
  const sessionId = row.sessionId ?? row.metadata?.sessionId
  return {
    id: row.id,
    actorName: row.actorName,
    actorUsername: row.actorUsername,
    action: row.action,
    actionLabel: row.actionLabel,
    targetType: row.targetType,
    targetId: row.targetId,
    reason: redactSideChannel(row.reason),
    createdAt: row.createdAt,
    expiresAt: expiresAt && EXPIRY_STAMP.test(expiresAt) ? expiresAt : undefined,
    ttlMinutes: typeof ttlMinutes === 'number' && ttlMinutes >= 1 && ttlMinutes <= 15 ? ttlMinutes : undefined,
    sessionId: typeof sessionId === 'number' && Number.isInteger(sessionId) && sessionId > 0 ? sessionId : undefined,
  }
}

export function formatTime(iso?: string): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString('zh-CN', { hour12: false })
}
