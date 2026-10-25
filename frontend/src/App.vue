<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import ToastHost from '@/components/ToastHost.vue'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const bootNotice = ref('')
let approvalTimer: number | undefined

onMounted(async () => {
  await session.bootstrap()
  if (session.error) bootNotice.value = session.error

  if (session.settings && !session.settings.onboardingCompleted && route.name !== 'welcome') {
    await router.replace({ name: 'welcome' })
  }

  approvalTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible' && session.loaded && !session.offline) {
      void session.refreshApprovals().catch(() => undefined)
    }
  }, 45_000)
})

onBeforeUnmount(() => {
  if (approvalTimer) window.clearInterval(approvalTimer)
})
</script>

<template>
  <div v-if="!session.loaded" class="boot" role="status" aria-live="polite">
    <div class="boot__mark" aria-hidden="true"><span /><span /><i /></div>
    <p class="boot__name">GhostShift <em>AI</em></p>
    <p class="boot__line"><span class="spinner" /> Opening your local workspace</p>
    <p class="boot__hint">Files, logs, and history stay on this machine.</p>
  </div>

  <RouterView v-else-if="route.meta.bare" />

  <AppShell v-else>
    <RouterView v-slot="{ Component }">
      <Transition name="page" mode="out-in">
        <component :is="Component" />
      </Transition>
    </RouterView>
  </AppShell>

  <p v-if="bootNotice && session.loaded && !session.offline" class="sr-only">{{ bootNotice }}</p>
  <ToastHost />
</template>

<style scoped>
.boot {
  min-height: 100vh;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 0.55rem;
  padding: 2rem;
  text-align: center;
}
.boot__mark {
  position: relative;
  width: 3.1rem;
  height: 3.1rem;
  margin-bottom: 0.7rem;
  border: 1px solid var(--line-strong);
  border-radius: 0;
  background: var(--surface-2);
  box-shadow: 4px 4px 0 var(--accent);
  overflow: hidden;
  animation: boot-mark 1.1s var(--ease) both;
}
.boot__mark span {
  position: absolute;
  top: 0.35rem;
  width: 1px;
  height: 2.4rem;
  background: var(--accent);
  transform: rotate(20deg);
  opacity: 0.75;
}
.boot__mark span:first-child {
  left: 1.05rem;
}
.boot__mark span:nth-child(2) {
  left: 1.62rem;
  opacity: 0.35;
}
.boot__mark i {
  position: absolute;
  right: 0.5rem;
  bottom: 0.55rem;
  width: 7px;
  height: 7px;
  border-radius: 0;
  background: var(--signal);
}
.boot__name {
  margin: 0;
  font-family: var(--font-display);
  font-size: 2rem;
  font-weight: 600;
  letter-spacing: -0.04em;
}
.boot__name em {
  font-style: normal;
  color: var(--accent);
}
.boot__line {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0.4rem 0 0;
  color: var(--text-muted);
  font-size: 0.82rem;
}
.boot__line .spinner {
  width: 0.75rem;
  height: 0.75rem;
}
.boot__hint {
  margin: 0.75rem 0 0;
  font-family: var(--font-sans);
  font-size: 0.72rem;
  color: var(--text-faint);
}
@keyframes boot-mark {
  from {
    opacity: 0;
    transform: translateY(6px) scale(0.96);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
</style>
