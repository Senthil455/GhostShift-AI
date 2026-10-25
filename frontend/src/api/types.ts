export type Category =
  | 'DOCUMENTS'
  | 'IMAGES'
  | 'VIDEOS'
  | 'AUDIO'
  | 'SOURCE_CODE'
  | 'DEVELOPMENT_LOGS'
  | 'ARCHIVES'
  | 'STUDY_MATERIALS'
  | 'FINANCIAL_DOCUMENTS'
  | 'PRESENTATIONS'
  | 'CONFIGURATION'
  | 'OTHER'

export interface ModelSettings {
  provider: string
  endpoint: string
  model: string
  timeoutSeconds: number
  maxInputChars: number
  lastHealthStatus?: string | null
  lastHealthAt?: string | null
  lastLatencyMs?: number | null
}

export interface ApprovalPolicy {
  fileMovesRequireApproval: boolean
  workflowMovesRequireApproval: boolean
  logAnalysisRequiresApproval: boolean
}

export interface TimeSavingsSettings {
  fileReviewMinutesPerFile: number
  logAnalysisMinutes: number
  workflowReviewMinutes: number
}

export interface AppSettings {
  model: ModelSettings
  approvals: ApprovalPolicy
  timeSavings: TimeSavingsSettings
  theme: string
  onboardingCompleted: boolean
  dataDir: string
  databasePath: string
  categoryFolders: Record<string, string>
}

export interface SettingsUpdateRequest {
  model?: Partial<Pick<ModelSettings, 'provider' | 'endpoint' | 'model' | 'timeoutSeconds' | 'maxInputChars'>>
  approvals?: ApprovalPolicy
  timeSavings?: TimeSavingsSettings
  theme?: string
  onboardingCompleted?: boolean
  categoryFolders?: Record<string, string>
}

export interface AiModelInfo {
  name: string
  sizeBytes?: number
  parameterSize?: string
  quantization?: string
  family?: string
}

export interface AiStatus {
  reachable: boolean
  modelAvailable: boolean
  endpoint: string
  model: string
  status: string
  message: string
  latencyMs?: number | null
  checkedAt?: string
  models?: AiModelInfo[]
}

export interface AiTestResult {
  ok: boolean
  endpoint: string
  model: string
  latencyMs: number
  message: string
  sample?: string
}

export interface RegisteredDirectory {
  id: number
  displayName: string
  path: string
  directoryType: string
  allowRead: boolean
  allowOrganize: boolean
  active: boolean
  createdAt: string
  lastScanAt?: string | null
}

export interface FileInfo {
  id: string
  name: string
  relativePath: string
  extension: string
  sizeBytes: number
  modifiedAt: string
}

export interface ScanResult {
  directoryId: number
  directoryName: string
  fileCount: number
  truncated: boolean
  skippedSymlinks: number
  files: FileInfo[]
}

export interface PlanAction {
  id: string
  sourceRelative: string
  destinationRelative: string
  fileName: string
  extension: string
  sizeBytes: number
  modifiedAt: string
  category: Category
  confidence: number
  rationale: string
  classificationSource: string
  status: string
  conflict: boolean
  error?: string | null
}

export interface OrganizationPlan {
  id: string
  directoryId: number
  directoryName: string
  directoryPath: string
  status: string
  fileCount: number
  conflictCount: number
  summary: string
  createdAt: string
  updatedAt: string
  executedAt?: string | null
  actions: PlanAction[]
}

export interface OrganizationPlanSummary {
  id: string
  directoryId: number
  directoryName: string
  status: string
  fileCount: number
  conflictCount: number
  summary: string
  createdAt: string
  updatedAt: string
  executedAt?: string | null
}

export interface PlanDecision {
  actionId: string
  decision: 'APPROVE' | 'REJECT'
  category?: Category | null
  remember?: boolean
}

export interface WorkflowInput {
  name: string
  label: string
  type: 'DIRECTORY' | 'TEXT' | 'NUMBER' | 'BOOLEAN'
  required: boolean
  description: string
}

export interface WorkflowStep {
  id: string
  type: string
  description: string
  parameters: Record<string, unknown>
  enabled: boolean
}

export interface WorkflowDefinition {
  schemaVersion: number
  name: string
  description: string
  steps: WorkflowStep[]
  inputs: WorkflowInput[]
  requiresApproval: boolean
  failurePolicy: 'STOP' | 'CONTINUE_SAFE'
}

export interface WorkflowValidation {
  valid: boolean
  errors: string[]
  warnings: string[]
  requiredPermissions: string[]
  riskSummary: string[]
}

export interface WorkflowVersionView {
  id: string
  version: number
  schemaVersion: number
  createdAt: string
  validation: WorkflowValidation
}

export interface WorkflowSummary {
  id: string
  name: string
  description: string
  category: string
  currentVersion: number
  status: string
  archived: boolean
  createdAt: string
  updatedAt: string
  stepCount: number
  requiredPermissions: string[]
  validation: WorkflowValidation
}

export interface WorkflowDetail extends Omit<WorkflowSummary, 'stepCount'> {
  definition: WorkflowDefinition
  versions: WorkflowVersionView[]
}

export interface WorkflowRequest {
  name: string
  description: string
  category: string
  definition: WorkflowDefinition
}

