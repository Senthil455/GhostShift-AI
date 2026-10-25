<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { api } from '@/api/client'
import type { Category, OrganizationPlan, OrganizationPlanSummary, PlanAction } from '@/api/types'
import { errorMessage } from '@/lib/errors'
import { CATEGORIES, bytes, humanDate, plural, relativeTime } from '@/lib/format'
import { useSessionStore } from '@/stores/session'
import { useToastStore } from '@/stores/toast'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import DirectoryPicker from '@/components/DirectoryPicker.vue'
import EmptyState from '@/components/EmptyState.vue'
import Icon from '@/components/Icon.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'

const session = useSessionStore()
const toast = useToastStore()

const plans = ref<OrganizationPlanSummary[]>([])
const selected = ref<OrganizationPlan | null>(null)
const directoryId = ref<number | null>(null)
const recursive = ref(false)
const useAi = ref(false)
const listLoading = ref(false)
const planLoading = ref(false)
const creating = ref(false)
const error = ref('')
const decisionBusy = ref('')
const categoryBusy = ref('')
const filter = ref('ALL')
const search = ref('')
const remember = ref<Record<string, boolean>>({})
const confirmAction = ref<'execute' | 'undo' | 'cancel' | null>(null)
const confirmBusy = ref(false)
let pollTimer: number | undefined

const selectedDirectory = computed(
  () => session.directories.find((directory) => directory.id === directoryId.value) ?? null,
)
const canOrganize = computed(() => selectedDirectory.value?.allowOrganize === true)
const reviewable = computed(
  () => selected.value?.status === 'READY_FOR_REVIEW' || selected.value?.status === 'APPROVED',
)
const approved = computed(() => selected.value?.actions.filter((action) => action.status === 'APPROVED') ?? [])
const proposed = computed(() => selected.value?.actions.filter((action) => action.status === 'PROPOSED') ?? [])
const counts = computed(() => {
  const result: Record<string, number> = { ALL: selected.value?.actions.length ?? 0 }
  for (const action of selected.value?.actions ?? []) {
    result[action.status] = (result[action.status] ?? 0) + 1
  }
  return result
})
const filters = computed(() => [
  { value: 'ALL', label: 'All', count: counts.value.ALL ?? 0 },
  { value: 'PROPOSED', label: 'Proposed', count: counts.value.PROPOSED ?? 0 },
  { value: 'APPROVED', label: 'Approved', count: counts.value.APPROVED ?? 0 },
  { value: 'MOVED', label: 'Moved', count: counts.value.MOVED ?? 0 },
  { value: 'REJECTED', label: 'Rejected', count: counts.value.REJECTED ?? 0 },
  { value: 'FAILED', label: 'Failed', count: counts.value.FAILED ?? 0 },
])
const filteredActions = computed(() => {
  const query = search.value.trim().toLowerCase()
  return (selected.value?.actions ?? []).filter((action) => {
    const matchesStatus = filter.value === 'ALL' || action.status === filter.value
    const matchesSearch =
      !query ||
      action.fileName.toLowerCase().includes(query) ||
      action.sourceRelative.toLowerCase().includes(query) ||
      action.destinationRelative.toLowerCase().includes(query)
    return matchesStatus && matchesSearch
  })
})
const planIsExecuting = computed(() => selected.value?.status === 'EXECUTING')
const canUndo = computed(
  () => selected.value?.status === 'COMPLETED' || selected.value?.status === 'PARTIALLY_COMPLETED',
)

const confirmTitle = computed(() => {
  if (confirmAction.value === 'execute') return 'Move the approved files?'
  if (confirmAction.value === 'undo') return 'Undo this organization plan?'
  return 'Cancel this plan?'
})
const confirmDescription = computed(() => {
  if (confirmAction.value === 'execute') {
    return `${approved.value.length} approved ${plural(approved.value.length, 'file')} will move into the GhostShift Organizer folder.`
  }
  if (confirmAction.value === 'undo') {
    return 'GhostShift verifies each file before restoring it. Files changed since the move are left alone.'
  }
  return 'Moves already completed are not reversed automatically. The remaining files will not be touched.'
})

function normalizePlan(plan: OrganizationPlan): OrganizationPlan {
  return { ...plan, actions: plan.actions ?? [] }
}

