<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import Icon from './Icon.vue'

const route = useRoute()
const session = useSessionStore()
const sidebarOpen = ref(false)

const sections = [
  {
    label: 'Studio',
    items: [
      { to: '/overview', label: 'Overview', icon: 'grid' },
      { to: '/sort', label: 'GhostSort', icon: 'sort' },
      { to: '/dev', label: 'GhostDev', icon: 'bug' },
      { to: '/capture', label: 'GhostCapture', icon: 'record' },
      { to: '/replay', label: 'GhostReplay', icon: 'play' },
    ],
  },
  {
    label: 'Intelligence',
    items: [
      { to: '/brain', label: 'GhostBrain', icon: 'brain' },
      { to: '/pulse', label: 'GhostPulse', icon: 'pulse' },
      { to: '/approvals', label: 'Approvals', icon: 'shield', badge: true },
      { to: '/history', label: 'History', icon: 'clock' },
    ],
  },
  {
    label: 'System',
    items: [{ to: '/settings', label: 'Settings', icon: 'settings' }],
  },
]

const title = computed(() => (typeof route.meta.title === 'string' ? route.meta.title : 'GhostShift AI'))
const group = computed(() => (typeof route.meta.group === 'string' ? route.meta.group : 'Local workspace'))
const pending = computed(() => session.pendingApprovals.length)
const aiLabel = computed(() => {
  if (!session.ai) return 'AI status unknown'
  if (!session.ai.reachable) return 'Deterministic mode'
  if (!session.ai.modelAvailable) return 'Model not installed'
  return session.ai.model
})
const aiTone = computed(() => {
  if (!session.ai?.reachable) return 'neutral'
  return session.ai.modelAvailable ? 'ok' : 'warn'
})

function isActive(to: string): boolean {
  if (to === '/history') return route.path.startsWith('/history')
  if (to === '/overview') return route.path === '/overview' || route.path === '/'
  return route.path === to
}

function toggleTheme() {
  void session.setTheme(session.theme === 'dark' ? 'light' : 'dark')
}

watch(
  () => route.fullPath,
  () => {
    sidebarOpen.value = false
  },
)
</script>

<template>
  <div class="shell">
    <a class="skip-link" href="#main-content">Skip to content</a>

    <button
      v-if="sidebarOpen"
      class="scrim"
      type="button"
      aria-label="Close navigation"
      @click="sidebarOpen = false"
    />

    <aside id="app-sidebar" class="sidebar" :class="{ 'sidebar--open': sidebarOpen }">
      <div class="brand">
        <span class="brand__mark" aria-hidden="true"><i /><i /><b /></span>
        <div class="brand__copy">
          <span class="brand__name">GhostShift<span>AI</span></span>
          <span class="brand__sub">Local automation studio</span>
        </div>
      </div>

      <nav class="nav" aria-label="Primary">
        <div v-for="section in sections" :key="section.label" class="nav__section">
          <p class="nav__label">{{ section.label }}</p>
          <RouterLink
            v-for="item in section.items"
            :key="item.to"
            :to="item.to"
            class="nav__link"
            :class="{ 'nav__link--active': isActive(item.to) }"
            :aria-current="isActive(item.to) ? 'page' : undefined"
          >
            <Icon :name="item.icon" :size="17" />
            <span>{{ item.label }}</span>
            <span v-if="item.badge && pending > 0" class="nav__badge" aria-label="pending approvals">{{ pending }}</span>
          </RouterLink>
        </div>
      </nav>

      <div class="sidebar__foot">
        <RouterLink to="/brain" class="ai-card" :class="`ai-card--${aiTone}`">
          <span class="ai-card__dot" />
          <div>
            <span class="ai-card__label">GhostBrain</span>
            <span class="ai-card__value">{{ aiLabel }}</span>
          </div>
          <Icon name="chevron" :size="14" />
        </RouterLink>
        <div class="sidebar__meta">
          <span class="mono">v{{ session.system?.version ?? '1.0.0' }}</span>
          <span>·</span>
          <span>{{ session.system?.bind ?? 'local only' }}</span>
        </div>
      </div>
    </aside>

    <div class="shell__body">
      <header class="topbar">
        <button
          class="icon-btn topbar__menu"
          type="button"
          aria-label="Open navigation"
          :aria-expanded="sidebarOpen"
          aria-controls="app-sidebar"
          @click="sidebarOpen = true"
        >
          <Icon name="menu" :size="19" />
        </button>

        <div class="topbar__title">
          <span>{{ group }}</span>
          <strong>{{ title }}</strong>
        </div>

        <div class="topbar__actions">
          <RouterLink to="/approvals" class="topbar__action topbar__approvals" aria-label="Open approvals">
            <Icon name="shield" :size="16" />
            <span>{{ pending }}</span>
          </RouterLink>
          <button class="topbar__action" type="button" aria-label="Toggle color theme" @click="toggleTheme">
            <Icon :name="session.theme === 'dark' ? 'sun' : 'moon'" :size="16" />
          </button>
          <span class="topbar__local">
            <span class="topbar__local-dot" />
            Local only
          </span>
        </div>
      </header>

      <div v-if="session.offline" class="offline-banner" role="status">
        <Icon name="alert" :size="15" />
        <span>
          <strong>Backend unavailable.</strong>
          Start the GhostShift backend, then reload. Nothing is being sent to a remote service.
        </span>
      </div>

      <main id="main-content" class="page">
        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell {
  position: relative;
  min-height: 100vh;
  display: grid;
  grid-template-columns: var(--sidebar) minmax(0, 1fr);
  overflow-x: clip;
}

