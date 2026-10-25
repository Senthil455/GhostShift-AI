<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { navSections } from '@/lib/modules'
import { useSessionStore } from '@/stores/session'
import Icon from './Icon.vue'
import ShiftMark from './ShiftMark.vue'

const route = useRoute()
const session = useSessionStore()
const sidebarOpen = ref(false)
const menuButton = ref<HTMLButtonElement | null>(null)
const closeButton = ref<HTMLButtonElement | null>(null)

const sections = navSections
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

function closeNav() {
  sidebarOpen.value = false
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && sidebarOpen.value) closeNav()
}

watch(
  () => route.fullPath,
  () => {
    sidebarOpen.value = false
  },
)

watch(sidebarOpen, async (open) => {
  document.body.classList.toggle('nav-open', open)
  if (open) {
    await nextTick()
    closeButton.value?.focus()
  } else {
    menuButton.value?.focus()
  }
})

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.classList.remove('nav-open')
})
</script>

<template>
  <div class="shell">
    <a class="skip-link" href="#main-content">Skip to content</a>

    <button
      v-if="sidebarOpen"
      class="scrim"
      type="button"
      aria-label="Close navigation"
      @click="closeNav"
    />

    <aside id="app-sidebar" class="sidebar" :class="{ 'sidebar--open': sidebarOpen }">
      <button
        ref="closeButton"
        class="icon-btn sidebar__close"
        type="button"
        aria-label="Close navigation"
        @click="closeNav"
      >
        <Icon name="x" :size="18" />
      </button>

      <RouterLink to="/overview" class="brand" aria-label="GhostShift AI overview">
        <ShiftMark />
        <span class="brand__copy">
          <span class="brand__name">GhostShift<span>AI</span></span>
          <span class="brand__sub">Local automation studio</span>
        </span>
      </RouterLink>

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
            <span class="nav__no" aria-hidden="true">{{ item.order }}</span>
            <span class="nav__icon"><Icon :name="item.icon" :size="16" /></span>
            <span class="nav__name">{{ item.label }}</span>
            <span
              v-if="item.to === '/approvals' && pending > 0"
              class="nav__badge"
              aria-hidden="true"
            >{{ pending }}</span>
          </RouterLink>
        </div>
      </nav>

      <div class="sidebar__foot">
        <RouterLink to="/brain" class="ai-card" :class="`ai-card--${aiTone}`">
          <span class="ai-card__dot" />
          <span class="ai-card__copy">
            <span class="ai-card__label">GhostBrain</span>
            <span class="ai-card__value">{{ aiLabel }}</span>
          </span>
          <Icon name="chevron" :size="14" />
        </RouterLink>
        <div class="sidebar__meta">
          <span>v{{ session.system?.version ?? '1.0.0' }}</span>
          <span aria-hidden="true">·</span>
          <span>{{ session.system?.bind ?? 'local only' }}</span>
        </div>
      </div>
    </aside>

    <div class="shell__body">
      <header class="topbar">
        <button
          ref="menuButton"
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
  z-index: 1;
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
  padding: 0.65rem 0.9rem;
  background: var(--signal);
  color: var(--signal-ink);
  font-size: 0.82rem;
  font-weight: 700;
  transition: transform 160ms var(--ease);
}
.skip-link:focus {
  transform: translateY(0);
  color: var(--signal-ink);
}

.sidebar {
  position: sticky;
  top: 0;
  z-index: 40;
  height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
  padding: 1.15rem 0.8rem 1rem;
  border-right: 1px solid var(--line);
  background: var(--surface);
}

.sidebar__close {
  display: none;
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.35rem 0.45rem 1.2rem;
  color: var(--text);
}
.brand:hover {
  color: var(--text);
}
.brand__copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.brand__name {
  font-family: var(--font-display);
  font-size: 1.26rem;
  font-weight: 600;
  letter-spacing: -0.045em;
  line-height: 1;
}
.brand__name span {
  margin-left: 0.18rem;
  color: var(--signal);
}
.brand__sub {
  margin-top: 0.25rem;
  color: var(--text-faint);
  font-size: 0.68rem;
  line-height: 1.2;
}

.nav {
  flex: 1;
  overflow-y: auto;
  padding: 0 0.05rem;
  scrollbar-width: thin;
  scrollbar-color: var(--line-strong) transparent;
}
.nav__section + .nav__section {
  margin-top: 1.35rem;
}
.nav__label {
  margin: 0 0 0.35rem;
  padding: 0 0.55rem;
  color: var(--text-faint);
  font-size: 0.7rem;
  font-weight: 650;
  letter-spacing: 0.01em;
}
.nav__link {
  position: relative;
  display: grid;
  grid-template-columns: 1.35rem 1.15rem minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.42rem;
  min-height: 2.45rem;
  padding: 0.38rem 0.55rem;
  border: 1px solid transparent;
  color: var(--text-muted);
  font-size: 0.88rem;
  font-weight: 600;
  line-height: 1.15;
  transition:
    background 150ms ease,
    color 150ms ease,
    border-color 150ms ease,
    transform 150ms var(--ease),
    box-shadow 150ms var(--ease);
}
.nav__link:hover {
  border-color: var(--line);
  background: var(--surface-2);
  color: var(--text);
  transform: translate(-1px, -1px);
}
.nav__link--active,
.nav__link--active:hover {
  border-color: var(--text);
  background: var(--text);
  color: var(--ink);
  transform: translate(-1px, -1px);
  box-shadow: 4px 4px 0 var(--accent);
}
.nav__no {
  color: var(--text-faint);
  font-family: var(--font-mono);
  font-size: 0.58rem;
  font-weight: 400;
  font-variant-numeric: tabular-nums;
}
.nav__link--active .nav__no {
  color: color-mix(in srgb, var(--ink) 55%, transparent);
}
.nav__icon {
  display: grid;
  place-items: center;
  color: currentColor;
  opacity: 0.8;
}
.nav__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.nav__badge {
  display: grid;
  place-items: center;
  min-width: 1.3rem;
  height: 1.3rem;
  padding: 0 0.28rem;
  background: var(--signal);
  color: var(--signal-ink);
  font-family: var(--font-sans);
  font-size: 0.66rem;
  font-weight: 750;
}

