export const CATEGORIES = [
  { value: 'DOCUMENTS', label: 'Documents' },
  { value: 'IMAGES', label: 'Images' },
  { value: 'VIDEOS', label: 'Videos' },
  { value: 'AUDIO', label: 'Audio' },
  { value: 'SOURCE_CODE', label: 'Source code' },
  { value: 'DEVELOPMENT_LOGS', label: 'Development logs' },
  { value: 'ARCHIVES', label: 'Archives' },
  { value: 'STUDY_MATERIALS', label: 'Study materials' },
  { value: 'FINANCIAL_DOCUMENTS', label: 'Financial documents' },
  { value: 'PRESENTATIONS', label: 'Presentations' },
  { value: 'CONFIGURATION', label: 'Configuration' },
  { value: 'OTHER', label: 'Other' },
] as const

const DATE_FORMAT = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
})

const SHORT_DATE = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' })

export function humanDate(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return DATE_FORMAT.format(date)
}

export function shortDate(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return SHORT_DATE.format(date)
}

export function relativeTime(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const delta = Date.now() - date.getTime()
  const future = delta < 0
  const seconds = Math.abs(Math.round(delta / 1000))
  if (seconds < 45) return future ? 'in a moment' : 'just now'
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return future ? `in ${minutes}m` : `${minutes}m ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return future ? `in ${hours}h` : `${hours}h ago`
  const days = Math.round(hours / 24)
  if (days < 30) return future ? `in ${days}d` : `${days}d ago`
  return humanDate(value)
}

export function bytes(value?: number | null): string {
  if (value === null || value === undefined) return '—'
  if (value < 1024) return `${value} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let size = value / 1024
  let unit = 0
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024
    unit++
  }
  return `${size < 10 ? size.toFixed(1) : Math.round(size)} ${units[unit]}`
}

export function duration(value?: number | null): string {
  if (value === null || value === undefined) return '—'
  if (value < 1000) return `${value} ms`
  const seconds = Math.round(value / 1000)
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  return `${minutes}m ${seconds % 60}s`
}

export function humanStatus(value?: string | null): string {
  if (!value) return 'Unknown'
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

export function titleCase(value: string): string {
  const normalized = value === value.toUpperCase() ? value.toLowerCase() : value
  return normalized.replace(/[_-]+/g, ' ').replace(/\b\w/g, (character) => character.toUpperCase())
}

export function categoryLabel(value?: string | null): string {
  return CATEGORIES.find((category) => category.value === value)?.label ?? titleCase(value ?? 'Other')
}

/** Mirrors the backend's default folder names for each category. */
export function categoryDefaultFolder(value: string): string {
  const label = CATEGORIES.find((category) => category.value === value)?.label ?? 'Other'
  return label.toLowerCase().replace(/\s+/g, '-')
}

export function minutes(value: number): string {
  if (value < 1) return '<1 min'
  if (value < 60) return `${Math.round(value)} min`
  const hours = Math.floor(value / 60)
  const rest = Math.round(value % 60)
  return `${hours}h${rest ? ` ${rest}m` : ''}`
}

export function percent(value: number, total: number): number {
  if (!total) return 0
  return Math.min(100, Math.max(0, Math.round((value / total) * 100)))
}

export function isActiveRun(status?: string | null): boolean {
  return status === 'QUEUED' || status === 'RUNNING' || status === 'WAITING_FOR_APPROVAL'
}

export function riskTone(risk?: string | null): string {
  switch ((risk ?? '').toUpperCase()) {
    case 'HIGH':
      return 'danger'
    case 'MODERATE':
      return 'warning'
    case 'LOW':
      return 'success'
    default:
      return 'neutral'
  }
}
