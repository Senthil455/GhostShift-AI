<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '@/api/client'
import type { ActivityItem, Insight } from '@/api/types'
import { errorMessage } from '@/lib/errors'
import { duration, minutes, plural, relativeTime } from '@/lib/format'
import { useSessionStore } from '@/stores/session'
import { useToastStore } from '@/stores/toast'
import EmptyState from '@/components/EmptyState.vue'
import GhostLedger from '@/components/GhostLedger.vue'
import Icon from '@/components/Icon.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'

const session = useSessionStore()
const toast = useToastStore()

const loading = ref(false)
const error = ref('')
const demoBusy = ref(false)

const dashboard = computed(() => session.dashboard)
const approvals = computed(() => session.pendingApprovals.slice(0, 3))
const hour = new Date().getHours()
const greeting = hour < 5 ? 'Working late' : hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening'
const today = new Intl.DateTimeFormat(undefined, { weekday: 'long', month: 'long', day: 'numeric' }).format(new Date())

const actions = [
  {
    to: '/sort',
    icon: 'sort',
    title: 'Review a folder',
    copy: 'Classify files, inspect every destination, then approve the move.',
  },
  {
    to: '/dev',
    icon: 'bug',
    title: 'Read a log',
    copy: 'Extract deterministic evidence first, then ask GhostBrain to interpret it.',
  },
  {
    to: '/replay',
    icon: 'play',
    title: 'Replay a workflow',
    copy: 'Dry-run an example or approve a reviewed automation.',
  },
]

function insightIcon(severity: string) {
  if (severity === 'ATTENTION') return 'alert'
  if (severity === 'WARNING') return 'shield'
  return 'spark'
}

function kindIcon(kind: string) {
  switch (kind.toUpperCase()) {
    case 'ORGANIZATION':
      return 'sort'
    case 'WORKFLOW':
      return 'play'
    case 'RECORDING':
      return 'record'
    case 'DIRECTORY':
      return 'folder'
    case 'REPORT':
      return 'file'
    default:
      return 'dot'
  }
}

function insightTone(insight: Insight) {
  if (insight.severity === 'ATTENTION') return 'danger'
  if (insight.severity === 'WARNING') return 'warning'
  return 'neutral'
}

async function refresh() {
  loading.value = true
  error.value = ''
  try {
    await Promise.all([session.refreshDashboard(), session.refreshApprovals()])
  } catch (cause) {
    error.value = errorMessage(cause, 'The dashboard could not be refreshed.')
  } finally {
    loading.value = false
  }
}

async function seedDemo() {
  demoBusy.value = true
  error.value = ''
  try {
    const result = await api.seedDemo()
    await Promise.all([
      session.refreshDirectories(),
      session.refreshDashboard(),
      session.refreshApprovals(),
    ])
    toast.success('Demo workspace ready', result.message)
  } catch (cause) {
    error.value = errorMessage(cause, 'Demo data could not be created.')
  } finally {
    demoBusy.value = false
  }
}

function activityLine(item: ActivityItem): string {
  return item.detail || item.kind
}

onMounted(() => {
  if (!session.dashboard) void refresh()
})
</script>

