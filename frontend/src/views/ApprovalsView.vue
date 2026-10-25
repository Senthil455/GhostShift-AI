<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import type { Approval, WorkflowRun } from '@/api/types'
import { errorMessage } from '@/lib/errors'
import { humanDate, plural, relativeTime } from '@/lib/format'
import { useSessionStore } from '@/stores/session'
import { useToastStore } from '@/stores/toast'
import ApprovalCard from '@/components/ApprovalCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import Icon from '@/components/Icon.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'

interface DecisionRecord {
  approvalId: string
  title: string
  workflowName: string
  runId: string
  runStatus: string
  at: string
}

const session = useSessionStore()
const toast = useToastStore()

const refreshing = ref(false)
const pageError = ref('')
const recent = ref<DecisionRecord[]>([])
let timer: number | undefined

const pending = computed<Approval[]>(() => session.pendingApprovals)
const highRisk = computed(() => pending.value.filter((approval) => approval.risk === 'HIGH').length)
const workflowCount = computed(() => new Set(pending.value.map((approval) => approval.workflowName)).size)
const oldest = computed(() => {
  const dates = pending.value
    .map((approval) => new Date(approval.createdAt).getTime())
    .filter((value) => Number.isFinite(value))
  return dates.length ? new Date(Math.min(...dates)).toISOString() : null
})

async function refresh(silent = false) {
  if (!silent) refreshing.value = true
  pageError.value = ''
  try {
    await session.refreshApprovals()
    await session.refreshDashboard().catch(() => undefined)
  } catch (cause) {
    if (!silent) pageError.value = errorMessage(cause, 'Approvals could not be loaded.')
  } finally {
    if (!silent) refreshing.value = false
  }
}

function decided(run: WorkflowRun) {
  const approvals = run.approvals ?? []
  const approval = approvals.find((item) => item.status !== 'PENDING') ?? approvals[0]
  recent.value.unshift({
    approvalId: approval?.id ?? run.id,
    title: approval?.title ?? 'Approval decision recorded',
    workflowName: run.workflowName,
    runId: run.id,
    runStatus: run.status,
    at: new Date().toISOString(),
  })
  recent.value = recent.value.slice(0, 6)
  void refresh(true)
}