async function loadPlans() {
  listLoading.value = true
  try {
    plans.value = await api.plans(30)
  } catch (cause) {
    error.value = errorMessage(cause, 'Organization plans could not be loaded.')
  } finally {
    listLoading.value = false
  }
}

async function loadPlan(id: string) {
  planLoading.value = true
  error.value = ''
  try {
    selected.value = normalizePlan(await api.plan(id))
    filter.value = 'ALL'
    search.value = ''
    remember.value = {}
    startPolling()
  } catch (cause) {
    error.value = errorMessage(cause, 'That plan could not be opened.')
  } finally {
    planLoading.value = false
  }
}

function stopPolling() {
  if (pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = undefined
  }
}

function startPolling() {
  stopPolling()
  if (selected.value?.status !== 'EXECUTING') return
  pollTimer = window.setInterval(async () => {
    if (!selected.value) return stopPolling()
    try {
      const plan = normalizePlan(await api.plan(selected.value.id))
      selected.value = plan
      await loadPlans()
      if (plan.status !== 'EXECUTING') {
        stopPolling()
        toast.info('Organization plan finished', plan.summary)
      }
    } catch {
      stopPolling()
    }
  }, 1500)
}

async function createPlan() {
  if (!directoryId.value) {
    error.value = 'Choose a registered folder before building a plan.'
    return
  }
  if (!canOrganize.value) {
    error.value = 'That folder is registered as read-only. Choose a folder with organize permission.'
    return
  }
  creating.value = true
  error.value = ''
  try {
    const plan = normalizePlan(
      await api.createPlan({
        directoryId: directoryId.value,
        recursive: recursive.value,
        useAi: useAi.value,
      }),
    )
    selected.value = plan
    filter.value = 'ALL'
    await loadPlans()
    toast.success('Plan ready for review', `${plan.fileCount} ${plural(plan.fileCount, 'file')} classified. Nothing moved yet.`)
  } catch (cause) {
    error.value = errorMessage(cause, 'The organization plan could not be created.')
  } finally {
    creating.value = false
  }
}

async function refreshSelected() {
  if (!selected.value) return
  selected.value = normalizePlan(await api.plan(selected.value.id))
}

async function decide(action: PlanAction, decision: 'APPROVE' | 'REJECT') {
  if (!selected.value) return
  decisionBusy.value = action.id
  error.value = ''
  try {
    selected.value = normalizePlan(
      await api.decidePlan(selected.value.id, [{ actionId: action.id, decision }], false),
    )
    await loadPlans()
    toast.success(decision === 'APPROVE' ? 'File approved' : 'File rejected', action.fileName)
  } catch (cause) {
    error.value = errorMessage(cause, 'The decision could not be saved.')
  } finally {
    decisionBusy.value = ''
  }
}

async function approveRemaining() {
  if (!selected.value || proposed.value.length === 0) {
    toast.info('Nothing to approve', 'Every proposed file already has a decision.')
    return
  }
  decisionBusy.value = 'all'
  error.value = ''
  try {
    selected.value = normalizePlan(
      await api.decidePlan(
        selected.value.id,
        proposed.value.map((action) => ({ actionId: action.id, decision: 'APPROVE' as const })),
        false,
      ),
    )
    await loadPlans()
    toast.success('Proposed files approved', `${proposed.value.length} ${plural(proposed.value.length, 'file')} ready to execute.`)
  } catch (cause) {
    error.value = errorMessage(cause, 'The files could not be approved.')
  } finally {
    decisionBusy.value = ''
  }
}

async function changeCategory(action: PlanAction, event: Event) {
  if (!selected.value) return
  const category = (event.target as HTMLSelectElement).value as Category
  if (category === action.category) return
  const shouldRemember = Boolean(remember.value[action.id])
  categoryBusy.value = action.id
  error.value = ''
  try {
    selected.value = normalizePlan(
      await api.patchPlanAction(selected.value.id, action.id, {
        category,
        remember: shouldRemember,
      }),
    )
    await loadPlans()
    if (shouldRemember) {
      remember.value[action.id] = false
    }
    toast.info(
      shouldRemember ? 'Category changed and remembered' : 'Destination recalculated',
      `${action.fileName} now routes by ${category.toLowerCase().replaceAll('_', ' ')}.`,
    )
  } catch (cause) {
    error.value = errorMessage(cause, 'That category could not be applied.')
    if (selected.value) {
      selected.value = normalizePlan(await api.plan(selected.value.id).catch(() => selected.value!))
    }
  } finally {
    categoryBusy.value = ''
  }
}