<template>
  <PageHeader
    :eyebrow="today"
    :title="`${greeting}, operator.`"
    description="Your local-first shift desk for files, logs, and repeatable workflows. Suggestions stay proposals until you authorize them."
  >
    <template #actions>
      <button class="btn btn--ghost btn--sm" type="button" :disabled="loading" @click="refresh">
        <span v-if="loading" class="spinner" />
        <Icon v-else name="refresh" :size="15" />
        Refresh
      </button>
      <RouterLink to="/replay" class="btn btn--primary btn--sm">
        <Icon name="play" :size="14" />
        Run a workflow
      </RouterLink>
    </template>
  </PageHeader>

  <p v-if="error" class="dashboard-error" role="alert">{{ error }}</p>

  <template v-if="dashboard">
    <section class="shift-stage" aria-label="Live workspace archive">
      <div class="shift-stage__copy">
        <p class="shift-stage__label"><span /> Live archive</p>
        <h2>Six modules.<br />One shift.</h2>
        <p class="shift-stage__lede">
          The workspace is a stack of records, not a feed. Move through it by module, then inspect the exact plan,
          approval, or receipt.
        </p>
        <div class="shift-stage__facts">
          <div>
            <span class="numeric">{{ dashboard.totalWorkflows }}</span>
            <p>reviewable workflows</p>
          </div>
          <div>
            <span class="numeric">{{ dashboard.pendingApprovals }}</span>
            <p>waiting for you</p>
          </div>
          <div>
            <span>{{ session.aiOnline ? 'Ready' : 'Local rules' }}</span>
            <p>GhostBrain state</p>
          </div>
        </div>
      </div>
      <GhostLedger mode="compact" interactive />
    </section>

    <section class="stat-grid dashboard-stats" aria-label="Workspace summary">
      <div class="stat">
        <p class="stat__label">Workflows</p>
        <p class="stat__value numeric">{{ dashboard.totalWorkflows }}</p>
        <p class="stat__sub">{{ dashboard.recentRuns.length ? 'Versioned and reviewable' : 'Examples are ready to run' }}</p>
      </div>
      <div class="stat">
        <p class="stat__label">Completed runs</p>
        <p class="stat__value numeric">{{ dashboard.completedRuns }}</p>
        <p class="stat__sub">{{ dashboard.totalRuns }} total · {{ dashboard.runsNeedingIntervention }} need attention</p>
      </div>
      <div class="stat">
        <p class="stat__label">Files organized</p>
        <p class="stat__value numeric">{{ dashboard.filesOrganized }}</p>
        <p class="stat__sub">{{ dashboard.filesScanned }} {{ plural(dashboard.filesScanned, 'file') }} reviewed in plans</p>
      </div>
      <div class="stat">
        <p class="stat__label">Estimated time saved</p>
        <p class="stat__value numeric">{{ minutes(dashboard.estimatedMinutesSaved) }}</p>
        <p class="stat__sub">Assumption-based, never a verified study</p>
      </div>
    </section>

    <div class="split-aside dashboard-main">
      <section class="panel">
        <header class="panel__head">
          <h2>Recent runs</h2>
          <RouterLink to="/history" class="panel__link">All history <Icon name="arrow" :size="13" /></RouterLink>
        </header>
        <div v-if="dashboard.recentRuns.length" class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th>Workflow</th>
                <th>Status</th>
                <th>Duration</th>
                <th>When</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="run in dashboard.recentRuns" :key="run.id">
                <td>
                  <RouterLink :to="{ name: 'run-detail', params: { runId: run.id } }" class="cell-main">
                    {{ run.workflowName }}
                  </RouterLink>
                  <span class="cell-sub">
                    {{ run.dryRun ? 'Dry run' : 'Live run' }} · {{ run.succeededSteps }}/{{ run.totalSteps }} steps
                  </span>
                </td>
                <td><StatusPill :status="run.status" dot /></td>
                <td class="muted small nowrap">{{ duration(run.durationMs) }}</td>
                <td class="muted small nowrap">{{ relativeTime(run.startedAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="panel__body">
          <EmptyState
            icon="play"
            title="No runs recorded yet"
            description="GhostShift never invents history. Dry-run one of the seeded examples to create a real record."
          >
            <RouterLink to="/replay" class="btn btn--sm">Open GhostReplay</RouterLink>
          </EmptyState>
        </div>
      </section>

      <aside class="stack">
        <section class="panel panel--accent">
          <header class="panel__head">
            <h2>Needs a human</h2>
            <span class="chip">{{ dashboard.pendingApprovals }} pending</span>
          </header>
          <div v-if="approvals.length" class="approval-list">
            <RouterLink v-for="approval in approvals" :key="approval.id" to="/approvals" class="approval-row">
              <span class="approval-row__risk" :class="`approval-row__risk--${approval.risk.toLowerCase()}`" />
              <div>
                <strong>{{ approval.title }}</strong>
                <span>{{ approval.workflowName }} · {{ relativeTime(approval.createdAt) }}</span>
              </div>
              <Icon name="chevron" :size="14" />
            </RouterLink>
            <RouterLink to="/approvals" class="btn btn--sm btn--block">Review all approvals</RouterLink>
          </div>
          <div v-else class="panel__body approval-empty">
            <Icon name="shield" :size="20" />
            <p>No action is waiting for authorization. The approval gate is quiet.</p>
          </div>
        </section>

        <section class="panel">
          <header class="panel__head">
            <h2>Signals</h2>
            <span class="panel__hint">Deterministic</span>
          </header>
          <div v-if="dashboard.insights?.length" class="insight-list">
            <div v-for="insight in dashboard.insights" :key="insight.title" class="insight">
              <span class="insight__icon" :class="`insight__icon--${insightTone(insight)}`">
                <Icon :name="insightIcon(insight.severity)" :size="15" />
              </span>
              <div>
                <strong>{{ insight.title }}</strong>
                <p>{{ insight.detail }}</p>
              </div>
            </div>
          </div>
          <div v-else class="panel__body">
            <p class="muted small">No open signals. GhostShift is idle and nothing is waiting.</p>
          </div>
        </section>
      </aside>
    </div>

    <section class="action-grid" aria-label="Start a shift">
      <RouterLink v-for="(action, index) in actions" :key="action.to" :to="action.to" class="action-card">
        <span class="action-card__no">{{ String(index + 1).padStart(2, '0') }}</span>
        <span class="action-card__icon"><Icon :name="action.icon" :size="19" /></span>
        <div>
          <h2>{{ action.title }}</h2>
          <p>{{ action.copy }}</p>
        </div>
        <Icon name="arrow" :size="16" class="action-card__arrow" />
      </RouterLink>
    </section>

    <div class="grid-2 dashboard-lower">
      <section class="panel">
        <header class="panel__head">
          <h2>Recent activity</h2>
          <RouterLink to="/history" class="panel__link">Full record <Icon name="arrow" :size="13" /></RouterLink>
        </header>
        <div v-if="dashboard.recentActivity?.length" class="activity-list">
          <div v-for="item in dashboard.recentActivity" :key="item.id" class="activity-row">
            <span class="activity-row__mark"><Icon :name="kindIcon(item.kind)" :size="14" /></span>
            <div class="activity-row__copy">
              <strong>{{ item.title }}</strong>
              <p>{{ activityLine(item) }}</p>
            </div>
            <span class="activity-row__time">{{ relativeTime(item.at) }}</span>
          </div>
        </div>
        <div v-else class="panel__body">
          <p class="muted small">Activity appears here after your first real operation.</p>
        </div>
      </section>

      <section class="panel brain-panel">
        <header class="panel__head">
          <h2>GhostBrain</h2>
          <span class="brain-state" :class="{ 'brain-state--online': session.aiOnline }">
            <span class="brain-state__dot" />
            {{ session.aiOnline ? 'Ready' : session.ai?.reachable ? 'Model missing' : 'Offline' }}
          </span>
        </header>
        <div class="panel__body">
          <p class="brain-panel__model mono">{{ dashboard.aiModel }}</p>
          <p class="brain-panel__copy">
            <template v-if="session.aiOnline">
              The local model is reachable. It may suggest classifications, draft workflows, and interpret log
              evidence. Deterministic code still validates every action.
            </template>
            <template v-else-if="session.ai?.reachable">
              Ollama is running, but the configured model is not installed. GhostShift remains usable with
              deterministic rules.
            </template>
            <template v-else>
              Ollama is not reachable. Every module still works with deterministic rules; AI-assisted steps will
              explain the fallback instead of inventing output.
            </template>
          </p>
          <div class="row">
            <RouterLink to="/brain" class="btn btn--sm">Configure model</RouterLink>
            <RouterLink to="/dev" class="btn btn--sm btn--ghost">See evidence reports</RouterLink>
          </div>
        </div>
      </section>
    </div>

    <section v-if="dashboard.totalRuns === 0" class="first-run">
      <div class="first-run__copy">
        <p class="eyebrow">A quiet first shift</p>
        <h2>Start with a real folder. Nothing else is assumed.</h2>
        <p>
          GhostShift only reads folders you register. You can create a synthetic demo folder with example files and
          three reviewable workflows, or register your own folder.
        </p>
      </div>
      <div class="row">
        <button class="btn btn--sm" type="button" :disabled="demoBusy" @click="seedDemo">
          <span v-if="demoBusy" class="spinner" />
          <Icon v-else name="spark" :size="14" />
          Create demo workspace
        </button>
        <RouterLink to="/settings" class="btn btn--sm btn--ghost">Register my folder</RouterLink>
      </div>
    </section>

    <p class="dashboard-foot">
      GhostShift suggests, deterministic code validates, you authorize, the engine executes, and every step is
      recorded. It never deletes files or runs arbitrary shell commands.
    </p>
  </template>

  <div v-else class="dashboard-loading">
    <div class="skeleton" style="width: 40%; height: 1.4rem" />
    <div class="skeleton" style="width: 100%; height: 18rem" />
    <div class="skeleton" style="width: 100%; height: 12rem" />
  </div>
</template>

<style scoped>
.dashboard-error {
  margin: 0 0 1.2rem;
  padding: 0.75rem 0.9rem;
  border: 1px solid color-mix(in srgb, var(--danger) 35%, var(--line));
  border-left: 3px solid var(--danger);
  background: color-mix(in srgb, var(--danger) 7%, var(--surface));
  color: var(--danger);
  font-size: 0.84rem;
}
.shift-stage {
  display: grid;
  grid-template-columns: minmax(17rem, 0.72fr) minmax(0, 1.28fr);
  gap: 1.1rem;
  margin-bottom: 1.35rem;
}
.shift-stage__copy {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 1.5rem 1.45rem;
  border: 1px solid var(--line);
  background: var(--surface);
  box-shadow: 4px 4px 0 var(--shadow-color);
}
.shift-stage__label {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  margin: 0;
  color: var(--text-muted);
  font-size: 0.74rem;
  font-weight: 650;
}
.shift-stage__label span {
  width: 0.42rem;
  height: 0.42rem;
  background: var(--signal);
}
.shift-stage__copy h2 {
  margin: 1rem 0 0;
  color: var(--text);
  font-family: var(--font-display);
  font-size: clamp(2.1rem, 3.3vw, 3.6rem);
  font-weight: 600;
  letter-spacing: -0.06em;
  line-height: 0.9;
}
.shift-stage__lede {
  max-width: 34ch;
  margin: 0.9rem 0 0;
  color: var(--text-muted);
  font-size: 0.84rem;
  line-height: 1.55;
}
.shift-stage__facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.6rem;
  margin-top: 1.5rem;
  padding-top: 0.85rem;
  border-top: 1px solid var(--line);
}
.shift-stage__facts span {
  display: block;
  color: var(--text);
  font-family: var(--font-display);
  font-size: 1.35rem;
  font-weight: 600;
  letter-spacing: -0.04em;
  line-height: 1;
}
.shift-stage__facts p {
  margin: 0.3rem 0 0;
  color: var(--text-faint);
  font-size: 0.68rem;
  line-height: 1.3;
}
.dashboard-stats {
  margin-bottom: 1.35rem;
}
.dashboard-main {
  margin-bottom: 1.35rem;
}
.panel__link,
.panel__hint {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  color: var(--text-faint);
  font-family: var(--font-sans);
  font-size: 0.72rem;
  font-weight: 650;
}
.panel__link:hover {
  color: var(--accent);
}
.dashboard-main .cell-main {
  display: inline-block;
  color: var(--text);
  font-weight: 650;
}
.dashboard-main .cell-main:hover {
  color: var(--accent);
}

.approval-list {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  padding: 0.55rem;
}
.approval-row {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.6rem;
  color: var(--text-muted);
  transition:
    background 140ms ease,
    color 140ms ease;
}
.approval-row:hover {
  background: var(--surface-2);
  color: var(--text);
}
.approval-row > div {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}
.approval-row strong {
  color: var(--text);
  font-size: 0.82rem;
  font-weight: 650;
}
.approval-row span {
  margin-top: 0.12rem;
  color: var(--text-faint);
  font-size: 0.7rem;
}
.approval-row .icon {
  color: var(--text-faint);
}
.approval-row__risk {
  width: 4px;
  height: 1.8rem;
  background: var(--text-faint);
  flex: none;
}
.approval-row__risk--high {
  background: var(--danger);
}
.approval-row__risk--moderate {
  background: var(--warn);
}
.approval-row__risk--low {
  background: var(--ok);
}
.approval-empty {
  display: flex;
  align-items: flex-start;
  gap: 0.65rem;
  color: var(--text-muted);
}
.approval-empty .icon {
  margin-top: 0.1rem;
  color: var(--ok);
}
.approval-empty p {
  margin: 0;
  font-size: 0.82rem;
  line-height: 1.5;
}

.insight-list {
  display: flex;
  flex-direction: column;
}
.insight {
  display: flex;
  gap: 0.65rem;
  padding: 0.8rem 1rem;
  border-bottom: 1px solid var(--line);
}
.insight:last-child {
  border-bottom: 0;
}
.insight__icon {
  display: grid;
  place-items: center;
  width: 1.9rem;
  height: 1.9rem;
  border: 1px solid var(--line);
  color: var(--text-muted);
  flex: none;
}
.insight__icon--danger {
  border-color: color-mix(in srgb, var(--danger) 40%, var(--line));
  color: var(--danger);
}
.insight__icon--warning {
  border-color: color-mix(in srgb, var(--warn) 40%, var(--line));
  color: var(--warn);
}
.insight strong {
  display: block;
  font-size: 0.82rem;
  font-weight: 650;
}
.insight p {
  margin: 0.2rem 0 0;
  color: var(--text-muted);
  font-size: 0.76rem;
  line-height: 1.45;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
  margin-bottom: 1.35rem;
}
.action-card {
  display: grid;
  grid-template-columns: auto auto minmax(0, 1fr) auto;
  align-items: start;
  gap: 0.75rem;
  padding: 1rem;
  border: 1px solid var(--line);
  background: var(--surface);
  box-shadow: 4px 4px 0 var(--shadow-color);
  color: var(--text);
  transition:
    border-color 150ms ease,
    background 150ms ease,
    transform 160ms var(--ease),
    box-shadow 160ms var(--ease);
}
.action-card:hover {
  border-color: var(--accent);
  background: var(--surface-2);
  color: var(--text);
  transform: translate(-2px, -2px);
  box-shadow: 6px 6px 0 color-mix(in srgb, var(--accent) 30%, transparent);
}
.action-card__no {
  padding-top: 0.35rem;
  color: var(--text-faint);
  font-family: var(--font-mono);
  font-size: 0.6rem;
}
.action-card__icon {
  display: grid;
  place-items: center;
  width: 2.2rem;
  height: 2.2rem;
  border: 1px solid var(--line-strong);
  color: var(--signal);
  flex: none;
}
.action-card h2 {
  margin: 0.1rem 0 0;
  font-family: var(--font-display);
  font-size: 1.06rem;
  font-weight: 600;
  letter-spacing: -0.025em;
}
.action-card p {
  margin: 0.3rem 0 0;
  color: var(--text-muted);
  font-size: 0.78rem;
  line-height: 1.45;
}
.action-card__arrow {
  margin-top: 0.15rem;
  color: var(--text-faint);
}

.dashboard-lower {
  align-items: start;
}
.activity-list {
  display: flex;
  flex-direction: column;
}
.activity-row {
  display: flex;
  align-items: flex-start;
  gap: 0.65rem;
  padding: 0.75rem 1.05rem;
  border-bottom: 1px solid var(--line);
}
.activity-row:last-child {
  border-bottom: 0;
}
.activity-row__mark {
  display: grid;
  place-items: center;
  width: 1.65rem;
  height: 1.65rem;
  border: 1px solid var(--line);
  color: var(--text-faint);
  flex: none;
  margin-top: 0.05rem;
}
.activity-row__copy {
  min-width: 0;
  flex: 1;
}
.activity-row__copy strong {
  display: block;
  font-size: 0.8rem;
  font-weight: 650;
}
.activity-row__copy p {
  margin: 0.15rem 0 0;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 0.74rem;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.activity-row__time {
  padding-top: 0.18rem;
  color: var(--text-faint);
  font-size: 0.7rem;
  white-space: nowrap;
}

.brain-panel {
  border-top: 3px solid var(--accent);
}
.brain-state {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--text-faint);
  font-size: 0.72rem;
  font-weight: 600;
}
.brain-state__dot {
  width: 0.4rem;
  height: 0.4rem;
  background: var(--text-faint);
}
.brain-state--online {
  color: var(--ok);
}
.brain-state--online .brain-state__dot {
  background: var(--ok);
}
.brain-panel__model {
  margin: 0;
  color: var(--accent);
  font-size: 0.76rem;
}
.brain-panel__copy {
  max-width: 60ch;
  margin: 0.55rem 0 0.9rem;
  color: var(--text-muted);
  font-size: 0.82rem;
  line-height: 1.6;
}

.first-run {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1.6rem;
  margin-top: 1.35rem;
  padding: 1.35rem 1.4rem;
  border: 1px solid var(--line-strong);
  border-left: 4px solid var(--signal);
  background: var(--surface);
  box-shadow: 4px 4px 0 var(--shadow-color);
}
.first-run__copy h2 {
  max-width: 24ch;
  margin: 0.4rem 0 0;
  font-family: var(--font-display);
  font-size: clamp(1.5rem, 2.4vw, 2.1rem);
  font-weight: 600;
  letter-spacing: -0.045em;
  line-height: 1;
}
.first-run__copy p {
  max-width: 66ch;
  margin: 0.55rem 0 0;
  color: var(--text-muted);
  font-size: 0.82rem;
  line-height: 1.55;
}
.first-run > .row {
  flex-wrap: nowrap;
}
.dashboard-foot {
  max-width: 90ch;
  margin: 1.6rem 0 0;
  padding-top: 1rem;
  border-top: 1px solid var(--line);
  color: var(--text-faint);
  font-size: 0.74rem;
  line-height: 1.6;
}
.dashboard-loading {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
}

@media (max-width: 1180px) {
  .shift-stage {
    grid-template-columns: 1fr;
  }
  .shift-stage__copy {
    padding: 1.3rem;
  }
  .action-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 720px) {
  .shift-stage__facts {
    grid-template-columns: 1fr 1fr;
  }
  .first-run {
    flex-direction: column;
    align-items: flex-start;
  }
  .first-run > .row {
    flex-wrap: wrap;
  }
  .activity-row__copy p {
    white-space: normal;
  }
}
</style>
