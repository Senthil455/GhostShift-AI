<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { api } from '@/api/client'
import type { ActionExecution, Approval, RunEvent, WorkflowRun } from '@/api/types'
import { errorMessage } from '@/lib/errors'
import { duration, humanDate, relativeTime, riskTone, titleCase } from '@/lib/format'
import { useSessionStore } from '@/stores/session'
import { useToastStore } from '@/stores/toast'
import ApprovalCard from '@/components/ApprovalCard.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import Icon from '@/components/Icon.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'

const route = useRoute()
const session = useSessionStore()
const toast = useToastStore()

const TERMINAL = new Set(['COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED', 'CANCELLED'])

const run = ref<WorkflowRun | null>(null)
const events = ref<RunEvent[]>([])
const loading = ref(false)
const error = ref('')
const cancelOpen = ref(false)
const cancelBusy = ref(false)
let timer: number | undefined

const runId = computed(() => String(route.params.runId ?? ''))
const actions = computed<ActionExecution[]>(() =>
  [...(run.value?.actions ?? [])].sort((a, b) => a.stepIndex - b.stepIndex),
)
const approvals = computed<Approval[]>(() => run.value?.approvals ?? [])
const pendingApprovals = computed(() => approvals.value.filter((approval) => approval.status === 'PENDING'))
const resolvedApprovals = computed(() => approvals.value.filter((approval) => approval.status !== 'PENDING'))
const active = computed(() => Boolean(run.value && !TERMINAL.has(run.value.status)))
const lastEventId = computed(() => events.value.reduce((max, event) => Math.max(max, event.id), 0))
const succeededCount = computed(() => actions.value.filter((action) => action.status === 'SUCCEEDED').length)
const failedCount = computed(() => actions.value.filter((action) => action.status === 'FAILED').length)
const description = computed(() => {
  if (!run.value) return 'Loading this local run record…'
  if (run.value.summary) return run.value.summary
  return `${run.value.dryRun ? 'Dry run' : 'Live run'} · version ${run.value.workflowVersion} · ${actions.value.length} step(s)`
})
const cancelLabel = computed(() => {
  if (!run.value) return 'Cancel run'
  if (run.value.status === 'WAITING_FOR_APPROVAL') return 'Cancel waiting run'
  return 'Request cancellation'
})

function normalize(value: WorkflowRun): WorkflowRun {
  return {
    ...value,
    actions: (value.actions ?? []).map((action) => ({
      ...action,
      parameters: action.parameters ?? {},
      result: action.result ?? {},
    })),
    approvals: value.approvals ?? [],
    inputs: value.inputs ?? {},
  }
}

function appendEvents(fresh: RunEvent[]) {
  if (!fresh.length) return
  const known = new Set(events.value.map((event) => event.id))
  const additions = fresh.filter((event) => !known.has(event.id))
  if (!additions.length) return
  events.value = [...events.value, ...additions].sort((a, b) => a.id - b.id)
}

function stopPolling() {
  if (timer) {
    window.clearInterval(timer)
    timer = undefined
  }
}

function startPolling() {
  stopPolling()
  if (!active.value) return
  timer = window.setInterval(() => {
    if (document.visibilityState === 'hidden') return
    void poll()
  }, 1400)
}

async function poll() {
  if (!runId.value) return
  try {
    const [freshRun, freshEvents] = await Promise.all([
      api.run(runId.value),
      api.runEvents(runId.value, lastEventId.value),
    ])
    run.value = normalize(freshRun)
    appendEvents(freshEvents ?? [])
    if (!active.value) {
      stopPolling()
      await session.refreshDashboard().catch(() => undefined)
    }
  } catch {
    // A transient poll failure should not erase the run already on screen.
  }
}

async function load() {
  if (!runId.value) return
  loading.value = true
  error.value = ''
  stopPolling()
  try {
    const [freshRun, freshEvents] = await Promise.all([
      api.run(runId.value),
      api.runEvents(runId.value, 0),
    ])
    run.value = normalize(freshRun)
    events.value = (freshEvents ?? []).sort((a, b) => a.id - b.id)
    startPolling()
  } catch (cause) {
    error.value = errorMessage(cause, 'That run record could not be opened.')
    run.value = null
    events.value = []
  } finally {
    loading.value = false
  }
}

async function refresh() {
  await load()
  await Promise.all([
    session.refreshApprovals().catch(() => undefined),
    session.refreshDashboard().catch(() => undefined),
  ])
}

async function approvalDecided() {
  toast.info('Decision recorded', 'The engine will re-check the run before continuing.')
  await poll()
  startPolling()
  await session.refreshDashboard().catch(() => undefined)
}

