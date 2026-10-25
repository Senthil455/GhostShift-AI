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
      <RouterLink v-for="action in actions" :key="action.to" :to="action.to" class="action-card">
        <span class="action-card__icon"><Icon :name="action.icon" :size="20" /></span>
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
    <div class="skeleton" style="width: 100%; height: 8rem" />
    <div class="skeleton" style="width: 100%; height: 12rem" />
  </div>
</template>

<style scoped>
.dashboard-error {
  margin: 0 0 1.2rem;
  padding: 0.7rem 0.9rem;
  border: 1px solid color-mix(in srgb, var(--danger) 30%, var(--line));
  border-left: 2px solid var(--danger);
  border-radius: var(--radius-sm);
  color: var(--danger);
  font-size: 0.8rem;
}
.dashboard-stats {
  margin-bottom: 1.4rem;
}
.dashboard-main {
  margin-bottom: 1.4rem;
}
.panel__link,
.panel__hint {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-family: var(--font-mono);
  font-size: 0.63rem;
  letter-spacing: 0.04em;
  color: var(--text-faint);
}
.panel__link:hover {
  color: var(--accent);
}
.dashboard-main .cell-main {
  display: inline-block;
  color: var(--text);
  font-weight: 550;
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
  padding: 0.55rem 0.6rem;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
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
  font-size: 0.8rem;
  font-weight: 550;
  color: var(--text);
}
.approval-row span {
  font-family: var(--font-mono);
  font-size: 0.62rem;
  color: var(--text-faint);
  margin-top: 0.1rem;
}
.approval-row .icon {
  color: var(--text-faint);
}
.approval-row__risk {
  width: 4px;
  height: 1.7rem;
  border-radius: 2px;
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
  color: var(--ok);
  margin-top: 0.1rem;
}
.approval-empty p {
  margin: 0;
  font-size: 0.8rem;
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
  width: 1.8rem;
  height: 1.8rem;
  border-radius: 50%;
  border: 1px solid var(--line);
  color: var(--text-muted);
  flex: none;
}
.insight__icon--danger {
  color: var(--danger);
  border-color: color-mix(in srgb, var(--danger) 30%, var(--line));
}
.insight__icon--warning {
  color: var(--warn);
  border-color: color-mix(in srgb, var(--warn) 30%, var(--line));
}
.insight strong {
  display: block;
  font-size: 0.79rem;
  font-weight: 560;
}
.insight p {
  margin: 0.2rem 0 0;
  font-size: 0.74rem;
  line-height: 1.45;
  color: var(--text-muted);
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
  margin-bottom: 1.4rem;
}
.action-card {
  display: flex;
  align-items: flex-start;
  gap: 0.8rem;
  padding: 1rem;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: color-mix(in srgb, var(--surface) 80%, transparent);
  color: var(--text);
  transition:
    border-color 150ms ease,
    background 150ms ease,
    transform 150ms var(--ease);
}
.action-card:hover {
  border-color: var(--line-strong);
  background: var(--surface-2);
  color: var(--text);
  transform: translateY(-1px);
}
.action-card__icon {
  display: grid;
  place-items: center;
  width: 2.2rem;
  height: 2.2rem;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--accent);
  flex: none;
}
.action-card h2 {
  margin: 0.1rem 0 0;
  font-size: 0.85rem;
  font-weight: 600;
}
.action-card p {
  margin: 0.3rem 0 0;
  font-size: 0.76rem;
  line-height: 1.5;
  color: var(--text-muted);
}
.action-card__arrow {
  margin-left: auto;
  color: var(--text-faint);
  margin-top: 0.15rem;
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
  padding: 0.7rem 1.05rem;
  border-bottom: 1px solid color-mix(in srgb, var(--line) 70%, transparent);
}
.activity-row:last-child {
  border-bottom: 0;
}
.activity-row__mark {
  display: grid;
  place-items: center;
  width: 1.55rem;
  height: 1.55rem;
  border-radius: 50%;
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
  font-size: 0.77rem;
  font-weight: 550;
}
.activity-row__copy p {
  margin: 0.15rem 0 0;
  font-size: 0.72rem;
  color: var(--text-muted);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.activity-row__time {
  font-family: var(--font-mono);
  font-size: 0.6rem;
  color: var(--text-faint);
  white-space: nowrap;
  padding-top: 0.18rem;
}

.brain-panel {
  background:
    linear-gradient(145deg, color-mix(in srgb, var(--accent) 5%, transparent), transparent 50%),
    var(--surface);
}
.brain-state {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-family: var(--font-mono);
  font-size: 0.6rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--text-faint);
}
.brain-state__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
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
  font-size: 0.7rem;
  color: var(--accent);
}
.brain-panel__copy {
  margin: 0.55rem 0 0.9rem;
  color: var(--text-muted);
  font-size: 0.8rem;
  line-height: 1.6;
  max-width: 60ch;
}

.first-run {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1.6rem;
  margin-top: 1.4rem;
  padding: 1.4rem 1.5rem;
  border: 1px solid color-mix(in srgb, var(--accent) 20%, var(--line));
  border-radius: var(--radius);
  background:
    linear-gradient(120deg, color-mix(in srgb, var(--accent) 7%, transparent), transparent 60%),
    var(--surface);
}
.first-run__copy h2 {
  margin: 0.35rem 0 0;
  font-family: var(--font-display);
  font-size: clamp(1.3rem, 2.2vw, 1.65rem);
  font-weight: 470;
  letter-spacing: -0.02em;
}
.first-run__copy p {
  margin: 0.5rem 0 0;
  max-width: 66ch;
  color: var(--text-muted);
  font-size: 0.8rem;
  line-height: 1.55;
}
.first-run > .row {
  flex-wrap: nowrap;
}
.dashboard-foot {
  margin: 1.6rem 0 0;
  padding-top: 1rem;
  border-top: 1px solid var(--line);
  font-size: 0.7rem;
  line-height: 1.6;
  color: var(--text-faint);
  max-width: 90ch;
}
.dashboard-loading {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
}

@media (max-width: 1100px) {
  .action-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 720px) {
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
