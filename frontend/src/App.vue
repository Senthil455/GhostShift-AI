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
    <div class="boot__machine" aria-hidden="true">
      <span class="boot__card boot__card--back" />
      <span class="boot__card boot__card--front">
        <i />
        <b />
      </span>
      <span class="boot__slot" />
    </div>
    <p class="boot__name">GhostShift</p>
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
  gap: 0.5rem;
  padding: 2rem;
  text-align: center;
}
.boot__machine {
  position: relative;
  width: 4.6rem;
  height: 4.6rem;
  margin-bottom: 1rem;
  perspective: 400px;
}
.boot__card {
  position: absolute;
  inset: 0.35rem 0.6rem auto 0.6rem;
  height: 2.6rem;
  border-radius: 1px;
  background:
    linear-gradient(160deg, rgb(255 255 255 / 0.5), transparent 45%),
    var(--paper);
  box-shadow: 0 6px 14px -8px rgb(0 0 0 / 0.8);
  transform-origin: 50% 100%;
}
.boot__card--back {
  transform: rotate(-7deg) translateY(-0.5rem);
  opacity: 0.4;
  animation: card-settle 1.6s var(--ease) infinite alternate;
}
.boot__card--front {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0.5rem;
  transform: rotate(2deg);
  animation: card-feed 2.4s var(--ease) infinite;
}
.boot__card--front i {
  width: 0.32rem;
  height: 0.32rem;
  border-radius: 50%;
  background: color-mix(in srgb, var(--paper-ink) 70%, transparent);
}
.boot__card--front b {
  width: 60%;
  height: 3px;
  margin-top: auto;
  background: var(--signal);
}
.boot__slot {
  position: absolute;
  left: 0.1rem;
  right: 0.1rem;
  bottom: 0.55rem;
  height: 0.32rem;
  border-radius: 1px;
  background: var(--surface-3);
  box-shadow:
    inset 0 1px 3px rgb(0 0 0 / 0.8),
    0 1px 0 rgb(255 255 255 / 0.06);
}
.boot__name {
  margin: 0;
  font-family: var(--font-display);
  font-size: 2.1rem;
  font-weight: 500;
  letter-spacing: -0.02em;
}
.boot__line {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0.35rem 0 0;
  color: var(--text-muted);
  font-size: 0.84rem;
}
.boot__line .spinner {
  width: 0.75rem;
  height: 0.75rem;
}
.boot__hint {
  margin: 0.7rem 0 0;
  font-size: 0.76rem;
  color: var(--text-faint);
}
@keyframes card-feed {
  0% {
    transform: translateY(-0.35rem) rotate(2deg);
  }
  50% {
    transform: translateY(0.16rem) rotate(1deg);
  }
  100% {
    transform: translateY(-0.35rem) rotate(2deg);
  }
}
@keyframes card-settle {
  from {
    transform: rotate(-9deg) translateY(-0.55rem);
  }
  to {
    transform: rotate(-5deg) translateY(-0.35rem);
  }
}
</style>