async function cancelRun() {
  if (!run.value) return
  cancelBusy.value = true
  error.value = ''
  try {
    run.value = normalize(await api.cancelRun(run.value.id))
    cancelOpen.value = false
    toast.info('Cancellation requested', 'The current safe action may finish before the run stops.')
    await poll()
    startPolling()
    await Promise.all([
      session.refreshDashboard().catch(() => undefined),
      session.refreshApprovals().catch(() => undefined),
    ])
  } catch (cause) {
    error.value = errorMessage(cause, 'The run could not be cancelled.')
  } finally {
    cancelBusy.value = false
  }
}

async function copyRunId() {
  if (!run.value) return
  try {
    await navigator.clipboard.writeText(run.value.id)
    toast.info('Run id copied', run.value.id)
  } catch {
    toast.warning('Copy failed', 'Select the run id and copy it manually.')
  }
}

function json(value: unknown): string {
  try {
    return JSON.stringify(value ?? {}, null, 2)
  } catch {
    return '{}'
  }
}

function hasData(value: unknown): boolean {
  return Boolean(value && typeof value === 'object' && Object.keys(value as Record<string, unknown>).length > 0)
}

function eventTone(level: string): string {
  switch (level.toUpperCase()) {
    case 'ERROR':
      return 'danger'
    case 'WARN':
      return 'warning'
    default:
      return 'neutral'
  }
}

function actionResultNote(action: ActionExecution): string {
  if (action.error) return action.error
  const result = action.result ?? {}
  for (const key of ['summary', 'message', 'notice', 'reason']) {
    const value = result[key]
    if (typeof value === 'string' && value.trim()) return value
  }
  return ''
}

watch(runId, () => void load(), { immediate: true })
onBeforeUnmount(stopPolling)
</script>

