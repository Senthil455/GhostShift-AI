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
const clock = ref('')

const sections = navSections
const title = computed(() => (typeof route.meta.title === 'string' ? route.meta.title : 'GhostShift AI'))
const group = computed(() => (typeof route.meta.group === 'string' ? route.meta.group : 'Local workspace'))
const pending = computed(() => session.pendingApprovals.length)
const aiLabel = computed(() => {
  if (!session.ai) return 'Status unknown'
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

let clockTimer: number | undefined
let lampFrame = 0
let lampX = 0
let lampY = 0

/* The desk lamp follows the pointer. A small, continuous parallax on the
   ground layer only, so text never moves under the reader. */
function applyLamp() {
  lampFrame = 0
  const root = document.documentElement
  root.style.setProperty('--lamp-x', `${lampX.toFixed(1)}px`)
  root.style.setProperty('--lamp-y', `${lampY.toFixed(1)}px`)
}

function onPointerLamp(event: PointerEvent) {
  lampX = (event.clientX / window.innerWidth - 0.5) * 34
  lampY = (event.clientY / window.innerHeight - 0.5) * 22
  if (!lampFrame) lampFrame = requestAnimationFrame(applyLamp)
}

function tick() {
  clock.value = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit' }).format(new Date())
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

let lampEnabled = false
onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  tick()
  clockTimer = window.setInterval(tick, 30_000)
  lampEnabled =
    window.matchMedia('(pointer: fine)').matches &&
    !window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (lampEnabled) window.addEventListener('pointermove', onPointerLamp, { passive: true })
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  window.removeEventListener('pointermove', onPointerLamp)
  if (lampFrame) cancelAnimationFrame(lampFrame)
  document.body.classList.remove('nav-open')
  if (clockTimer) window.clearInterval(clockTimer)
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
          <span class="brand__name">GhostShift</span>
          <span class="brand__sub">local automation</span>
        </span>
      </RouterLink>

      <nav class="nav" aria-label="Primary">
        <div v-for="section in sections" :key="section.label" class="nav__section">
          <p class="nav__label">
            <span>{{ section.label }}</span>
            <i aria-hidden="true" />
          </p>
          <RouterLink
            v-for="item in section.items"
            :key="item.to"
            :to="item.to"
            class="nav__link"
            :class="{ 'nav__link--active': isActive(item.to) }"
            :aria-current="isActive(item.to) ? 'page' : undefined"
          >
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
        <RouterLink to="/brain" class="machine" :class="`machine--${aiTone}`">
          <span class="machine__head">
            <span class="machine__dot" />
            <span class="machine__name">GhostBrain</span>
            <Icon name="chevron" :size="13" />
          </span>
          <span class="machine__value">{{ aiLabel }}</span>
        </RouterLink>
        <div class="sidebar__meta">
          <span>v{{ session.system?.version ?? '1.0.0' }}</span>
          <span class="sidebar__meta-rule" aria-hidden="true" />
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
          <span class="topbar__clock" aria-hidden="true">{{ clock }}</span>
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
  padding: 0.6rem 0.9rem;
  border-radius: var(--radius);
  background: var(--signal);
  color: var(--signal-ink);
  font-size: 0.84rem;
  font-weight: 600;
  transition: transform 160ms var(--ease);
}
.skip-link:focus {
  transform: translateY(0);
  color: var(--signal-ink);
}

/* ---- the index rail ---- */

.sidebar {
  position: sticky;
  top: 0;
  z-index: 40;
  height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  padding: 1.05rem 0.85rem 0.9rem;
  border-right: 1px solid var(--line);
  background:
    linear-gradient(180deg, rgb(255 255 255 / 0.022), transparent 32%),
    var(--surface);
  box-shadow:
    inset -1px 0 0 rgb(255 255 255 / 0.025),
    18px 0 40px -34px rgb(0 0 0 / 0.9);
}

.sidebar__close {
  display: none;
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.2rem 0.35rem 0.35rem;
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
  font-size: 1.42rem;
  font-weight: 500;
  letter-spacing: -0.02em;
  line-height: 0.95;
}
.brand__sub {
  margin-top: 0.28rem;
  color: var(--text-faint);
  font-size: 0.7rem;
  letter-spacing: 0.02em;
}

.nav {
  flex: 1;
  overflow-y: auto;
  padding: 0.15rem 0.05rem 0.4rem;
  scrollbar-width: thin;
  scrollbar-color: var(--line-strong) transparent;
}
.nav__section + .nav__section {
  margin-top: 1.3rem;
}
.nav__label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0 0 0.4rem;
  padding: 0 0.5rem;
  color: var(--text-faint);
  font-size: 0.72rem;
  font-weight: 500;
}
.nav__label i {
  flex: 1;
  height: 1px;
  background: color-mix(in srgb, var(--line-strong) 60%, transparent);
}
.nav__link {
  position: relative;
  display: grid;
  grid-template-columns: 1.1rem minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.55rem;
  min-height: 2.4rem;
  margin-bottom: 0.12rem;
  padding: 0.4rem 0.55rem 0.4rem 0.6rem;
  border: 1px solid transparent;
  border-radius: var(--radius);
  color: var(--text-muted);
  font-size: 0.9rem;
  font-weight: 500;
  line-height: 1.15;
  transition:
    background 150ms ease,
    color 150ms ease,
    border-color 150ms ease,
    transform 150ms var(--ease);
}
.nav__link:hover {
  border-color: var(--line);
  background: var(--surface-2);
  color: var(--text);
  transform: translateX(2px);
}
.nav__link--active,
.nav__link--active:hover {
  border-color: color-mix(in srgb, var(--paper) 20%, var(--line));
  background: var(--paper);
  color: var(--paper-ink);
  transform: translateX(3px);
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 0.6),
    -3px 0 0 var(--signal),
    0 10px 18px -14px rgb(0 0 0 / 0.9);
}
[data-theme='light'] .nav__link--active,
[data-theme='light'] .nav__link--active:hover {
  border-color: var(--text);
  background: var(--text);
  color: var(--paper);
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 0.12),
    -3px 0 0 var(--signal),
    0 10px 18px -14px rgb(0 0 0 / 0.55);
}
.nav__icon {
  display: grid;
  place-items: center;
  color: currentColor;
  opacity: 0.72;
}
.nav__link--active .nav__icon {
  opacity: 0.95;
}
.nav__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.nav__badge {
  display: grid;
  place-items: center;
  min-width: 1.25rem;
  height: 1.25rem;
  padding: 0 0.3rem;
  border-radius: 1px;
  background: var(--signal);
  color: var(--signal-ink);
  font-size: 0.68rem;
  font-weight: 650;
}

