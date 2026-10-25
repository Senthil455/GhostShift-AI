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
    title: 'Register a folder',
    copy: 'You choose exactly what GhostShift may read. Symlinks that escape the folder are skipped.',
    to: '/settings',
    link: 'Open folders',
  },
  {
    title: 'Review every destination',
    copy: 'GhostSort classifies files into a closed category set and shows the full plan before anything moves.',
    to: '/sort',
    link: 'See GhostSort',
  },
  {
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
          <span class="brand__name">GhostShift</span>
          <span class="brand__sub">local automation studio</span>
        </span>
      </RouterLink>

      <div class="welcome__top-right">
        <span class="welcome__local"><i /> Local only</span>
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

          <ul class="welcome__tape" aria-label="Local guarantees">
            <li>SQLite on disk</li>
            <li>Ollama optional</li>
            <li>No account</li>
            <li>No hidden uploads</li>
          </ul>
        </div>

        <div class="welcome__stage">
          <GhostLedger mode="hero" :interactive="false" />
          <p class="welcome__caption">
            The card file: six modules, one machine. Every shift is written on a card and kept on your desk.
          </p>
        </div>
      </section>

      <section class="desknote" aria-label="Operating principles">
        <div class="desknote__sheet">
          <header class="desknote__head">
            <h2>What the desk promises</h2>
            <span class="desknote__stamp" aria-hidden="true">Local<br />only</span>
          </header>

          <div class="desknote__grid">
            <article class="rule rule--lead">
              <span class="rule__icon"><Icon :name="principles[0].icon" :size="20" /></span>
              <h3>{{ principles[0].title }}</h3>
              <p>{{ principles[0].copy }}</p>
            </article>
            <article v-for="principle in principles.slice(1)" :key="principle.title" class="rule">
              <span class="rule__icon"><Icon :name="principle.icon" :size="18" /></span>
              <h3>{{ principle.title }}</h3>
              <p>{{ principle.copy }}</p>
            </article>
          </div>

          <p class="desknote__foot">
            Three rules, written once. Nothing leaves this machine to be checked against them.
          </p>
        </div>
      </section>

      <section class="shift" aria-label="First steps">
        <div class="shift__head">
          <h2>Three moves, no surprises.</h2>
          <p>
            GhostShift starts with what you register, shows every destination, and keeps the full record. Nothing is
            inferred from silence.
          </p>
        </div>

        <ol class="shift__steps">
          <li v-for="step in steps" :key="step.title">
            <RouterLink :to="step.to" class="ticket">
              <span class="ticket__punch" aria-hidden="true" />
              <h3>{{ step.title }}</h3>
              <p>{{ step.copy }}</p>
              <span class="ticket__link">{{ step.link }} <Icon name="arrow" :size="13" /></span>
            </RouterLink>
          </li>
        </ol>

        <div class="shift__note">
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
  padding: 0 clamp(1.1rem, 3.6vw, 3.2rem);
}
.welcome__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.2rem 0 1rem;
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
  font-size: 1.4rem;
  font-weight: 500;
  letter-spacing: -0.02em;
  line-height: 1;
}
.brand__sub {
  margin-top: 0.25rem;
  color: var(--text-faint);
  font-size: 0.7rem;
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
  font-size: 0.74rem;
  white-space: nowrap;
}
.welcome__local i {
  width: 5px;
  height: 5px;
  border-radius: 1px;
  background: var(--ok);
}
.back-icon {
  transform: rotate(180deg);
}

.welcome__main {
  width: 100%;
  max-width: 1520px;
  margin: 0 auto;
  padding: clamp(1.8rem, 4.4vw, 4.6rem) 0 0;
}

.welcome__hero {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
  align-items: center;
  gap: clamp(1.4rem, 3.4vw, 3.4rem);
}
.welcome__copy {
  max-width: 38rem;
  animation: welcome-rise 620ms var(--ease) both;
}
.welcome__title {
  margin: 0;
  color: var(--text);
  font-family: var(--font-display);
  font-size: clamp(2.7rem, 5vw, 4.8rem);
  font-weight: 500;
  letter-spacing: -0.028em;
  line-height: 0.94;
  text-wrap: balance;
}
.welcome__lede {
  max-width: 46ch;
  margin: 1.15rem 0 0;
  color: var(--text-muted);
  font-size: clamp(0.98rem, 1.1vw, 1.06rem);
  line-height: 1.6;
}
.welcome__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin-top: 1.5rem;
}
.welcome__status {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.8rem;
  margin-top: 0.95rem;
}
.welcome__ai {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  color: var(--text-muted);
  font-size: 0.78rem;
}
.welcome__ai i {
  width: 5px;
  height: 5px;
  border-radius: 1px;
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
  font-size: 0.78rem;
  text-decoration: underline;
  text-underline-offset: 0.2em;
}
.welcome__error {
  margin: 1rem 0 0;
  padding: 0.7rem 0.85rem;
  border-left: 2px solid var(--danger);
  background: color-mix(in srgb, var(--danger) 8%, var(--surface));
  color: var(--danger);
  font-size: 0.84rem;
}
.welcome__tape {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
  margin: 1.9rem 0 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--line);
}
.welcome__tape li {
  position: relative;
  padding: 0.55rem 0.9rem 0 0;
  margin-right: 0.9rem;
  color: var(--text-faint);
  font-size: 0.74rem;
}
.welcome__tape li + li::before {
  content: '';
  position: absolute;
  left: -0.55rem;
  top: 0.95rem;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--line-strong);
}
.welcome__stage {
  min-width: 0;
  animation: welcome-rise 740ms 80ms var(--ease) both;
}
.welcome__caption {
  max-width: 46ch;
  margin: 0.7rem 0 0;
  color: var(--text-faint);
  font-size: 0.76rem;
  line-height: 1.5;
}