<template>
  <PageHeader
    eyebrow="Run record"
    :title="run?.workflowName ?? 'Run record'"
    :description="description"
  >
    <template #actions>
      <RouterLink to="/history" class="btn btn--ghost btn--sm">
        <Icon name="clock" :size="14" />
        All history
      </RouterLink>
      <RouterLink v-if="run" :to="{ name: 'ghost-replay', query: { workflow: run.workflowId } }" class="btn btn--sm">
        <Icon name="play" :size="14" />
        Open workflow
      </RouterLink>
      <button
        v-if="run && active"
        class="btn btn--danger btn--sm"
        type="button"
        :disabled="run.cancelRequested"
        @click="cancelOpen = true"
      >
        <Icon name="x" :size="14" />
        {{ run.cancelRequested ? 'Cancellation requested' : cancelLabel }}
      </button>
    </template>
  </PageHeader>

  <p v-if="error" class="run-error" role="alert">{{ error }}</p>

  <div v-if="run" class="run-meta panel">
    <div class="run-meta__status">
      <StatusPill :status="run.status" dot />
      <span v-if="active" class="run-live"><span class="run-live__dot" /> {{ run.status === 'WAITING_FOR_APPROVAL' ? 'Paused for a human' : 'Updating live' }}</span>
      <span v-else class="faint tiny">Finished {{ relativeTime(run.endedAt) }}</span>
    </div>
    <div class="run-meta__facts">
      <div>
        <span>Mode</span>
        <strong>{{ run.dryRun ? 'Dry run' : 'Live' }}</strong>
      </div>
      <div>
        <span>Version</span>
        <strong>v{{ run.workflowVersion }}</strong>
      </div>
      <div>
        <span>Trigger</span>
        <strong>{{ titleCase(run.triggerType) }}</strong>
      </div>
      <div>
        <span>Steps</span>
        <strong class="numeric">{{ succeededCount }}/{{ actions.length }}<template v-if="failedCount"> · {{ failedCount }} failed</template></strong>
      </div>
      <div>
        <span>Duration</span>
        <strong>{{ duration(run.durationMs) }}</strong>
      </div>
      <div>
        <span>Started</span>
        <strong>{{ humanDate(run.startedAt) }}</strong>
      </div>
    </div>
    <div class="run-meta__id">
      <span class="mono">Run id {{ run.id }}</span>
      <button class="icon-btn" type="button" aria-label="Copy run id" @click="copyRunId">
        <Icon name="copy" :size="14" />
      </button>
      <span v-if="run.cancelRequested" class="chip chip--accent">Cancellation requested</span>
    </div>
  </div>

  <div v-if="run" class="run-layout">
    <div class="run-main">
      <section class="panel">
        <header class="panel__head">
          <h2>Steps</h2>
          <span class="panel__hint">{{ actions.length }} recorded</span>
        </header>
        <div v-if="actions.length" class="action-list">
          <article v-for="action in actions" :key="action.id" class="action">
            <div class="action__rail">
              <span class="action__index mono">{{ String(action.stepIndex).padStart(2, '0') }}</span>
              <span class="action__line" />
            </div>
            <div class="action__body">
              <header class="action__head">
                <div>
                  <h3>{{ titleCase(action.actionType) }}</h3>
                  <p class="mono">{{ action.stepId }}</p>
                </div>
                <StatusPill :status="action.status" />
              </header>
              <p v-if="actionResultNote(action)" class="action__note" :class="{ 'action__note--error': action.error }">
                {{ actionResultNote(action) }}
              </p>
              <div class="action__times">
                <span v-if="action.startedAt">Started {{ humanDate(action.startedAt) }}</span>
                <span v-if="action.endedAt">Ended {{ humanDate(action.endedAt) }}</span>
              </div>
              <details v-if="hasData(action.parameters)" class="action__details">
                <summary>Parameters</summary>
                <pre class="code-block">{{ json(action.parameters) }}</pre>
              </details>
              <details v-if="hasData(action.result)" class="action__details">
                <summary>Result</summary>
                <pre class="code-block">{{ json(action.result) }}</pre>
              </details>
            </div>
          </article>
        </div>
        <div v-else class="panel__body">
          <p class="muted small">No action rows were recorded for this run.</p>
        </div>
      </section>

      <section class="panel events-panel">
        <header class="panel__head">
          <h2>Run events</h2>
          <span class="panel__hint">{{ events.length }} entries{{ active ? ' · live' : '' }}</span>
        </header>
        <div v-if="events.length" class="event-list">
          <div v-for="event in events" :key="event.id" class="event">
            <span class="event__level" :class="`event__level--${eventTone(event.level)}`">{{ event.level }}</span>
            <div class="event__copy">
              <p>{{ event.message }}</p>
              <details v-if="hasData(event.data)" class="event__data">
                <summary>Event data</summary>
                <pre class="code-block">{{ json(event.data) }}</pre>
              </details>
            </div>
            <time class="event__time" :title="humanDate(event.at)">{{ relativeTime(event.at) }}</time>
          </div>
        </div>
        <div v-else class="panel__body">
          <p class="muted small">No events recorded yet. A queued run writes its first event immediately.</p>
        </div>
      </section>
    </div>

    <aside class="run-aside">
      <section class="panel" :class="{ 'panel--accent': pendingApprovals.length }">
        <header class="panel__head">
          <h2>Approvals</h2>
          <span class="panel__hint">{{ pendingApprovals.length }} pending</span>
        </header>
        <div v-if="pendingApprovals.length" class="approval-stack">
          <ApprovalCard v-for="approval in pendingApprovals" :key="approval.id" :approval="approval" @decided="approvalDecided" />
        </div>
        <div v-else class="panel__body approval-quiet">
          <Icon name="shield" :size="18" />
          <p>This run is not waiting for authorization.</p>
        </div>

        <div v-if="resolvedApprovals.length" class="resolved-approvals">
          <p class="resolved-approvals__label eyebrow">Decisions on this run</p>
          <article v-for="approval in resolvedApprovals" :key="approval.id" class="resolved-approval">
            <header>
              <strong>{{ approval.title }}</strong>
              <StatusPill :status="approval.status" />
            </header>
            <p>{{ approval.explanation }}</p>
            <p v-if="approval.resolutionNote" class="resolved-approval__note">
              <span>Note</span>{{ approval.resolutionNote }}
            </p>
            <footer>
              <span class="mono">{{ approval.kind.replaceAll('_', ' ') }} · {{ riskTone(approval.risk) }} risk</span>
              <span class="mono">{{ relativeTime(approval.resolvedAt) }}</span>
            </footer>
          </article>
        </div>
      </section>

      <section class="panel">
        <header class="panel__head"><h2>Run inputs</h2></header>
        <div class="panel__body run-inputs">
          <pre v-if="hasData(run.inputs)" class="code-block">{{ json(run.inputs) }}</pre>
          <p v-else class="muted small">This run declared no inputs.</p>
          <p class="run-inputs__note">
            Inputs are validated against the workflow definition before the run starts. Folder ids are checked for
            read permission on every run.
          </p>
        </div>
      </section>

      <section class="panel panel--quiet">
        <header class="panel__head"><h2>Safety record</h2></header>
        <div class="panel__body run-safety">
          <p><Icon name="shield" :size="14" /> Approval gates are enforced in the engine, not only in this view.</p>
          <p><Icon name="lock" :size="14" /> Paths are resolved inside registered folders on every action.</p>
          <p><Icon name="x" :size="14" /> Deletes and silent overwrites are never available to a workflow.</p>
          <p><Icon name="clock" :size="14" /> Completed steps are not repeated when a run resumes after approval.</p>
        </div>
      </section>
    </aside>
  </div>

  <div v-else-if="loading" class="run-loading">
    <div class="skeleton" style="height: 5rem" />
    <div class="skeleton" style="height: 14rem" />
  </div>

  <EmptyState
    v-else-if="!error"
    icon="play"
    title="Run not found"
    description="This run id does not exist in the local database. It may have been removed by a data reset."
  >
    <RouterLink to="/history" class="btn btn--sm">Back to history</RouterLink>
  </EmptyState>

  <ConfirmDialog
    :open="cancelOpen"
    title="Cancel this workflow run?"
    :description="run?.status === 'WAITING_FOR_APPROVAL' ? 'The pending approval will expire and nothing further will run.' : 'The current safe action may finish first. Completed actions stay recorded and are not reversed automatically.'"
    confirm-label="Cancel run"
    cancel-label="Keep running"
    tone="danger"
    :busy="cancelBusy"
    @close="cancelOpen = false"
    @confirm="cancelRun"
  >
    <p class="muted small">
      Cancellation is a request. GhostShift checks it before each action and stops at the next safe boundary.
    </p>
  </ConfirmDialog>