.sidebar__foot {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding-top: 0.9rem;
}
.ai-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.6rem;
  padding: 0.7rem 0.68rem;
  border: 1px solid var(--line);
  background: var(--surface-2);
  color: var(--text-muted);
  box-shadow: 3px 3px 0 var(--shadow-color);
  transition:
    transform 150ms var(--ease),
    box-shadow 150ms var(--ease),
    border-color 150ms ease;
}
.ai-card:hover {
  border-color: var(--accent);
  color: var(--text);
  transform: translate(-1px, -1px);
  box-shadow: 4px 4px 0 color-mix(in srgb, var(--accent) 35%, transparent);
}
.ai-card__dot {
  width: 0.42rem;
  height: 0.42rem;
  background: var(--text-faint);
}
.ai-card--ok .ai-card__dot {
  background: var(--ok);
}
.ai-card--warn .ai-card__dot {
  background: var(--warn);
}
.ai-card__copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.ai-card__label {
  color: var(--text-faint);
  font-size: 0.68rem;
  font-weight: 650;
}
.ai-card__value {
  overflow: hidden;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 0.68rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ai-card .icon {
  color: var(--text-faint);
}
.sidebar__meta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.38rem;
  color: var(--text-faint);
  font-family: var(--font-mono);
  font-size: 0.6rem;
  font-variant-numeric: tabular-nums;
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
  height: 3.9rem;
  padding: 0 clamp(1rem, 2.4vw, 2.1rem);
  border-bottom: 1px solid var(--line);
  background: var(--surface);
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
  color: var(--text-faint);
  font-size: 0.72rem;
  font-weight: 600;
}
.topbar__title strong {
  overflow: hidden;
  color: var(--text);
  font-family: var(--font-display);
  font-size: 1.02rem;
  font-weight: 600;
  letter-spacing: -0.03em;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.topbar__actions {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin-left: auto;
}
.topbar__action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  min-width: 2.2rem;
  height: 2.2rem;
  padding: 0 0.5rem;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-muted);
  font-family: var(--font-sans);
  font-size: 0.76rem;
  font-weight: 650;
  transition:
    background 140ms ease,
    border-color 140ms ease,
    color 140ms ease;
}
.topbar__action:hover {
  border-color: var(--line);
  background: var(--surface-2);
  color: var(--text);
}
.topbar__approvals span {
  color: var(--signal);
}
.topbar__local {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  margin-left: 0.35rem;
  padding-left: 0.8rem;
  border-left: 1px solid var(--line);
  color: var(--text-faint);
  font-size: 0.7rem;
  white-space: nowrap;
}
.topbar__local-dot {
  width: 0.38rem;
  height: 0.38rem;
  background: var(--ok);
}

.offline-banner {
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;
  padding: 0.7rem clamp(1rem, 2.4vw, 2.1rem);
  border-bottom: 1px solid color-mix(in srgb, var(--danger) 35%, var(--line));
  border-left: 3px solid var(--danger);
  background: color-mix(in srgb, var(--danger) 7%, var(--surface));
  color: var(--text-muted);
  font-size: 0.82rem;
  line-height: 1.45;
}
.offline-banner .icon {
  margin-top: 0.05rem;
  color: var(--danger);
}
.offline-banner strong {
  color: var(--text);
}

.page {
  width: 100%;
  max-width: 1560px;
  margin: 0 auto;
  padding: 2.2rem clamp(1.2rem, 2.8vw, 3.2rem) 5rem;
  flex: 1;
}

.scrim {
  position: fixed;
  inset: 0;
  z-index: 35;
  border: 0;
  background: rgb(0 0 0 / 0.62);
}

@media (max-width: 1000px) {
  .shell {
    grid-template-columns: 1fr;
  }
  .sidebar {
    position: fixed;
    inset: 0 auto 0 0;
    width: min(310px, 86vw);
    transform: translateX(-102%);
    transition: transform 240ms var(--ease);
    box-shadow: 18px 0 0 rgb(0 0 0 / 0.28);
  }
  .sidebar--open {
    transform: translateX(0);
  }
  .sidebar__close {
    position: absolute;
    top: 0.85rem;
    right: 0.85rem;
    z-index: 1;
    display: inline-grid;
  }
  .brand {
    padding-right: 2.6rem;
  }
  .topbar__menu {
    display: inline-grid;
  }
}

@media (max-width: 560px) {
  .topbar__local,
  .topbar__title span {
    display: none;
  }
  .topbar__title strong {
    font-size: 0.94rem;
  }
  .page {
    padding: 1.35rem 0.95rem 3.5rem;
  }
}
</style>

<style>
body.nav-open {
  overflow: hidden;
}
</style>