/* ---- the promise, printed on one sheet ---- */

.desknote {
  margin-top: clamp(2.4rem, 5.2vw, 5rem);
  perspective: 1400px;
}
.desknote__sheet {
  position: relative;
  padding: clamp(1.3rem, 2.6vw, 2.2rem);
  border-radius: 2px;
  background:
    linear-gradient(168deg, rgb(255 255 255 / 0.55), transparent 38%),
    var(--paper);
  color: var(--paper-ink);
  box-shadow:
    0 1px 0 rgb(255 255 255 / 0.5) inset,
    0 30px 50px -34px rgb(0 0 0 / 0.9),
    0 2px 6px rgb(0 0 0 / 0.4);
  transform: rotate(-0.35deg);
}
.desknote__sheet::before {
  content: '';
  position: absolute;
  inset: 0.6rem auto 0.6rem 0.55rem;
  width: 1px;
  background-image: radial-gradient(
    circle,
    color-mix(in srgb, var(--paper-ink) 34%, transparent) 0 1.5px,
    transparent 2px
  );
  background-size: 100% 14px;
}
.desknote__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.2rem 0 1rem 1.2rem;
  border-bottom: 1px solid color-mix(in srgb, var(--paper-ink) 20%, transparent);
}
.desknote__head h2 {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.6rem, 2.6vw, 2.3rem);
  font-weight: 500;
  letter-spacing: -0.02em;
  color: var(--paper-ink);
}
.desknote__stamp {
  flex: none;
  display: grid;
  place-items: center;
  width: 4.6rem;
  height: 4.6rem;
  border: 2px solid color-mix(in srgb, #b23a18 78%, transparent);
  border-radius: 50%;
  color: #a4340f;
  font-size: 0.62rem;
  font-weight: 650;
  line-height: 1.15;
  text-align: center;
  text-transform: uppercase;
  transform: rotate(-11deg);
  opacity: 0.85;
  box-shadow: inset 0 0 0 2px rgb(178 58 24 / 0.14);
}
.desknote__grid {
  display: grid;
  grid-template-columns: 1.35fr 1fr 1fr;
  gap: 0;
  padding-left: 1.2rem;
}
.rule {
  padding: 1.1rem 1.1rem 0.4rem 0;
}
.rule + .rule {
  padding-left: 1.1rem;
  border-left: 1px solid color-mix(in srgb, var(--paper-ink) 16%, transparent);
}
.rule__icon {
  display: grid;
  place-items: center;
  width: 1.9rem;
  height: 1.9rem;
  border: 1px solid color-mix(in srgb, var(--paper-ink) 26%, transparent);
  color: #a4340f;
}
.rule h3 {
  margin: 0.7rem 0 0;
  color: var(--paper-ink);
  font-family: var(--font-display);
  font-size: 1.2rem;
  font-weight: 500;
  letter-spacing: -0.01em;
}
.rule--lead h3 {
  font-size: clamp(1.6rem, 2.5vw, 2.1rem);
  line-height: 1.02;
}
.rule p {
  max-width: 40ch;
  margin: 0.4rem 0 0;
  color: color-mix(in srgb, var(--paper-ink) 74%, transparent);
  font-size: 0.82rem;
  line-height: 1.55;
}
.desknote__foot {
  margin: 1.2rem 0 0 1.2rem;
  padding-top: 0.8rem;
  border-top: 1px solid color-mix(in srgb, var(--paper-ink) 16%, transparent);
  color: color-mix(in srgb, var(--paper-ink) 62%, transparent);
  font-size: 0.76rem;
}

/* ---- the tickets ---- */

.shift {
  display: grid;
  grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.6fr);
  gap: clamp(1.4rem, 3.2vw, 3.2rem);
  margin-top: clamp(2.4rem, 5vw, 4.6rem);
  align-items: start;
}
.shift__head h2 {
  max-width: 14ch;
  margin: 0;
  color: var(--text);
  font-family: var(--font-display);
  font-size: clamp(1.9rem, 3vw, 2.9rem);
  font-weight: 500;
  letter-spacing: -0.02em;
  line-height: 0.98;
}
.shift__head p {
  max-width: 44ch;
  margin: 0.8rem 0 0;
  color: var(--text-muted);
  font-size: 0.88rem;
  line-height: 1.6;
}
.shift__steps {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
  margin: 0;
  padding: 0.2rem 0.45rem 0.5rem 0;
  list-style: none;
  perspective: 1000px;
}
.shift__steps li:nth-child(1) {
  transform: rotate(-1.1deg);
}
.shift__steps li:nth-child(2) {
  transform: translateY(0.5rem) rotate(0.5deg);
}
.shift__steps li:nth-child(3) {
  transform: rotate(-0.5deg);
}
.ticket {
  position: relative;
  display: block;
  height: 100%;
  padding: 1.15rem 1.05rem 1.05rem 1.4rem;
  border: 1px solid var(--line);
  border-radius: 2px;
  background:
    linear-gradient(160deg, rgb(255 255 255 / 0.035), transparent 50%),
    var(--surface);
  color: var(--text);
  box-shadow: var(--shadow-plate);
  transition:
    transform 220ms var(--ease),
    border-color 180ms ease,
    box-shadow 220ms var(--ease);
}
.ticket::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 0.55rem;
  border-right: 1px dashed color-mix(in srgb, var(--line-strong) 60%, transparent);
  background-image: radial-gradient(
    circle,
    color-mix(in srgb, var(--ink) 90%, transparent) 0 2px,
    transparent 2.5px
  );
  background-size: 100% 13px;
  background-position: 50% 4px;
}
.ticket:hover {
  transform: translateY(-4px) rotate(0deg);
  border-color: color-mix(in srgb, var(--accent) 45%, var(--line));
  color: var(--text);
  box-shadow: var(--shadow-lift);
}
.ticket__punch {
  position: absolute;
  top: 0.9rem;
  right: 0.9rem;
  width: 0.4rem;
  height: 0.4rem;
  border-radius: 50%;
  background: var(--ink);
  box-shadow: inset 0 1px 1px rgb(0 0 0 / 0.7);
}
.ticket h3 {
  margin: 0;
  padding-right: 1.2rem;
  color: var(--text);
  font-family: var(--font-display);
  font-size: 1.28rem;
  font-weight: 500;
  letter-spacing: -0.015em;
  line-height: 1.1;
}
.ticket p {
  margin: 0.55rem 0 0;
  color: var(--text-muted);
  font-size: 0.82rem;
  line-height: 1.55;
}
.ticket__link {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  margin-top: 1.1rem;
  color: var(--accent);
  font-size: 0.78rem;
}
.shift__note {
  display: flex;
  align-items: flex-start;
  gap: 0.65rem;
  grid-column: 2;
  margin-top: 0.4rem;
  padding: 0.85rem 1rem;
  border: 1px solid var(--line);
  border-left: 2px solid var(--signal);
  border-radius: var(--radius);
  background: var(--surface-2);
  color: var(--text-muted);
}
.shift__note .icon {
  margin-top: 0.1rem;
  color: var(--signal-strong);
}
.shift__note p {
  max-width: 68ch;
  margin: 0;
  font-size: 0.82rem;
  line-height: 1.55;
}

