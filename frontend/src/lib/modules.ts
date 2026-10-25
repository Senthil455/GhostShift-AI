export type ModuleTone = 'cobalt' | 'signal' | 'bone'

export interface WorkspaceModule {
  key: string
  label: string
  to: string
  icon: string
  group: 'Studio' | 'Intelligence' | 'System'
  order: string
  tone: ModuleTone
  short: string
  scene?: boolean
}

export const workspaceModules: WorkspaceModule[] = [
  {
    key: 'overview',
    label: 'Overview',
    to: '/overview',
    icon: 'grid',
    group: 'Studio',
    order: '00',
    tone: 'bone',
    short: 'The shift at a glance.',
  },
  {
    key: 'sort',
    label: 'GhostSort',
    to: '/sort',
    icon: 'sort',
    group: 'Studio',
    order: '01',
    tone: 'cobalt',
    short: 'Classify files into a plan you can inspect.',
    scene: true,
  },
  {
    key: 'dev',
    label: 'GhostDev',
    to: '/dev',
    icon: 'bug',
    group: 'Studio',
    order: '02',
    tone: 'cobalt',
    short: 'Read the evidence inside a log.',
    scene: true,
  },
  {
    key: 'capture',
    label: 'GhostCapture',
    to: '/capture',
    icon: 'record',
    group: 'Studio',
    order: '03',
    tone: 'cobalt',
    short: 'Record an allowlisted file sequence.',
    scene: true,
  },
  {
    key: 'replay',
    label: 'GhostReplay',
    to: '/replay',
    icon: 'play',
    group: 'Studio',
    order: '04',
    tone: 'cobalt',
    short: 'Dry-run or approve a reviewed workflow.',
    scene: true,
  },
  {
    key: 'brain',
    label: 'GhostBrain',
    to: '/brain',
    icon: 'brain',
    group: 'Intelligence',
    order: '05',
    tone: 'signal',
    short: 'A local model with a narrow job.',
    scene: true,
  },
  {
    key: 'pulse',
    label: 'GhostPulse',
    to: '/pulse',
    icon: 'pulse',
    group: 'Intelligence',
    order: '06',
    tone: 'signal',
    short: 'Measure duplicates without guessing.',
    scene: true,
  },
  {
    key: 'approvals',
    label: 'Approvals',
    to: '/approvals',
    icon: 'shield',
    group: 'Intelligence',
    order: '07',
    tone: 'signal',
    short: 'The human gate before sensitive action.',
  },
  {
    key: 'history',
    label: 'History',
    to: '/history',
    icon: 'clock',
    group: 'Intelligence',
    order: '08',
    tone: 'signal',
    short: 'Every run, decision, and file receipt.',
  },
  {
    key: 'settings',
    label: 'Settings',
    to: '/settings',
    icon: 'settings',
    group: 'System',
    order: '09',
    tone: 'bone',
    short: 'Folders, model endpoint, appearance.',
  },
]

export const navSections = [
  { label: 'Studio', items: workspaceModules.filter((item) => item.group === 'Studio') },
  { label: 'Intelligence', items: workspaceModules.filter((item) => item.group === 'Intelligence') },
  { label: 'System', items: workspaceModules.filter((item) => item.group === 'System') },
]

export const ledgerModules = workspaceModules.filter((item) => item.scene)

export function moduleForPath(path: string): WorkspaceModule | undefined {
  return workspaceModules
    .filter((item) => path === item.to || path.startsWith(`${item.to}/`))
    .sort((a, b) => b.to.length - a.to.length)[0]
}