</template>

<style scoped>
.run-error {
  margin: 0 0 1rem;
  padding: 0.65rem 0.85rem;
  border: 1px solid color-mix(in srgb, var(--danger) 30%, var(--line));
  border-left: 2px solid var(--danger);
  border-radius: var(--radius-sm);
  color: var(--danger);
  font-size: 0.78rem;
}
.run-meta {
  padding: 1rem 1.15rem;
  margin-bottom: 1.3rem;
}
.run-meta__status {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  flex-wrap: wrap;
}
.run-live {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-family: var(--font-mono);
  font-size: 0.58rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--info);
}
.run-live__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--info);
  animation: run-pulse 1.6s ease-in-out infinite;
}
@keyframes run-pulse {
  0%,
  100% {
    opacity: 0.35;
  }
  50% {
    opacity: 1;
  }
}
.run-meta__facts {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 0.8rem;
  margin-top: 0.9rem;
  padding-top: 0.85rem;
  border-top: 1px solid var(--line);
}
.run-meta__facts span {
  display: block;
  font-family: var(--font-mono);
  font-size: 0.54rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-faint);
}
.run-meta__facts strong {
  display: block;
  margin-top: 0.22rem;
  font-size: 0.76rem;
  font-weight: 550;
  overflow-wrap: anywhere;
}
.run-meta__id {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin-top: 0.85rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--line);
  color: var(--text-faint);
  font-size: 0.6rem;
}
.run-meta__id .mono {
  overflow-wrap: anywhere;
  min-width: 0;
}
.run-meta__id .chip {
  margin-left: auto;
}
.run-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(290px, 0.8fr);
  gap: 1.3rem;
  align-items: start;
}
.run-main,
.run-aside {
  display: flex;
  flex-direction: column;
  gap: 1.15rem;
  min-width: 0;
}
.panel__hint {
  font-family: var(--font-mono);
  font-size: 0.6rem;
  color: var(--text-faint);
}
.action-list {
  padding: 0.8rem 1rem 0.4rem;
}
.action {
  display: flex;
  gap: 0.8rem;
}
.action__rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 1.6rem;
  flex: none;
}
.action__index {
  color: var(--accent);
  font-size: 0.6rem;
  padding-top: 0.25rem;
}
.action__line {
  flex: 1;
  width: 1px;
  margin: 0.35rem 0;
  background: var(--line);
}
.action:last-child .action__line {
  display: none;
}
.action__body {
  flex: 1;
  min-width: 0;
  padding-bottom: 1rem;
}
.action__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.8rem;
}
.action__head h3 {
  margin: 0;
  font-size: 0.82rem;
  font-weight: 600;
}
.action__head p {
  margin: 0.15rem 0 0;
  color: var(--text-faint);
  font-size: 0.56rem;
}
.action__note {
  margin: 0.45rem 0 0;
  padding: 0.45rem 0.6rem;
  border-left: 2px solid var(--line-strong);
  color: var(--text-muted);
  background: color-mix(in srgb, var(--surface-2) 55%, transparent);
  font-size: 0.72rem;
  line-height: 1.5;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.action__note--error {
  border-left-color: var(--danger);
  color: var(--danger);
}
.action__times {
  display: flex;
  gap: 0.8rem;
  flex-wrap: wrap;
  margin-top: 0.4rem;
  color: var(--text-faint);
  font-family: var(--font-mono);
  font-size: 0.56rem;
}
.action__details {
  margin-top: 0.45rem;
}
.action__details summary,
.event__data summary {
  cursor: pointer;
  font-family: var(--font-mono);
  font-size: 0.6rem;
  color: var(--text-faint);
  width: fit-content;
}
.action__details summary:hover,
.event__data summary:hover {
  color: var(--text);
}
.action__details .code-block,
.event__data .code-block {
  margin-top: 0.4rem;
  max-height: 15rem;
}
.events-panel {
  margin-bottom: 0;
}
.event-list {
  display: flex;
  flex-direction: column;
}
.event {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
  padding: 0.6rem 1rem;
  border-bottom: 1px solid color-mix(in srgb, var(--line) 65%, transparent);
}
.event:last-child {
  border-bottom: 0;
}
.event__level {
  flex: none;
  padding: 0.12rem 0.32rem;
  border: 1px solid var(--line);
  border-radius: 3px;
  font-family: var(--font-mono);
  font-size: 0.52rem;
  letter-spacing: 0.04em;
  color: var(--text-faint);
  margin-top: 0.05rem;
}
.event__level--danger {
  color: var(--danger);
  border-color: color-mix(in srgb, var(--danger) 30%, var(--line));
}
.event__level--warning {
  color: var(--warn);
  border-color: color-mix(in srgb, var(--warn) 30%, var(--line));
}
.event__copy {
  flex: 1;
  min-width: 0;
}
.event__copy p {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.74rem;
  line-height: 1.45;
  overflow-wrap: anywhere;
}
.event__data {
  margin-top: 0.3rem;
}
.event__time {
  flex: none;
  color: var(--text-faint);
  font-family: var(--font-mono);
  font-size: 0.56rem;
  padding-top: 0.1rem;
  white-space: nowrap;
}
.approval-stack {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
  padding: 0.8rem;
}
.approval-stack :deep(.approval-card) {
  padding: 0.85rem;
}
.approval-quiet {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  color: var(--ok);
}
.approval-quiet p {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.75rem;
  line-height: 1.5;
}
.resolved-approvals {
  padding: 0.8rem 1rem 1rem;
  border-top: 1px solid var(--line);
}
.resolved-approvals__label {
  margin-bottom: 0.5rem;
}
.resolved-approval {
  padding: 0.55rem 0;
  border-top: 1px solid color-mix(in srgb, var(--line) 65%, transparent);
}
.resolved-approval:first-of-type {
  border-top: 0;
}
.resolved-approval header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.6rem;
}
.resolved-approval header strong {
  font-size: 0.74rem;
  font-weight: 550;
}
.resolved-approval p {
  margin: 0.25rem 0 0;
  color: var(--text-muted);
  font-size: 0.68rem;
  line-height: 1.5;
}
.resolved-approval__note {
  padding: 0.35rem 0.5rem;
  border-left: 2px solid var(--line-strong);
  background: var(--surface-2);
}
.resolved-approval__note span {
  display: block;
  font-family: var(--font-mono);
  font-size: 0.52rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--text-faint);
  margin-bottom: 0.15rem;
}
.resolved-approval footer {
  display: flex;
  justify-content: space-between;
  gap: 0.6rem;
  margin-top: 0.35rem;
  color: var(--text-faint);
  font-size: 0.54rem;
}
.run-inputs__note {
  margin: 0.65rem 0 0;
  color: var(--text-faint);
  font-size: 0.65rem;
  line-height: 1.5;
}
.run-safety {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}
.run-safety p {
  display: flex;
  align-items: flex-start;
  gap: 0.45rem;
  margin: 0;
  color: var(--text-muted);
  font-size: 0.68rem;
  line-height: 1.5;
}
.run-safety .icon {
  color: var(--text-faint);
  margin-top: 0.1rem;
}
.run-loading {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
@media (max-width: 1100px) {
  .run-layout {
    grid-template-columns: 1fr;
  }
  .run-meta__facts {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 620px) {
  .run-meta__facts {
    grid-template-columns: 1fr 1fr;
  }
  .run-meta__id {
    flex-wrap: wrap;
  }
  .run-meta__id .chip {
    margin-left: 0;
  }
  .action {
    gap: 0.5rem;
  }
  .event__time {
    display: none;
  }
}
</style>
