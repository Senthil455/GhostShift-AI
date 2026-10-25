<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api } from '@/api/client'
import { errorMessage } from '@/lib/errors'
import { useSessionStore } from '@/stores/session'
import { useToastStore } from '@/stores/toast'
import GhostLedger from '@/components/GhostLedger.vue'
import Icon from '@/components/Icon.vue'
import ShiftMark from '@/components/ShiftMark.vue'

const session = useSessionStore()
const toast = useToastStore()
const router = useRouter()
const route = useRoute()

const busy = ref<'enter' | 'demo' | ''>('')
const error = ref('')

const replay = computed(() => route.query.replay === '1')
const aiState = computed(() => {
  if (!session.ai) return { label: 'Status unknown', tone: 'neutral' }
  if (!session.ai.reachable) return { label: 'Deterministic mode', tone: 'neutral' }
  if (!session.ai.modelAvailable) return { label: 'Model not installed', tone: 'warn' }
  return { label: `${session.ai.model} ready`, tone: 'ok' }
})

const principles = [
  {
    icon: 'lock',
    title: 'Files stay here',
    copy: 'SQLite, reports, and model calls stay on this machine. No account, telemetry, or cloud sync.',
  },
  {
    icon: 'shield',
    title: 'Approval before action',
    copy: 'Suggestions are proposals. Deterministic code validates them, and you authorize every move.',
  },
  {
    icon: 'undo',
    title: 'Reversible where possible',
    copy: 'GhostShift records recovery data for moves, never deletes files, and never silently overwrites.',
  },
]

const steps = [
  {
    number: '01',
    title: 'Register a folder',
    copy: 'You choose exactly what GhostShift may read. Symlinks that escape the folder are skipped.',
    to: '/settings',
    link: 'Open folders',
  },
  {
    number: '02',
    title: 'Review every destination',
    copy: 'GhostSort classifies files into a closed category set and shows the full plan before anything moves.',
    to: '/sort',
    link: 'See GhostSort',
  },
  {
    number: '03',
    title: 'Keep the receipts',
    copy: 'Runs, approvals, reports, and file operations are recorded locally so you can inspect the whole shift.',
    to: '/history',
    link: 'Open history',
  },
]

async function complete() {
  try {
    const saved = await api.updateSettings({ onboardingCompleted: true })
    session.settings = saved
  } catch {
    // The workspace remains usable even if this preference cannot be persisted.
  }
}

async function enter() {
  busy.value = 'enter'
  error.value = ''
  await complete()
  busy.value = ''
  await router.push('/overview')
}

async function createDemo() {
  busy.value = 'demo'
  error.value = ''
  try {
    const result = await api.seedDemo()
    await Promise.all([
      session.refreshDirectories().catch(() => undefined),
      session.refreshDashboard().catch(() => undefined),
    ])
    await complete()
    toast.success('Demo workspace ready', result.message)
    await router.push('/sort')
  } catch (cause) {
    error.value = errorMessage(cause, 'The demo workspace could not be created.')
  } finally {
    busy.value = ''
  }
}

onMounted(() => {
  if (session.settings && !session.ai) {
    void session.refreshAi(false).catch(() => undefined)
  }
})
</script>