.skip-link {
  position: fixed;
  top: 0.7rem;
  left: 0.7rem;
  z-index: 200;
  transform: translateY(-160%);
  padding: 0.6rem 0.9rem;
  background: var(--accent);
  color: var(--accent-ink);
  border-radius: var(--radius-sm);
  font-weight: 600;
  font-size: 0.8rem;
  transition: transform 160ms var(--ease);
}
.skip-link:focus {
  transform: translateY(0);
  color: var(--accent-ink);
}

.sidebar {
  position: sticky;
  top: 0;
  z-index: 40;
  height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 1.6rem;
  padding: 1.25rem 0.9rem 1rem;
  border-right: 1px solid var(--line);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--surface) 92%, transparent), var(--ink) 92%),
    var(--ink);
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.35rem 0.55rem 0.9rem;
  border-bottom: 1px solid var(--line);
}

.brand__mark {
  position: relative;
  display: inline-block;
  width: 2rem;
  height: 2rem;
  border: 1px solid color-mix(in srgb, var(--accent) 45%, var(--line));
  border-radius: 7px;
  background: color-mix(in srgb, var(--accent) 7%, transparent);
  overflow: hidden;
  flex: none;
}
.brand__mark i {
  position: absolute;
  width: 1px;
  height: 1.6rem;
  top: 0.2rem;
  background: var(--accent);
  transform: rotate(18deg);
  opacity: 0.75;
}
.brand__mark i:first-child {
  left: 0.68rem;
}
.brand__mark i:nth-child(2) {
  left: 1.08rem;
  opacity: 0.4;
}
.brand__mark b {
  position: absolute;
  right: 0.34rem;
  bottom: 0.36rem;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--accent-strong);
}

.brand__copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.brand__name {
  font-family: var(--font-display);
  font-size: 1.16rem;
  font-weight: 500;
  letter-spacing: -0.015em;
  line-height: 1.1;
}
.brand__name span {
  color: var(--accent);
  margin-left: 0.22rem;
}
.brand__sub {
  font-family: var(--font-mono);
  font-size: 0.58rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-faint);
  margin-top: 0.2rem;
}

.nav {
  flex: 1;
  overflow-y: auto;
  padding: 0 0.15rem;
  scrollbar-width: thin;
  scrollbar-color: var(--line-strong) transparent;
}
.nav__section + .nav__section {
  margin-top: 1.15rem;
}
.nav__label {
  margin: 0 0 0.35rem;
  padding: 0 0.6rem;
  font-family: var(--font-mono);
  font-size: 0.57rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--text-faint);
}
.nav__link {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.65rem;
  min-height: 2.25rem;
  padding: 0.4rem 0.6rem;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  font-size: 0.83rem;
  font-weight: 500;
  transition:
    background 140ms ease,
    color 140ms ease;
}
.nav__link:hover {
  background: var(--surface-2);
  color: var(--text);
}
.nav__link--active {
  background: color-mix(in srgb, var(--accent) 9%, transparent);
  color: var(--accent-strong);
}
.nav__link--active::before {
  content: '';
  position: absolute;
  left: -0.15rem;
  top: 0.55rem;
  bottom: 0.55rem;
  width: 2px;
  border-radius: 2px;
  background: var(--accent);
}
.nav__link .icon {
  color: currentColor;
  opacity: 0.8;
}
.nav__badge {
  margin-left: auto;
  display: inline-grid;
  place-items: center;
  min-width: 1.25rem;
  height: 1.25rem;
  padding: 0 0.32rem;
  border-radius: 999px;
  background: var(--accent);
  color: var(--accent-ink);
  font-family: var(--font-mono);
  font-size: 0.6rem;
  font-weight: 500;
}