export interface WorkflowActionView {
  type: string
  label: string
  risk: 'LOW' | 'MODERATE' | 'HIGH'
  permission: string
  requiresApproval: boolean
  description: string
  parameters: Record<string, Record<string, unknown>>
}

export interface AiWorkflowDraft {
  definition: WorkflowDefinition
  explanation: string
  validation: WorkflowValidation
  aiUsed: boolean
}

export interface ActionExecution {
  id: string
  stepId: string
  stepIndex: number
  actionType: string
  status: string
  parameters: Record<string, unknown>
  result: Record<string, unknown>
  error?: string | null
  startedAt?: string | null
  endedAt?: string | null
}

export interface Approval {
  id: string
  runId: string
  actionExecutionId?: string | null
  workflowName: string
  kind: string
  title: string
  explanation: string
  risk: string
  status: string
  resolutionNote?: string | null
  createdAt: string
  resolvedAt?: string | null
}

export interface WorkflowRun {
  id: string
  workflowId: string
  workflowName: string
  workflowVersion: number
  status: string
  dryRun: boolean
  triggerType: string
  inputs: Record<string, unknown>
  summary?: string | null
  cancelRequested: boolean
  currentStep: number
  startedAt?: string | null
  endedAt?: string | null
  durationMs?: number | null
  actions: ActionExecution[]
  approvals: Approval[]
}

export interface RunEvent {
  id: number
  runId: string
  at: string
  level: string
  message: string
  data?: Record<string, unknown>
}

export interface RunSummary {
  id: string
  workflowId: string
  workflowName: string
  status: string
  dryRun: boolean
  startedAt?: string | null
  endedAt?: string | null
  durationMs?: number | null
  totalSteps: number
  succeededSteps: number
  failedSteps: number
  waitingSteps: number
  summary?: string | null
}

export interface ActivityItem {
  id: number
  kind: string
  title: string
  detail: string
  entityId?: string | null
  at: string
}

export interface Insight {
  title: string
  detail: string
  severity: string
}

export interface DashboardSummary {
  totalWorkflows: number
  totalRuns: number
  completedRuns: number
  runsNeedingIntervention: number
  filesScanned: number
  filesOrganized: number
  reportsGenerated: number
  pendingApprovals: number
  recentFailures: number
  estimatedMinutesSaved: number
  aiStatus: string
  aiModel: string
  recentRuns: RunSummary[]
  recentActivity: ActivityItem[]
  insights: Insight[]
}

export interface SavingsLine {
  label: string
  manualMinutes: number
  actualMinutes: number
  savedMinutes: number
  basis: string
}

export interface TimeSavingsView {
  estimatedMinutesSaved: number
  manualMinutes: number
  actualMinutes: number
  method: string
  disclaimer: string
  lines: SavingsLine[]
}

export interface HistoryPage {
  items: ActivityItem[]
  page: number
  pageSize: number
  total: number
}

export interface DuplicateFile {
  relativePath: string
  sizeBytes: number
  modifiedAt: string
}

export interface DuplicateGroup {
  kind: string
  label: string
  sizeBytes: number
  files: DuplicateFile[]
}

export interface DuplicateReport {
  directoryId: number
  filesHashed: number
  skippedLargeFiles: number
  groups: DuplicateGroup[]
  notice: string
}

export interface ErrorGroup {
  signature: string
  sample: string
  count: number
  firstSeen?: string | null
  lastSeen?: string | null
  lineNumbers: number[]
}

export interface LogAnalysis {
  fileName: string
  totalLines: number
  errorLines: number
  warningLines: number
  bytesRead: number
  truncated: boolean
  groups: ErrorGroup[]
  timelineNote?: string | null
  observations: string[]
}

export interface LogEvidence {
  analysis: LogAnalysis
  observations: string[]
  possibleCauses: string[]
  recommendedChecks: string[]
  aiNotice: string
  aiUsed: boolean
}

export interface ReportSummary {
  id: string
  kind: string
  title: string
  sourceRelative?: string | null
  aiUsed: boolean
  model?: string | null
  createdAt: string
}

export interface ReportView {
  id: string
  kind: string
  title: string
  sourceRelative?: string | null
  contentMarkdown: string
  evidence?: LogEvidence | null
  aiUsed: boolean
  model?: string | null
  createdAt: string
}

export interface RecordedStepView {
  id: string
  order: number
  actionType: string
  description: string
  parameters: Record<string, unknown>
  requiresApproval: boolean
}

export interface RecordingView {
  id: string
  name: string
  mode: string
  status: string
  directoryId?: number | null
  createdAt: string
  updatedAt: string
  stoppedAt?: string | null
  workflowId?: string | null
  steps: RecordedStepView[]
}

export interface HealthStatus {
  status: string
  version: string
  database: string
  aiStatus: string
  aiModel: string
  uptimeSeconds: number
  timestamp: string
}

export interface SystemInfo {
  name: string
  version: string
  dataDir: string
  databasePath: string
  bind: string
  apiTokenEnabled: boolean
  maxScanFiles: number
  maxScanDepth: number
  maxLogBytes: number
}

export interface DemoSeedResult {
  directoryId: number
  directoryName: string
  filesCreated: number
  workflowsCreated: number
  message: string
}

export interface DataExport {
  generatedAt: string
  appVersion: string
  data: Record<string, unknown>
}