.welcome__foot {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  width: 100%;
  max-width: 1520px;
  margin: clamp(2.4rem, 5.5vw, 5rem) auto 0;
  padding: 1rem 0 1.3rem;
  border-top: 1px solid var(--line);
  color: var(--text-faint);
  font-size: 0.72rem;
}

@keyframes welcome-rise {
  from {
    opacity: 0;
    transform: translate3d(0, 18px, -40px);
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
    max-width: 42rem;
  }
  .welcome__stage {
    max-width: 50rem;
  }
}
@media (max-width: 900px) {
  .desknote__grid {
    grid-template-columns: 1fr;
  }
  .rule {
    padding: 1rem 0;
  }
  .rule + .rule {
    padding-left: 0;
    border-left: 0;
    border-top: 1px solid color-mix(in srgb, var(--paper-ink) 16%, transparent);
  }
  .shift {
    grid-template-columns: 1fr;
  }
  .shift__steps {
    grid-template-columns: 1fr;
  }
  .shift__steps li,
  .shift__steps li:nth-child(2),
  .shift__steps li:nth-child(3) {
    transform: none;
  }
  .shift__note {
    grid-column: 1;
  }
}
@media (max-width: 620px) {
  .welcome__top-right .welcome__local {
    display: none;
  }
  .welcome__title {
    font-size: clamp(2.4rem, 10vw, 3.3rem);
  }
  .welcome__actions .btn {
    width: 100%;
  }
  .desknote__stamp {
    width: 3.6rem;
    height: 3.6rem;
    font-size: 0.55rem;
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