async function rememberCurrent(action: PlanAction, event: Event) {
  if (!selected.value) return
  const checked = (event.target as HTMLInputElement).checked
  if (!checked) return
  categoryBusy.value = action.id
  error.value = ''
  try {
    selected.value = normalizePlan(
      await api.patchPlanAction(selected.value.id, action.id, { remember: true }),
    )
    await loadPlans()
    toast.success(
      'Correction remembered',
      `Future files named ${action.fileName} will route by ${action.category.toLowerCase().replaceAll('_', ' ')}.`,
    )
  } catch (cause) {
    remember.value[action.id] = false
    error.value = errorMessage(cause, 'That correction could not be remembered.')
  } finally {
    categoryBusy.value = ''
  }
}

async function confirmCurrent() {
  if (!selected.value || !confirmAction.value) return
  confirmBusy.value = true
  error.value = ''
  try {
    if (confirmAction.value === 'execute') {
      selected.value = normalizePlan(await api.executePlan(selected.value.id))
      await loadPlans()
      toast.success('Execution started', 'Only approved files will move. Destinations are checked again.')
      startPolling()
    } else if (confirmAction.value === 'undo') {
      selected.value = normalizePlan(await api.undoPlan(selected.value.id))
      await loadPlans()
      toast.success('Undo complete', selected.value.summary)
    } else {
      selected.value = normalizePlan(await api.cancelPlan(selected.value.id))
      await loadPlans()
      stopPolling()
      toast.info('Plan cancelled', 'Completed moves were left in place.')
    }
    confirmAction.value = null
  } catch (cause) {
    error.value = errorMessage(cause, 'That action could not be completed.')
  } finally {
    confirmBusy.value = false
  }
}

async function refreshAll() {
  error.value = ''
  await Promise.all([loadPlans(), refreshSelected()])
  startPolling()
}

onMounted(async () => {
  if (session.directories.length === 0) {
    await session.refreshDirectories().catch(() => undefined)
  }
  await loadPlans()
  if (plans.value.length > 0) await loadPlan(plans.value[0].id)
})

onBeforeUnmount(stopPolling)
</script>