onMounted(async () => {
  await refresh()
  timer = window.setInterval(() => {
    if (document.visibilityState === 'visible') void refresh(true)
  }, 30_000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <PageHeader
    eyebrow="Approvals"
    title="Nothing crosses a gate without you."
    description="GhostShift pauses before sensitive workflow actions. You see the exact request, decide with a note, and the engine resumes or stops. No approval is inferred from silence."
  >
    <template #actions>
      <button class="btn btn--ghost btn--sm" type="button" :disabled="refreshing" @click="refresh()">
        <span v-if="refreshing" class="spinner" />
        <Icon v-else name="refresh" :size="15" />
        Refresh
      </button>
      <RouterLink to="/history" class="btn btn--primary btn--sm">
        <Icon name="clock" :size="14" />
        Review history
      </RouterLink>
    </template>
  </PageHeader>

  <p v-if="pageError" class="approvals-error" role="alert">{{ pageError }}</p>

  <section class="approvals-summary" aria-label="Approval summary">
    <div class="approvals-summary__lead">
      <span class="approvals-summary__mark"><Icon name="shield" :size="22" /></span>
      <div>
        <p class="eyebrow">The human gate</p>
        <h2>
          <template v-if="pending.length">{{ pending.length }} decision{{ pending.length === 1 ? '' : 's' }} waiting</template>
          <template v-else>The gate is quiet</template>
        </h2>
        <p>
          <template v-if="pending.length">
            Workflows are paused at a safe boundary. Nothing else will run until each request is approved or rejected.
          </template>
          <template v-else>
            No workflow is waiting for authorization. New requests appear here as soon as a live run reaches a gate.
          </template>
        </p>
      </div>
    </div>
    <div class="approvals-summary__facts">
      <div>
        <span>Pending</span>
        <strong class="numeric">{{ pending.length }}</strong>
      </div>
      <div>
        <span>High risk</span>
        <strong class="numeric" :class="{ 'approvals-risk': highRisk > 0 }">{{ highRisk }}</strong>
      </div>
      <div>
        <span>Workflows</span>
        <strong class="numeric">{{ workflowCount }}</strong>
      </div>
      <div>
        <span>Oldest wait</span>
        <strong>{{ oldest ? relativeTime(oldest) : '—' }}</strong>
      </div>
    </div>
  </section>

  <div class="approvals-layout">
    <section class="approvals-main" aria-label="Pending approvals">
      <div v-if="pending.length" class="approvals-list">
        <ApprovalCard v-for="approval in pending" :key="approval.id" :approval="approval" @decided="decided" />
      </div>
      <EmptyState
        v-else
        icon="check"
        title="No action is waiting for authorization"
        description="GhostShift never treats an unanswered request as permission. When a live workflow needs a decision, it will pause here with the exact context."
      >
        <div class="row">
          <RouterLink to="/replay" class="btn btn--sm">Open GhostReplay</RouterLink>
          <RouterLink to="/sort" class="btn btn--sm btn--ghost">Review a folder</RouterLink>
        </div>
      </EmptyState>

      <section v-if="recent.length" class="panel recent-panel">
        <header class="panel__head">
          <h2>Decided in this session</h2>
          <span class="panel__hint">{{ recent.length }} {{ plural(recent.length, 'record') }}</span>
        </header>
        <div class="recent-list">
          <RouterLink
            v-for="item in recent"
            :key="`${item.approvalId}-${item.at}`"
            :to="{ name: 'run-detail', params: { runId: item.runId } }"
            class="recent-row"
          >
            <span class="recent-row__icon"><Icon name="check" :size="14" /></span>
            <span class="recent-row__copy">
              <strong>{{ item.title }}</strong>
              <span>{{ item.workflowName }} · decision sent {{ relativeTime(item.at) }}</span>
            </span>
            <StatusPill :status="item.runStatus" />
            <Icon name="chevron" :size="13" />
          </RouterLink>
        </div>
      </section>
    </section>

    <aside class="approvals-aside">
      <section class="panel panel--accent">
        <header class="panel__head">
          <h2>How a gate works</h2>
          <span class="panel__hint">Enforced in the engine</span>
        </header>
        <ol class="gate-steps">
          <li>
            <span class="gate-steps__num">01</span>
            <div>
              <strong>Pause at a boundary</strong>
              <p>The run stops before a sensitive action. Completed steps stay recorded and are not repeated blindly.</p>
            </div>
          </li>
          <li>
            <span class="gate-steps__num">02</span>
            <div>
              <strong>Decide with context</strong>
              <p>You see the workflow, risk, and exact explanation. A note is optional but becomes part of the record.</p>
            </div>
          </li>
          <li>
            <span class="gate-steps__num">03</span>
            <div>
              <strong>Resume or stop</strong>
              <p>Approval re-checks boundaries before continuing. Rejection cancels the remaining steps and records why.</p>
            </div>
          </li>
        </ol>
      </section>

      <section class="panel approvals-safety">
        <header class="panel__head"><h2>Always refused</h2></header>
        <div class="panel__body">
          <ul class="safety-list">
            <li><Icon name="x" :size="14" /> Deleting user files</li>
            <li><Icon name="x" :size="14" /> Silent overwrites</li>
            <li><Icon name="x" :size="14" /> Paths outside registered folders</li>
            <li><Icon name="x" :size="14" /> Arbitrary shell commands</li>
            <li><Icon name="x" :size="14" /> Inferred or blanket consent</li>
          </ul>
          <p class="approvals-safety__note">
            An approval only resolves the request shown. It does not authorize a different action or future run.
          </p>
        </div>
      </section>

      <section class="panel panel--quiet">
        <header class="panel__head"><h2>Approval policy</h2></header>
        <div class="panel__body">
          <p class="approvals-policy">
            File-move gates and workflow gates are controlled in Settings. Manual approval of a workflow before a
            live run is always required, regardless of policy.
          </p>
          <RouterLink to="/settings" class="btn btn--sm btn--block">Open policies</RouterLink>
        </div>
      </section>
    </aside>
  </div>

  <p v-if="oldest" class="approvals-foot">
    Oldest request opened {{ humanDate(oldest) }}. Every decision is written to the local activity record.
  </p>
</template>

<style scoped>
.approvals-error {
  margin: 0 0 1rem;
  padding: 0.65rem 0.85rem;
  border: 1px solid color-mix(in srgb, var(--danger) 30%, var(--line));
  border-left: 2px solid var(--danger);
  border-radius: var(--radius-sm);
  color: var(--danger);
  font-size: 0.78rem;
}
.approvals-summary {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 1.4rem;
  padding: 1.25rem 1.35rem;
  margin-bottom: 1.3rem;
  border: 1px solid color-mix(in srgb, var(--accent) 22%, var(--line));
  border-radius: var(--radius);
  background:
    linear-gradient(120deg, color-mix(in srgb, var(--accent) 7%, transparent), transparent 60%),
    var(--surface);
}
.approvals-summary__lead {
  display: flex;
  align-items: flex-start;
  gap: 0.85rem;
  min-width: 0;
}
.approvals-summary__mark {
  display: grid;
  place-items: center;
  width: 2.6rem;
  height: 2.6rem;
  border: 1px solid color-mix(in srgb, var(--accent) 35%, var(--line));
  border-radius: 50%;
  color: var(--accent);
  flex: none;
}
.approvals-summary h2 {
  margin: 0.3rem 0 0;
  font-family: var(--font-display);
  font-size: clamp(1.3rem, 2.3vw, 1.7rem);
  font-weight: 460;
  letter-spacing: -0.02em;
}
.approvals-summary__lead p:last-child {
  margin: 0.4rem 0 0;
  max-width: 65ch;
  color: var(--text-muted);
  font-size: 0.78rem;
  line-height: 1.55;
}
.approvals-summary__facts {
  display: grid;
  grid-template-columns: repeat(4, minmax(70px, auto));
  gap: 0.9rem;
  align-items: center;
  flex: none;
}
.approvals-summary__facts > div {
  padding-left: 0.9rem;
  border-left: 1px solid var(--line);
}
.approvals-summary__facts span {
  display: block;
  font-family: var(--font-mono);
  font-size: 0.55rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-faint);
}
.approvals-summary__facts strong {
  display: block;
  margin-top: 0.2rem;
  font-family: var(--font-display);
  font-size: 1.1rem;
  font-weight: 460;
  white-space: nowrap;
}
.approvals-summary__facts .approvals-risk {
  color: var(--danger);
}
.approvals-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(290px, 0.75fr);
  gap: 1.4rem;
  align-items: start;
}
.approvals-main {
  min-width: 0;
}
.approvals-list {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}
.approvals-aside {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.panel__hint {
  font-family: var(--font-mono);
  font-size: 0.6rem;
  color: var(--text-faint);
}
.gate-steps {
  list-style: none;
  margin: 0;
  padding: 0.7rem 1rem 0.9rem;
}
.gate-steps li {
  display: flex;
  gap: 0.7rem;
  padding: 0.55rem 0;
  border-bottom: 1px solid color-mix(in srgb, var(--line) 65%, transparent);
}
.gate-steps li:last-child {
  border-bottom: 0;
}
.gate-steps__num {
  font-family: var(--font-mono);
  font-size: 0.62rem;
  color: var(--accent);
  padding-top: 0.15rem;
  flex: none;
}
.gate-steps strong {
  display: block;
  font-size: 0.78rem;
  font-weight: 550;
}
.gate-steps p {
  margin: 0.2rem 0 0;
  color: var(--text-muted);
  font-size: 0.7rem;
  line-height: 1.5;
}
.safety-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}
.safety-list li {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  color: var(--text-muted);
  font-size: 0.74rem;
}
.safety-list .icon {
  color: var(--danger);
}
.approvals-safety__note,
.approvals-policy {
  margin: 0.85rem 0 0;
  color: var(--text-faint);
  font-size: 0.7rem;
  line-height: 1.55;
}
.approvals-policy {
  margin: 0 0 0.75rem;
}
.recent-panel {
  margin-top: 1.1rem;
}
.recent-list {
  display: flex;
  flex-direction: column;
}
.recent-row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.65rem 0.9rem;
  border-bottom: 1px solid var(--line);
  color: var(--text);
}
.recent-row:last-child {
  border-bottom: 0;
}
.recent-row:hover {
  background: var(--surface-2);
  color: var(--text);
}
.recent-row__icon {
  display: grid;
  place-items: center;
  width: 1.5rem;
  height: 1.5rem;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--ok);
  flex: none;
}
.recent-row__copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}
.recent-row__copy strong {
  font-size: 0.74rem;
  font-weight: 550;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recent-row__copy span {
  margin-top: 0.1rem;
  color: var(--text-faint);
  font-size: 0.62rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recent-row > .icon {
  color: var(--text-faint);
}
.approvals-foot {
  margin: 1.5rem 0 0;
  padding-top: 1rem;
  border-top: 1px solid var(--line);
  color: var(--text-faint);
  font-size: 0.68rem;
  line-height: 1.6;
}
@media (max-width: 1100px) {
  .approvals-layout {
    grid-template-columns: 1fr;
  }
  .approvals-summary {
    flex-direction: column;
  }
  .approvals-summary__facts {
    grid-template-columns: repeat(4, 1fr);
  }
  .approvals-summary__facts > div:first-child {
    padding-left: 0;
    border-left: 0;
  }
}
@media (max-width: 620px) {
  .approvals-summary__facts {
    grid-template-columns: 1fr 1fr;
  }
  .approvals-summary__facts > div:nth-child(odd) {
    padding-left: 0;
    border-left: 0;
  }
}
</style>