.sidebar__foot {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
  padding-top: 0.8rem;
  border-top: 1px solid var(--line);
}

.ai-card {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.65rem 0.7rem;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--surface-2) 60%, transparent);
  color: var(--text-muted);
}
.ai-card:hover {
  border-color: var(--line-strong);
  color: var(--text);
}
.ai-card__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-faint);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--text-faint) 12%, transparent);
  flex: none;
}
.ai-card--ok .ai-card__dot {
  background: var(--ok);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--ok) 14%, transparent);
}
.ai-card--warn .ai-card__dot {
  background: var(--warn);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--warn) 14%, transparent);
}
.ai-card div {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}
.ai-card__label {
  font-family: var(--font-mono);
  font-size: 0.56rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--text-faint);
}
.ai-card__value {
  font-size: 0.72rem;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ai-card .icon {
  color: var(--text-faint);
  flex: none;
}
.sidebar__meta {
  display: flex;
  gap: 0.35rem;
  align-items: center;
  justify-content: center;
  color: var(--text-faint);
  font-family: var(--font-mono);
  font-size: 0.57rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.shell__body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  gap: 0.85rem;
  height: 3.75rem;
  padding: 0 1.6rem;
  border-bottom: 1px solid var(--line);
  background: color-mix(in srgb, var(--ink) 86%, transparent);
  backdrop-filter: blur(14px);
}
.topbar__menu {
  display: none;
}
.topbar__title {
  display: flex;
  align-items: baseline;
  gap: 0.55rem;
  min-width: 0;
}
.topbar__title span {
  font-family: var(--font-mono);
  font-size: 0.59rem;
  letter-spacing: 0.11em;
  text-transform: uppercase;
  color: var(--text-faint);
}
.topbar__title strong {
  font-weight: 560;
  font-size: 0.82rem;
  color: var(--text);
  letter-spacing: -0.005em;
}
.topbar__actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 0.35rem;
}
.topbar__action {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  min-width: 2rem;
  height: 2rem;
  padding: 0 0.5rem;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 0.66rem;
}
.topbar__action:hover {
  background: var(--surface-2);
  color: var(--text);
}
.topbar__approvals span {
  color: var(--accent);
}
.topbar__local {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  margin-left: 0.4rem;
  padding-left: 0.7rem;
  border-left: 1px solid var(--line);
  font-family: var(--font-mono);
  font-size: 0.58rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-faint);
  white-space: nowrap;
}
.topbar__local-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--ok);
}

.offline-banner {
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;
  padding: 0.6rem 1.6rem;
  background: color-mix(in srgb, var(--danger) 8%, var(--surface));
  border-bottom: 1px solid color-mix(in srgb, var(--danger) 25%, var(--line));
  color: var(--text-muted);
  font-size: 0.78rem;
  line-height: 1.45;
}
.offline-banner .icon {
  color: var(--danger);
  margin-top: 0.05rem;
}
.offline-banner strong {
  color: var(--text);
}

.page {
  width: 100%;
  max-width: 1420px;
  margin: 0 auto;
  padding: 2rem 2.2rem 4rem;
  flex: 1;
}

.scrim {
  position: fixed;
  inset: 0;
  z-index: 35;
  border: 0;
  background: rgb(0 0 0 / 0.5);
  backdrop-filter: blur(2px);
}

@media (max-width: 1000px) {
  .shell {
    grid-template-columns: 1fr;
  }
  .sidebar {
    position: fixed;
    inset: 0 auto 0 0;
    width: min(286px, 84vw);
    transform: translateX(-102%);
    transition: transform 220ms var(--ease);
    box-shadow: 20px 0 60px rgb(0 0 0 / 0.35);
  }
  .sidebar--open {
    transform: translateX(0);
  }
  .topbar__menu {
    display: inline-grid;
  }
  .topbar {
    padding: 0 1rem;
  }
  .page {
    padding: 1.5rem 1.1rem 3rem;
  }
}

@media (max-width: 560px) {
  .topbar__local,
  .topbar__title span {
    display: none;
  }
  .topbar__title strong {
    font-size: 0.78rem;
  }
  .page {
    padding: 1.25rem 0.9rem 3rem;
  }
}
</style>
