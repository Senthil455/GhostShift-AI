<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '@/api/client'
import type { Approval, WorkflowRun } from '@/api/types'
import { useSessionStore } from '@/stores/session'
import { useToastStore } from '@/stores/toast'
import { errorMessage, errorDetails } from '@/lib/errors'
import { humanDate, relativeTime, riskTone } from '@/lib/format'
import Icon from './Icon.vue'
import Modal from './Modal.vue'
import StatusPill from './StatusPill.vue'

const props = defineProps<{ approval: Approval }>()
const emit = defineEmits<{ decided: [run: WorkflowRun] }>()

const session = useSessionStore()
const toast = useToastStore()

const modalOpen = ref(false)
const mode = ref<'approve' | 'reject'>('approve')
const note = ref('')
const busy = ref(false)
const error = ref('')

function ask(next: 'approve' | 'reject') {
  mode.value = next
  note.value = ''
  error.value = ''
  modalOpen.value = true
}

async function submit() {
  busy.value = true
  error.value = ''
  try {
    const run =
      mode.value === 'approve'
        ? await api.approve(props.approval.id, note.value.trim())
        : await api.reject(props.approval.id, note.value.trim())
    toast.success(
      mode.value === 'approve' ? 'Approval recorded' : 'Approval rejected',
      mode.value === 'approve' ? 'The workflow can continue.' : 'The workflow will not cross this gate.',
    )
    await Promise.all([
      session.refreshApprovals().catch(() => undefined),
      session.refreshDashboard().catch(() => undefined),
    ])
    modalOpen.value = false
    emit('decided', run)
  } catch (cause) {
    const details = errorDetails(cause)
    error.value = details.length ? `${errorMessage(cause)} ${details.join(' ')}` : errorMessage(cause)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <article class="approval-card">
    <header class="approval-card__head">
      <div class="approval-card__title">
        <span class="eyebrow">{{ approval.kind.replaceAll('_', ' ') }}</span>
        <h2>{{ approval.title }}</h2>
        <p class="approval-card__workflow">
          {{ approval.workflowName }}
          <span class="faint">·</span>
          <span class="faint">{{ relativeTime(approval.createdAt) }}</span>
        </p>
      </div>
      <StatusPill :tone="riskTone(approval.risk)" :label="`${approval.risk} risk`" />
    </header>

    <p class="approval-card__explanation">{{ approval.explanation }}</p>

    <footer class="approval-card__foot">
      <div class="approval-card__meta">
        <span class="mono">Requested {{ humanDate(approval.createdAt) }}</span>
        <RouterLink :to="{ name: 'run-detail', params: { runId: approval.runId } }" class="approval-card__link">
          Open run <Icon name="arrow" :size="13" />
        </RouterLink>
      </div>
      <div class="row">
        <button class="btn btn--sm btn--danger" type="button" @click="ask('reject')">
          <Icon name="x" :size="14" />
          Reject
        </button>
        <button class="btn btn--sm btn--primary" type="button" @click="ask('approve')">
          <Icon name="check" :size="14" />
          Approve
        </button>
      </div>
    </footer>
  </article>

  <Modal
    :open="modalOpen"
    :title="mode === 'approve' ? 'Approve this action?' : 'Reject this action?'"
    :subtitle="approval.title"
    @close="modalOpen = false"
  >
    <div class="stack">
      <p class="approval-card__explanation">{{ approval.explanation }}</p>
      <div class="notice" :class="mode === 'approve' ? 'notice--accent' : 'notice--danger'">
        <Icon :name="mode === 'approve' ? 'shield' : 'x'" :size="15" class="notice__icon" />
        <span v-if="mode === 'approve'">
          Approving lets this run continue. GhostShift still refuses deletes, silent overwrites, and paths outside
          registered folders.
        </span>
        <span v-else>
          The run will stop at this gate. Any completed steps remain recorded and are not reversed automatically.
        </span>
      </div>
      <div class="field">
        <label for="approval-note">Decision note <span class="faint">(optional)</span></label>
        <textarea
          id="approval-note"
          v-model="note"
          maxlength="500"
          rows="3"
          placeholder="Why are you making this decision?"
        />
      </div>
      <p v-if="error" class="approval-card__error" role="alert">{{ error }}</p>
    </div>
    <template #footer>
      <button class="btn btn--ghost" type="button" @click="modalOpen = false">Cancel</button>
      <button
        class="btn"
        :class="mode === 'approve' ? 'btn--primary' : 'btn--danger'"
        type="button"
        :disabled="busy"
        @click="submit"
      >
        <span v-if="busy" class="spinner" />
        <Icon v-else :name="mode === 'approve' ? 'check' : 'x'" :size="15" />
        {{ mode === 'approve' ? 'Approve action' : 'Reject action' }}
      </button>
    </template>
  </Modal>
</template>

<style scoped>
.approval-card {
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: color-mix(in srgb, var(--surface) 90%, transparent);
  padding: 1.15rem 1.2rem;
}
.approval-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}
.approval-card__title h2 {
  margin: 0.3rem 0 0;
  font-family: var(--font-display);
  font-size: 1.25rem;
  font-weight: 470;
  letter-spacing: -0.015em;
}
.approval-card__workflow {
  margin: 0.35rem 0 0;
  font-family: var(--font-mono);
  font-size: 0.65rem;
  color: var(--text-muted);
}
.approval-card__explanation {
  margin: 0.85rem 0 0;
  color: var(--text-muted);
  font-size: 0.83rem;
  line-height: 1.55;
}
.approval-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.8rem;
  margin-top: 1.1rem;
  padding-top: 0.9rem;
  border-top: 1px solid var(--line);
}
.approval-card__meta {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  color: var(--text-faint);
  font-size: 0.66rem;
}
.approval-card__link {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-family: var(--font-mono);
  font-size: 0.64rem;
  color: var(--accent);
}
.approval-card__error {
  margin: 0;
  color: var(--danger);
  font-size: 0.78rem;
}
</style>