<template>
  <PageHeader
    eyebrow="GhostSort"
    title="Put a folder in order."
    description="Scan, classify, and preview every destination. GhostShift proposes; you approve each file before anything moves."
  >
    <template #actions>
      <button class="btn btn--ghost btn--sm" type="button" :disabled="listLoading" @click="refreshAll">
        <span v-if="listLoading" class="spinner" />
        <Icon v-else name="refresh" :size="15" />
        Refresh
      </button>
    </template>
  </PageHeader>

  <p v-if="error" class="sorter-error" role="alert">{{ error }}</p>

  <section class="panel sorter-create">
    <div class="sorter-create__intro">
      <span class="eyebrow">New review plan</span>
      <h2>Nothing moves during a scan.</h2>
      <p>
        Deterministic filename rules run first. The local model may only help with ambiguous names, and its
        suggestions are still shown as proposals for you to approve.
      </p>
    </div>
    <div class="sorter-create__form">
      <DirectoryPicker
        v-model="directoryId"
        label="Folder to inspect"
        hint="Destinations are always placed under GhostShift Organizer inside this folder."
        :organize-default="true"
      />
      <div class="sorter-options">
        <label class="check">
          <input v-model="recursive" type="checkbox" />
          Include subfolders
        </label>
        <label class="check">
          <input v-model="useAi" type="checkbox" />
          Ask GhostBrain about ambiguous files
        </label>
        <button class="btn btn--primary" type="button" :disabled="creating || !directoryId" @click="createPlan">
          <span v-if="creating" class="spinner" />
          <Icon v-else name="sort" :size="15" />
          Build plan
        </button>
      </div>
      <p v-if="selectedDirectory && !selectedDirectory.allowOrganize" class="sorter-warning">
        <Icon name="lock" :size="13" />
        This folder is read-only. Register it with organize permission to move files.
      </p>
      <p v-else-if="!session.directories.length" class="field__hint">
        No folders are registered yet. Add a folder to begin.
      </p>
    </div>
  </section>

  <section v-if="plans.length" class="plan-history" aria-label="Recent organization plans">
    <div class="plan-history__head">
      <h2>Recent plans</h2>
      <span class="mono">{{ plans.length }} {{ plural(plans.length, 'record') }}</span>
    </div>
    <div class="plan-history__strip">
      <button
        v-for="plan in plans"
        :key="plan.id"
        class="plan-chip"
        :class="{ 'plan-chip--active': selected?.id === plan.id }"
        type="button"
        @click="loadPlan(plan.id)"
      >
        <span class="plan-chip__top">
          <span class="plan-chip__name">{{ plan.directoryName }}</span>
          <StatusPill :status="plan.status" />
        </span>
        <span class="plan-chip__meta">
          {{ plan.fileCount }} files · {{ plan.conflictCount }} conflicts · {{ relativeTime(plan.createdAt) }}
        </span>
      </button>
    </div>
  </section>

  <div v-if="planLoading" class="panel panel__body sorter-loading">
    <span class="spinner" /> Opening plan…
  </div>

  <template v-else-if="selected">
    <section class="panel plan">
      <header class="plan__head">
        <div class="plan__identity">
          <div class="row">
            <StatusPill :status="selected.status" dot />
            <span class="chip">{{ selected.fileCount }} files</span>
            <span v-if="selected.conflictCount" class="chip chip--accent">{{ selected.conflictCount }} conflicts</span>
          </div>
          <h2>{{ selected.directoryName }}</h2>
          <p class="plan__path mono">{{ selected.directoryPath }}</p>
          <p class="plan__summary">{{ selected.summary }}</p>
        </div>
        <div class="plan__aside">
          <span class="eyebrow">Created</span>
          <span class="plan__date">{{ humanDate(selected.createdAt) }}</span>
          <span class="eyebrow">Executed</span>
          <span class="plan__date">{{ selected.executedAt ? humanDate(selected.executedAt) : 'Not yet' }}</span>
        </div>
      </header>

      <div class="plan__bar">
        <div class="tabs" role="tablist" aria-label="Filter files">
          <button
            v-for="item in filters"
            :key="item.value"
            class="tab"
            :class="{ 'tab--active': filter === item.value }"
            type="button"
            role="tab"
            :aria-selected="filter === item.value"
            @click="filter = item.value"
          >
            {{ item.label }} <span class="tab__count">{{ item.count }}</span>
          </button>
        </div>
        <div class="plan__tools">
          <label class="search-box">
            <Icon name="search" :size="14" />
            <input v-model="search" type="search" placeholder="Filter files" aria-label="Filter plan files" />
          </label>
          <button
            v-if="reviewable"
            class="btn btn--sm"
            type="button"
            :disabled="proposed.length === 0 || decisionBusy !== ''"
            @click="approveRemaining"
          >
            <span v-if="decisionBusy === 'all'" class="spinner" />
            <Icon v-else name="check" :size="14" />
            Approve proposed ({{ proposed.length }})
          </button>
          <button
            v-if="selected.status === 'APPROVED'"
            class="btn btn--sm btn--primary"
            type="button"
            :disabled="approved.length === 0 || planIsExecuting"
            @click="confirmAction = 'execute'"
          >
            <Icon name="play" :size="14" />
            Execute {{ approved.length }}
          </button>
          <button
            v-if="planIsExecuting"
            class="btn btn--sm btn--danger"
            type="button"
            @click="confirmAction = 'cancel'"
          >
            <Icon name="x" :size="14" />
            Cancel
          </button>
          <button v-if="canUndo" class="btn btn--sm" type="button" @click="confirmAction = 'undo'">
            <Icon name="undo" :size="14" />
            Undo
          </button>
        </div>
      </div>

      <div v-if="planIsExecuting" class="plan__notice notice notice--accent">
        <span class="spinner" />
        <span>Moving approved files now. This page refreshes automatically.</span>
      </div>
      <div v-else-if="selected.status === 'READY_FOR_REVIEW'" class="plan__notice notice">
        <Icon name="shield" :size="15" class="notice__icon" />
        <span>
          <strong>Nothing has moved.</strong> Approve files individually or approve all proposed files. Then execute
          the approved set. Conflicts are renamed, never overwritten.
        </span>
      </div>
      <div v-else-if="selected.status === 'COMPLETED'" class="plan__notice notice notice--accent">
        <Icon name="check" :size="15" class="notice__icon" />
        <span><strong>Complete.</strong> {{ approved.length }} approved {{ plural(approved.length, 'file') }} processed. Undo verifies every file before restoring it.</span>
      </div>

      <div class="table-wrap">
        <table class="table plan-table">
          <thead>
            <tr>
              <th>File</th>
              <th>Category</th>
              <th>Destination</th>
              <th>Evidence</th>
              <th>Status</th>
              <th><span class="sr-only">Decision</span></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="action in filteredActions" :key="action.id">
              <td class="plan-table__file">
                <span class="cell-main">{{ action.fileName }}</span>
                <span class="cell-sub">{{ action.sourceRelative }} · {{ bytes(action.sizeBytes) }}</span>
                <span v-if="action.conflict" class="plan-conflict">
                  <Icon name="alert" :size="11" /> Destination exists — will be renamed safely
                </span>
                <span v-if="action.error" class="plan-error">{{ action.error }}</span>
              </td>
              <td>
                <div class="category-cell">
                  <select
                    :value="action.category"
                    :disabled="!reviewable || categoryBusy === action.id"
                    :aria-label="`Category for ${action.fileName}`"
                    @change="changeCategory(action, $event)"
                  >
                    <option v-for="category in CATEGORIES" :key="category.value" :value="category.value">
                      {{ category.label }}
                    </option>
                  </select>
                  <label class="check category-cell__remember" title="Check this before changing the category to remember the correction for files with the same name.">
                    <input
                      v-model="remember[action.id]"
                      type="checkbox"
                      :disabled="!reviewable || categoryBusy === action.id"
                      @change="rememberCurrent(action, $event)"
                    />
                    Remember
                  </label>
                </div>
              </td>
              <td class="plan-table__destination mono">{{ action.destinationRelative }}</td>
              <td>
                <div class="confidence">
                  <span class="confidence__value">{{ Math.round(action.confidence * 100) }}%</span>
                  <span class="chip">{{ action.classificationSource }}</span>
                </div>
                <span class="cell-sub">{{ action.rationale }}</span>
              </td>
              <td><StatusPill :status="action.status" /></td>
              <td class="plan-table__actions">
                <button
                  v-if="reviewable && action.status !== 'APPROVED' && action.status !== 'MOVED'"
                  class="icon-btn plan-approve"
                  type="button"
                  :disabled="decisionBusy === action.id"
                  :aria-label="`Approve ${action.fileName}`"
                  @click="decide(action, 'APPROVE')"
                >
                  <span v-if="decisionBusy === action.id" class="spinner" />
                  <Icon v-else name="check" :size="15" />
                </button>
                <button
                  v-if="reviewable && action.status === 'APPROVED'"
                  class="icon-btn plan-reject"
                  type="button"
                  :disabled="decisionBusy === action.id"
                  :aria-label="`Reject ${action.fileName}`"
                  @click="decide(action, 'REJECT')"
                >
                  <Icon name="x" :size="15" />
                </button>
                <span v-if="!reviewable && action.status !== 'APPROVED'" class="faint tiny">—</span>
              </td>
            </tr>
            <tr v-if="filteredActions.length === 0">
              <td colspan="6">
                <EmptyState compact icon="search" title="No files match this view" description="Change the filter or search text." />
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <footer class="plan__foot">
        <span class="mono">
          {{ counts.ALL ?? 0 }} total · {{ counts.APPROVED ?? 0 }} approved · {{ counts.MOVED ?? 0 }} moved ·
          {{ counts.FAILED ?? 0 }} failed
        </span>
        <span v-if="selected.status === 'APPROVED'" class="plan__ready">
          <Icon name="shield" :size="13" />
          Approved. Execution is the only step that touches files.
        </span>
      </footer>
    </section>
  </template>

  <EmptyState
    v-else-if="!planLoading"
    icon="sort"
    title="No organization plan selected"
    description="Choose a registered folder above and build a plan. GhostShift will classify the files and show every destination before asking for approval."
  />

  <ConfirmDialog
    :open="confirmAction !== null"
    :title="confirmTitle"
    :description="confirmDescription"
    :confirm-label="confirmAction === 'execute' ? 'Execute approved moves' : confirmAction === 'undo' ? 'Undo verified moves' : 'Cancel plan'"
    :tone="confirmAction === 'execute' ? 'primary' : 'danger'"
    :busy="confirmBusy"
    @close="confirmAction = null"
    @confirm="confirmCurrent"
  >
    <div v-if="confirmAction === 'execute'" class="stack stack--sm">
      <div class="notice notice--accent">
        <Icon name="shield" :size="15" class="notice__icon" />
        <span>
          GhostShift revalidates the source, size, timestamp, and destination before each move. Existing files are
          renamed, never overwritten. Every move records recovery metadata.
        </span>
      </div>
      <p class="muted small">{{ approved.length }} {{ plural(approved.length, 'file') }} will move under <code>GhostShift Organizer</code>.</p>
    </div>
    <div v-else-if="confirmAction === 'undo'" class="notice">
      <Icon name="undo" :size="15" class="notice__icon" />
      <span>Only files that still match the recorded move are restored. Anything changed since then is left in place for your review.</span>
    </div>
    <div v-else class="notice notice--warning">
      <Icon name="alert" :size="15" class="notice__icon" />
      <span>This stops the plan after the current safe move. Files already moved stay where they are until you undo.</span>
    </div>
  </ConfirmDialog>