<template>
  <div class="welcome">
    <header class="welcome__top">
      <RouterLink to="/overview" class="brand" aria-label="GhostShift AI overview">
        <ShiftMark />
        <span class="brand__copy">
          <span class="brand__name">GhostShift<span>AI</span></span>
          <span class="brand__sub">Local automation studio</span>
        </span>
      </RouterLink>

      <div class="welcome__top-right">
        <span class="welcome__local"><i /> Local only · v{{ session.system?.version ?? '1.0.0' }}</span>
        <RouterLink v-if="replay" to="/overview" class="btn btn--ghost btn--sm">
          <Icon name="arrow" :size="14" class="back-icon" />
          Back to workspace
        </RouterLink>
      </div>
    </header>

    <main class="welcome__main">
      <section class="welcome__hero">
        <div class="welcome__copy">
          <h1 class="welcome__title">Your work doesn't need the cloud.</h1>
          <p class="welcome__lede">
            GhostShift is a local-first shift desk for the files, logs, and repetitive workflows that pile up after
            hours. The model suggests. Deterministic code checks. You decide. Every move leaves a receipt.
          </p>

          <div class="welcome__actions">
            <button class="btn btn--primary" type="button" :disabled="busy !== ''" @click="enter">
              <span v-if="busy === 'enter'" class="spinner" />
              <Icon v-else name="arrow" :size="15" />
              {{ replay ? 'Return to overview' : 'Enter the workspace' }}
            </button>
            <button class="btn" type="button" :disabled="busy !== ''" @click="createDemo">
              <span v-if="busy === 'demo'" class="spinner" />
              <Icon v-else name="spark" :size="15" />
              Create demo workspace
            </button>
          </div>

          <div class="welcome__status">
            <span class="welcome__ai" :class="`welcome__ai--${aiState.tone}`">
              <i />
              GhostBrain · {{ aiState.label }}
            </span>
            <RouterLink to="/brain" class="welcome__ai-link">Configure model</RouterLink>
          </div>

          <p v-if="error" class="welcome__error" role="alert">{{ error }}</p>

          <div class="welcome__tape" aria-label="Local guarantees">
            <span>SQLite on disk</span>
            <span>Ollama optional</span>
            <span>No account</span>
            <span>No hidden uploads</span>
          </div>
        </div>

        <div class="welcome__stage">
          <GhostLedger mode="hero" :interactive="false" />
          <div class="welcome__stage-caption">
            <span>Shift 00 / The archive</span>
            <span>Six modules · one local machine</span>
          </div>
        </div>
      </section>

      <section class="welcome__principles" aria-label="Operating principles">
        <div v-for="(principle, index) in principles" :key="principle.title" class="principle">
          <span class="principle__no">{{ String(index + 1).padStart(2, '0') }}</span>
          <span class="principle__icon"><Icon :name="principle.icon" :size="18" /></span>
          <div>
            <strong>{{ principle.title }}</strong>
            <p>{{ principle.copy }}</p>
          </div>
        </div>
      </section>

      <section class="welcome__shift" aria-label="First steps">
        <div class="welcome__shift-head">
          <p class="eyebrow">The first shift</p>
          <h2>Three moves, no surprises.</h2>
          <p>
            GhostShift starts with what you register, shows every destination, and keeps the full record. Nothing is
            inferred from silence.
          </p>
        </div>

        <div class="welcome__steps">
          <RouterLink v-for="step in steps" :key="step.number" :to="step.to" class="step">
            <span class="step__number">{{ step.number }}</span>
            <div>
              <h3>{{ step.title }}</h3>
              <p>{{ step.copy }}</p>
              <span class="step__link">{{ step.link }} <Icon name="arrow" :size="13" /></span>
            </div>
          </RouterLink>
        </div>

        <div class="welcome__note">
          <Icon name="terminal" :size="18" />
          <p>
            GhostShift never runs arbitrary shell commands. Workflow actions come from a closed registry with explicit
            permissions.
          </p>
        </div>
      </section>
    </main>

    <footer class="welcome__foot">
      <span>GhostShift AI · an independent local-first project</span>
      <span>No deletes · no silent overwrites · no hidden uploads</span>
    </footer>
  </div>
</template>

<style scoped>
.welcome {
  position: relative;
  z-index: 1;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 0 clamp(1.1rem, 3.8vw, 3.4rem);
}
.welcome__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.35rem 0 1.1rem;
  border-bottom: 1px solid var(--line);
}
.brand {
  display: flex;
  align-items: center;
  gap: 0.7rem;
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
  font-size: 1.3rem;
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
}
.welcome__top-right {
  display: flex;
  align-items: center;
  gap: 0.8rem;
}
.welcome__local {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  color: var(--text-muted);
  font-size: 0.72rem;
  white-space: nowrap;
}
.welcome__local i {
  width: 0.38rem;
  height: 0.38rem;
  background: var(--ok);
}
.back-icon {
  transform: rotate(180deg);
}