.sidebar__foot {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--line);
}
.machine {
  display: block;
  padding: 0.6rem 0.65rem;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: color-mix(in srgb, var(--ink) 45%, transparent);
  color: var(--text-muted);
  box-shadow: var(--shadow-inset);
  transition:
    border-color 150ms ease,
    background 150ms ease,
    color 150ms ease;
}
.machine:hover {
  border-color: var(--line-strong);
  background: var(--surface-2);
  color: var(--text);
}
.machine__head {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--text-faint);
  font-size: 0.72rem;
}
.machine__head .icon {
  margin-left: auto;
}
.machine__dot {
  width: 5px;
  height: 5px;
  border-radius: 1px;
  background: var(--text-faint);
}
.machine--ok .machine__dot {
  background: var(--ok);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--ok) 18%, transparent);
}
.machine--warn .machine__dot {
  background: var(--warn);
}
.machine__value {
  display: block;
  margin-top: 0.18rem;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 0.8rem;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sidebar__meta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  color: var(--text-faint);
  font-size: 0.68rem;
  font-variant-numeric: tabular-nums;
}
.sidebar__meta-rule {
  width: 1rem;
  height: 1px;
  background: var(--line-strong);
}

/* ---- the shift strip ---- */

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
  height: 3.7rem;
  padding: 0 clamp(1rem, 2.4vw, 2.2rem);
  border-bottom: 1px solid var(--line);
  background: color-mix(in srgb, var(--desk) 92%, var(--surface));
  box-shadow: 0 14px 24px -26px rgb(0 0 0 / 0.9);
}
/* a machined measure along the base of the strip, not a page grid */
.topbar::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -3px;
  height: 3px;
  pointer-events: none;
  opacity: 0.5;
  background-image: repeating-linear-gradient(
    90deg,
    color-mix(in srgb, var(--line-strong) 80%, transparent) 0 1px,
    transparent 1px 9px
  );
  mask-image: linear-gradient(90deg, transparent, #000 15%, #000 70%, transparent);
}
.topbar__menu {
  display: none;
}
.topbar__title {
  display: flex;
  align-items: baseline;
  gap: 0.6rem;
  min-width: 0;
}
.topbar__title span {
  color: var(--text-faint);
  font-size: 0.76rem;
}
.topbar__title strong {
  overflow: hidden;
  color: var(--text);
  font-family: var(--font-display);
  font-size: 1.16rem;
  font-weight: 500;
  letter-spacing: -0.015em;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.topbar__actions {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  margin-left: auto;
}
.topbar__clock {
  margin-right: 0.5rem;
  color: var(--text-faint);
  font-size: 0.76rem;
  font-variant-numeric: tabular-nums;
}
.topbar__action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.32rem;
  min-width: 2.2rem;
  height: 2.2rem;
  padding: 0 0.5rem;
  border: 1px solid transparent;
  border-radius: var(--radius);
  background: transparent;
  color: var(--text-muted);
  font-family: var(--font-sans);
  font-size: 0.78rem;
  font-weight: 550;
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
  color: var(--signal-strong);
}
.topbar__local {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  margin-left: 0.4rem;
  padding-left: 0.85rem;
  border-left: 1px solid var(--line);
  color: var(--text-faint);
  font-size: 0.72rem;
  white-space: nowrap;
}
.topbar__local-dot {
  width: 5px;
  height: 5px;
  border-radius: 1px;
  background: var(--ok);
}

.offline-banner {
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;
  padding: 0.7rem clamp(1rem, 2.4vw, 2.2rem);
  border-bottom: 1px solid color-mix(in srgb, var(--danger) 35%, var(--line));
  border-left: 2px solid var(--danger);
  background: color-mix(in srgb, var(--danger) 6%, var(--surface));
  color: var(--text-muted);
  font-size: 0.84rem;
  line-height: 1.45;
}
.offline-banner .icon {
  margin-top: 0.1rem;
  color: var(--danger);
}
.offline-banner strong {
  color: var(--text);
}

.page {
  width: 100%;
  max-width: 1520px;
  margin: 0 auto;
  padding: 2.1rem clamp(1.2rem, 2.8vw, 3rem) 5rem;
  flex: 1;
  perspective: 1600px;
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
    transition: transform 260ms var(--ease);
    box-shadow: 24px 0 50px -20px rgb(0 0 0 / 0.7);
  }
  .sidebar--open {
    transform: translateX(0);
  }
  .sidebar__close {
    position: absolute;
    top: 0.8rem;
    right: 0.8rem;
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
  .topbar__clock,
  .topbar__title span {
    display: none;
  }
  .topbar__title strong {
    font-size: 1.02rem;
  }
  .page {
    padding: 1.4rem 0.95rem 3.5rem;
  }
}
</style>

<style>
body.nav-open {
  overflow: hidden;
}
</style>
