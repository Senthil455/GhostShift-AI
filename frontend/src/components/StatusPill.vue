<script setup lang="ts">
import { computed } from 'vue'
import { humanStatus } from '@/lib/format'

const props = defineProps<{
  status?: string | null
  label?: string
  tone?: string
  dot?: boolean
}>()

const tone = computed(() => {
  if (props.tone) return props.tone
  switch ((props.status ?? '').toUpperCase()) {
    case 'COMPLETED':
    case 'SUCCEEDED':
    case 'APPROVED':
    case 'ACTIVE':
    case 'OK':
    case 'LOW':
    case 'READY':
      return 'success'
    case 'RUNNING':
    case 'EXECUTING':
    case 'MOVED':
      return 'info'
    case 'QUEUED':
    case 'WAITING_FOR_APPROVAL':
    case 'PAUSED':
    case 'READY_FOR_REVIEW':
    case 'DRAFT':
    case 'PROPOSED':
    case 'MODERATE':
    case 'WARNING':
      return 'warning'
    case 'FAILED':
    case 'ERROR':
    case 'REJECTED':
    case 'CANCELLED':
    case 'UNDO_FAILED':
    case 'HIGH':
    case 'DEGRADED':
      return 'danger'
    case 'UNDONE':
    case 'ARCHIVED':
    case 'STOPPED':
    case 'SKIPPED':
      return 'neutral'
    default:
      return 'neutral'
  }
})

const text = computed(() => props.label ?? humanStatus(props.status))
</script>

<template>
  <span class="pill" :class="`pill--${tone}`">
    <span v-if="dot" class="pill__dot" />
    {{ text }}
  </span>
</template>

<style scoped>
.pill {
  display: inline-flex;
  align-items: center;
  gap: 0.38rem;
  padding: 0.2rem 0.45rem;
  border-radius: 0;
  border: 1px solid transparent;
  font-family: var(--font-sans);
  font-size: 0.7rem;
  font-weight: 650;
  letter-spacing: -0.005em;
  line-height: 1.2;
  text-transform: none;
  white-space: nowrap;
}
.pill__dot {
  width: 5px;
  height: 5px;
  border-radius: 0;
  background: currentColor;
}
.pill--success {
  color: var(--ok);
  border-color: color-mix(in srgb, var(--ok) 28%, transparent);
  background: color-mix(in srgb, var(--ok) 9%, transparent);
}
.pill--info {
  color: var(--info);
  border-color: color-mix(in srgb, var(--info) 28%, transparent);
  background: color-mix(in srgb, var(--info) 9%, transparent);
}
.pill--warning {
  color: var(--warn);
  border-color: color-mix(in srgb, var(--warn) 28%, transparent);
  background: color-mix(in srgb, var(--warn) 9%, transparent);
}
.pill--danger {
  color: var(--danger);
  border-color: color-mix(in srgb, var(--danger) 28%, transparent);
  background: color-mix(in srgb, var(--danger) 9%, transparent);
}
.pill--neutral {
  color: var(--text-muted);
  border-color: var(--line);
  background: var(--surface-2);
}
</style>