.welcome__main {
  width: 100%;
  max-width: 1540px;
  margin: 0 auto;
  padding: clamp(2rem, 4.8vw, 5.4rem) 0 0;
}
.welcome__hero {
  display: grid;
  grid-template-columns: minmax(0, 1.02fr) minmax(0, 0.98fr);
  align-items: center;
  gap: clamp(1.5rem, 4vw, 4rem);
}
.welcome__copy {
  max-width: 48rem;
  animation: welcome-rise 640ms var(--ease) both;
}
.welcome__title {
  max-width: 11.5ch;
  margin: 0;
  color: var(--text);
  font-family: var(--font-display);
  font-size: clamp(3.2rem, 6.7vw, 7.4rem);
  font-weight: 600;
  letter-spacing: -0.07em;
  line-height: 0.86;
}
.welcome__lede {
  max-width: 54ch;
  margin: 1.3rem 0 0;
  color: var(--text-muted);
  font-size: clamp(0.98rem, 1.2vw, 1.08rem);
  line-height: 1.58;
}
.welcome__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem;
  margin-top: 1.6rem;
}
.welcome__status {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.8rem;
  margin-top: 1rem;
}
.welcome__ai {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  color: var(--text-muted);
  font-size: 0.76rem;
}
.welcome__ai i {
  width: 0.4rem;
  height: 0.4rem;
  background: var(--text-faint);
}
.welcome__ai--ok i {
  background: var(--ok);
}
.welcome__ai--warn i {
  background: var(--warn);
}
.welcome__ai-link {
  color: var(--accent);
  font-size: 0.76rem;
  font-weight: 650;
  text-decoration: underline;
  text-underline-offset: 0.2em;
}
.welcome__error {
  margin: 1rem 0 0;
  padding: 0.7rem 0.85rem;
  border-left: 3px solid var(--danger);
  background: color-mix(in srgb, var(--danger) 8%, var(--surface));
  color: var(--danger);
  font-size: 0.82rem;
}
.welcome__tape {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin-top: 2rem;
}
.welcome__tape span {
  padding: 0.3rem 0.48rem;
  border: 1px solid var(--line);
  color: var(--text-faint);
  font-size: 0.68rem;
  font-weight: 600;
}
.welcome__stage {
  min-width: 0;
  animation: welcome-rise 760ms 90ms var(--ease) both;
}
.welcome__stage-caption {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  margin-top: 0.65rem;
  color: var(--text-faint);
  font-size: 0.7rem;
}

.welcome__principles {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0;
  margin-top: clamp(2.2rem, 5vw, 4.8rem);
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}
.principle {
  display: grid;
  grid-template-columns: auto auto minmax(0, 1fr);
  gap: 0.7rem;
  padding: 1.35rem 1.3rem 1.45rem 0;
}
.principle + .principle {
  padding-left: 1.3rem;
  border-left: 1px solid var(--line);
}
.principle__no {
  color: var(--text-faint);
  font-family: var(--font-mono);
  font-size: 0.62rem;
  padding-top: 0.22rem;
}
.principle__icon {
  display: grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  border: 1px solid var(--line-strong);
  color: var(--signal);
}
.principle strong {
  display: block;
  color: var(--text);
  font-family: var(--font-display);
  font-size: 1.06rem;
  font-weight: 600;
  letter-spacing: -0.025em;
}
.principle p {
  max-width: 38ch;
  margin: 0.35rem 0 0;
  color: var(--text-muted);
  font-size: 0.78rem;
  line-height: 1.5;
}

