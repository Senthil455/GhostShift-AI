<script setup lang="ts">
import { useToastStore } from '@/stores/toast'
import Icon from './Icon.vue'

const toast = useToastStore()
</script>

<template>
  <div class="toast-host" aria-live="polite">
    <TransitionGroup name="toast">
      <div v-for="item in toast.items" :key="item.id" class="toast" :class="`toast--${item.tone}`">
        <span class="toast__mark">
          <Icon :name="item.tone === 'error' ? 'alert' : item.tone === 'success' ? 'check' : 'dot'" :size="15" />
        </span>
        <div class="toast__copy">
          <strong>{{ item.title }}</strong>
          <p v-if="item.detail">{{ item.detail }}</p>
        </div>
        <button class="toast__close" type="button" aria-label="Dismiss" @click="toast.dismiss(item.id)">
          <Icon name="x" :size="14" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  z-index: 120;
  right: 1.2rem;
  bottom: 1.2rem;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  width: min(380px, calc(100vw - 2.4rem));
  pointer-events: none;
}
.toast {
  pointer-events: auto;
  display: flex;
  align-items: flex-start;
  gap: 0.7rem;
  background: var(--surface-2);
  border: 1px solid var(--line-strong);
  border-left: 3px solid var(--text-muted);
  border-radius: 0;
  padding: 0.8rem 0.9rem;
  box-shadow: 5px 5px 0 var(--shadow-color);
}
.toast--success {
  border-left-color: var(--ok);
}
.toast--error {
  border-left-color: var(--danger);
}
.toast--warning {
  border-left-color: var(--warn);
}
.toast--info {
  border-left-color: var(--info);
}
.toast__mark {
  color: var(--text-muted);
  margin-top: 0.1rem;
}
.toast--success .toast__mark {
  color: var(--ok);
}
.toast--error .toast__mark {
  color: var(--danger);
}
.toast--warning .toast__mark {
  color: var(--warn);
}
.toast__copy {
  flex: 1;
  min-width: 0;
}
.toast__copy strong {
  font-size: 0.84rem;
  font-weight: 600;
}
.toast__copy p {
  margin: 0.25rem 0 0;
  font-size: 0.78rem;
  color: var(--text-muted);
  line-height: 1.45;
}
.toast__close {
  background: none;
  border: none;
  color: var(--text-faint);
  cursor: pointer;
  padding: 0.2rem;
}
.toast-enter-active,
.toast-leave-active {
  transition: all 180ms ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