</template>

<style scoped>
.sorter-error {
  margin: 0 0 1.1rem;
  padding: 0.7rem 0.9rem;
  border: 1px solid color-mix(in srgb, var(--danger) 30%, var(--line));
  border-left: 2px solid var(--danger);
  border-radius: var(--radius-sm);
  color: var(--danger);
  font-size: 0.8rem;
}
.sorter-create {
  display: grid;
  grid-template-columns: minmax(240px, 0.8fr) minmax(0, 1.4fr);
  gap: 1.8rem;
  padding: 1.4rem 1.5rem;
  margin-bottom: 1.5rem;
  background:
    linear-gradient(140deg, color-mix(in srgb, var(--accent) 5%, transparent), transparent 45%),
    var(--surface);
}
.sorter-create__intro h2 {
  margin: 0.45rem 0 0;
  font-family: var(--font-display);
  font-size: clamp(1.35rem, 2vw, 1.7rem);
  font-weight: 470;
  letter-spacing: -0.02em;
}
.sorter-create__intro p {
  margin: 0.55rem 0 0;
  max-width: 44ch;
  font-size: 0.8rem;
  line-height: 1.6;
  color: var(--text-muted);
}
.sorter-create__form {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  min-width: 0;
}
.sorter-options {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
}
.sorter-options .btn {
  margin-left: auto;
}
.sorter-warning {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin: 0;
  color: var(--warn);
  font-size: 0.73rem;
}
.plan-history {
  margin-bottom: 1.4rem;
}
.plan-history__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.55rem;
}
.plan-history__head h2 {
  margin: 0;
  font-size: 0.8rem;
  font-weight: 600;
}
.plan-history__head span {
  color: var(--text-faint);
  font-size: 0.6rem;
}
.plan-history__strip {
  display: flex;
  gap: 0.55rem;
  overflow-x: auto;
  padding-bottom: 0.25rem;
  scrollbar-width: thin;
}
.plan-chip {
  flex: 0 0 240px;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  padding: 0.65rem 0.75rem;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--surface);
  text-align: left;
  color: var(--text);
  transition:
    border-color 140ms ease,
    background 140ms ease;
}
.plan-chip:hover {
  border-color: var(--line-strong);
  background: var(--surface-2);
}
.plan-chip--active {
  border-color: color-mix(in srgb, var(--accent) 35%, var(--line));
  background: color-mix(in srgb, var(--accent) 6%, var(--surface));
}
.plan-chip__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}
.plan-chip__name {
  font-size: 0.78rem;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.plan-chip__meta {
  font-family: var(--font-mono);
  font-size: 0.58rem;
  color: var(--text-faint);
}
.sorter-loading {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--text-muted);
  font-size: 0.8rem;
}
.plan__head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 1.5rem;
  padding: 1.25rem 1.4rem;
  border-bottom: 1px solid var(--line);
}
.plan__identity h2 {
  margin: 0.55rem 0 0.15rem;
  font-family: var(--font-display);
  font-size: 1.7rem;
  font-weight: 470;
  letter-spacing: -0.02em;
  line-height: 1.1;
}
.plan__path {
  margin: 0;
  color: var(--text-faint);
  font-size: 0.62rem;
  overflow-wrap: anywhere;
}
.plan__summary {
  margin: 0.65rem 0 0;
  max-width: 80ch;
  color: var(--text-muted);
  font-size: 0.8rem;
  line-height: 1.55;
}
.plan__aside {
  display: grid;
  grid-template-columns: auto;
  gap: 0.2rem;
  align-content: start;
  min-width: 140px;
}
.plan__aside .eyebrow {
  margin-top: 0.4rem;
}
.plan__aside .eyebrow:first-child {
  margin-top: 0;
}
.plan__date {
  font-family: var(--font-mono);
  font-size: 0.63rem;
  color: var(--text-muted);
}
.plan__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--line);
  flex-wrap: wrap;
}
.plan__tools {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  flex-wrap: wrap;
}
.tab__count {
  font-family: var(--font-mono);
  font-size: 0.6rem;
  opacity: 0.7;
  margin-left: 0.15rem;
}
.search-box {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0 0.55rem;
  min-height: 2rem;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--ink);
  color: var(--text-faint);
}
.search-box input {
  min-height: 0 !important;
  width: 10rem;
  padding: 0.25rem 0 !important;
  border: 0 !important;
  background: transparent !important;
  font-size: 0.74rem !important;
}
.plan__notice {
  margin: 0.9rem 1rem 0;
}
.plan-table__file {
  min-width: 240px;
}
.plan-table__destination {
  color: var(--text-muted);
  font-size: 0.64rem;
  max-width: 260px;
  overflow-wrap: anywhere;
}
.plan-table__actions {
  text-align: right;
  white-space: nowrap;
}
.plan-approve {
  color: var(--ok);
}
.plan-reject {
  color: var(--danger);
}
.plan-conflict {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  margin-top: 0.25rem;
  font-size: 0.65rem;
  color: var(--warn);
}
.plan-error {
  display: block;
  margin-top: 0.25rem;
  color: var(--danger);
  font-size: 0.66rem;
}
.category-cell {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  min-width: 150px;
}
.category-cell select {
  min-height: 1.9rem;
  padding: 0.25rem 1.7rem 0.25rem 0.5rem;
  font-size: 0.74rem;
}
.category-cell__remember {
  font-size: 0.63rem;
  gap: 0.3rem;
}
.category-cell__remember input {
  width: 0.75rem;
  height: 0.75rem;
}
.confidence {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}
.confidence__value {
  font-family: var(--font-mono);
  font-size: 0.68rem;
  color: var(--text);
}
.plan__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.75rem 1.1rem;
  border-top: 1px solid var(--line);
  color: var(--text-faint);
  font-size: 0.62rem;
  flex-wrap: wrap;
}
.plan__ready {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  color: var(--ok);
  font-family: var(--font-mono);
  font-size: 0.6rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

@media (max-width: 1000px) {
  .sorter-create {
    grid-template-columns: 1fr;
    gap: 1.2rem;
  }
}
@media (max-width: 760px) {
  .plan__head {
    grid-template-columns: 1fr;
  }
  .plan__aside {
    grid-template-columns: auto auto;
    gap: 0.25rem 0.8rem;
  }
  .plan__bar {
    align-items: stretch;
    flex-direction: column;
  }
  .tabs {
    overflow-x: auto;
  }
  .plan__tools {
    justify-content: flex-start;
  }
  .search-box {
    flex: 1;
  }
  .search-box input {
    width: 100%;
  }
}
</style>