.welcome__shift {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.6fr);
  gap: clamp(1.4rem, 3.5vw, 3.5rem);
  margin-top: clamp(2.4rem, 5vw, 5rem);
  align-items: start;
}
.welcome__shift-head h2 {
  max-width: 12ch;
  margin: 0.55rem 0 0;
  color: var(--text);
  font-family: var(--font-display);
  font-size: clamp(2rem, 3.3vw, 3.5rem);
  font-weight: 600;
  letter-spacing: -0.055em;
  line-height: 0.93;
}
.welcome__shift-head > p:last-child {
  max-width: 44ch;
  margin: 0.85rem 0 0;
  color: var(--text-muted);
  font-size: 0.86rem;
  line-height: 1.55;
}
.welcome__steps {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
}
.step {
  display: block;
  padding: 0.95rem 0 0;
  border-top: 3px solid var(--line-strong);
  color: var(--text);
  transition:
    border-color 160ms ease,
    transform 180ms var(--ease);
}
.step:hover {
  border-color: var(--signal);
  color: var(--text);
  transform: translateY(-3px);
}
.step__number {
  display: block;
  color: var(--accent);
  font-family: var(--font-display);
  font-size: 2.3rem;
  font-weight: 600;
  letter-spacing: -0.06em;
  line-height: 0.9;
}
.step h3 {
  margin: 0.8rem 0 0;
  color: var(--text);
  font-family: var(--font-display);
  font-size: 1.12rem;
  font-weight: 600;
  letter-spacing: -0.03em;
}
.step p {
  margin: 0.4rem 0 0;
  color: var(--text-muted);
  font-size: 0.78rem;
  line-height: 1.5;
}
.step__link {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  margin-top: 0.85rem;
  color: var(--accent);
  font-size: 0.74rem;
  font-weight: 650;
}
.welcome__note {
  display: flex;
  align-items: flex-start;
  gap: 0.65rem;
  grid-column: 2;
  margin-top: 1rem;
  padding: 0.9rem 1rem;
  border: 1px solid var(--line);
  background: var(--surface-2);
  box-shadow: 4px 4px 0 var(--shadow-color);
  color: var(--text-muted);
}
.welcome__note .icon {
  margin-top: 0.08rem;
  color: var(--signal);
}
.welcome__note p {
  max-width: 68ch;
  margin: 0;
  font-size: 0.78rem;
  line-height: 1.5;
}

.welcome__foot {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  width: 100%;
  max-width: 1540px;
  margin: clamp(2.5rem, 6vw, 5.5rem) auto 0;
  padding: 1rem 0 1.4rem;
  border-top: 1px solid var(--line);
  color: var(--text-faint);
  font-size: 0.7rem;
}

@keyframes welcome-rise {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (max-width: 1120px) {
  .welcome__hero {
    grid-template-columns: 1fr;
  }
  .welcome__copy {
    max-width: 62rem;
  }
  .welcome__title {
    max-width: 12ch;
    font-size: clamp(3.2rem, 8vw, 6rem);
  }
  .welcome__stage {
    max-width: 52rem;
  }
}
@media (max-width: 900px) {
  .welcome__principles {
    grid-template-columns: 1fr;
  }
  .principle {
    grid-template-columns: auto auto minmax(0, 1fr);
    padding: 1.1rem 0;
  }
  .principle + .principle {
    padding-left: 0;
    border-top: 1px solid var(--line);
    border-left: 0;
  }
  .welcome__shift {
    grid-template-columns: 1fr;
  }
  .welcome__steps {
    grid-template-columns: 1fr;
  }
  .welcome__note {
    grid-column: 1;
  }
}
@media (max-width: 620px) {
  .welcome__top-right .welcome__local {
    display: none;
  }
  .welcome__title {
    max-width: 10ch;
    font-size: clamp(2.85rem, 13vw, 4.5rem);
  }
  .welcome__actions .btn {
    width: 100%;
  }
  .welcome__stage-caption {
    flex-direction: column;
    gap: 0.25rem;
  }
  .welcome__foot {
    flex-direction: column;
    gap: 0.35rem;
  }
}
@media (prefers-reduced-motion: reduce) {
  .welcome__copy,
  .welcome__stage {
    animation: none;
  }
}
</style>
