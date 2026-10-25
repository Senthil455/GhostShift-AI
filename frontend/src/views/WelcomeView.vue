<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api } from '@/api/client'
import { errorMessage } from '@/lib/errors'
import { useSessionStore } from '@/stores/session'
import { useToastStore } from '@/stores/toast'
import Icon from '@/components/Icon.vue'

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
      <div class="brand">
        <span class="brand__mark" aria-hidden="true"><i /><i /><b /></span>
        <div>
          <span class="brand__name">GhostShift<span>AI</span></span>
          <span class="brand__sub">Local automation studio</span>
        </div>
      </div>
      <RouterLink v-if="replay" to="/overview" class="btn btn--ghost btn--sm">
        <Icon name="arrow" :size="14" class="back-icon" />
        Back to workspace
      </RouterLink>
      <span v-else class="welcome__local"><span /> Local only · v{{ session.system?.version ?? '1.0.0' }}</span>
    </header>

    <main class="welcome__main">
      <section class="welcome__hero">
        <p class="eyebrow">A quieter kind of automation</p>
        <h1>Your work doesn't need the cloud.</h1>
        <p class="welcome__lede">
          GhostShift is a local-first shift desk for files, logs, and repeatable workflows. The model suggests.
          Deterministic code checks. You decide. The engine executes. Every step leaves a record.
        </p>

        <div class="welcome__principles">
          <div v-for="principle in principles" :key="principle.title" class="principle">
            <span class="principle__icon"><Icon :name="principle.icon" :size="17" /></span>
            <div>
              <strong>{{ principle.title }}</strong>
              <p>{{ principle.copy }}</p>
            </div>
          </div>
        </div>

        <div class="welcome__start">
          <div class="welcome__start-copy">
            <p class="eyebrow">Begin</p>
            <h2>{{ replay ? 'Return to the shift desk.' : 'Start with a look around.' }}</h2>
            <p>
              Create a synthetic demo workspace with example files and three reviewable workflows, or enter the empty
              workspace and register your own folder.
            </p>
            <div class="welcome__ai" :class="`welcome__ai--${aiState.tone}`">
              <span class="welcome__ai-dot" />
              <span>GhostBrain · {{ aiState.label }}</span>
              <RouterLink to="/brain" class="welcome__ai-link">Configure</RouterLink>
            </div>
          </div>
          <div class="welcome__actions">
            <button class="btn btn--primary btn--block" type="button" :disabled="busy !== ''" @click="enter">
              <span v-if="busy === 'enter'" class="spinner" />
              <Icon v-else name="arrow" :size="15" />
              {{ replay ? 'Return to overview' : 'Enter the workspace' }}
            </button>
            <button class="btn btn--block" type="button" :disabled="busy !== ''" @click="createDemo">
              <span v-if="busy === 'demo'" class="spinner" />
              <Icon v-else name="spark" :size="15" />
              Create demo workspace
            </button>
            <p class="welcome__fine">
              The demo lives inside GhostShift's own data folder. Every file is synthetic, and nothing is overwritten.
            </p>
          </div>
        </div>

        <p v-if="error" class="welcome__error" role="alert">{{ error }}</p>
      </section>

      <aside class="welcome__steps" aria-label="First steps">
        <div class="steps-head">
          <p class="eyebrow">The first shift</p>
          <span class="mono">Three moves, no surprises</span>
        </div>
        <RouterLink v-for="step in steps" :key="step.number" :to="step.to" class="step">
          <span class="step__number">{{ step.number }}</span>
          <div>
            <h2>{{ step.title }}</h2>
            <p>{{ step.copy }}</p>
            <span class="step__link">{{ step.link }} <Icon name="arrow" :size="13" /></span>
          </div>
        </RouterLink>

        <div class="steps-note">
          <Icon name="terminal" :size="16" />
          <p>
            GhostShift never runs arbitrary shell commands. Workflow actions come from a closed registry with explicit
            permissions.
          </p>
        </div>
      </aside>
    </main>

    <footer class="welcome__foot">
      <span>GhostShift AI · an independent local-first project</span>
      <span class="mono">No deletes · no silent overwrites · no hidden uploads</span>
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
  padding: 0 clamp(1.2rem, 4vw, 3.5rem);
}
.welcome__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.5rem 0 1.2rem;
  border-bottom: 1px solid var(--line);
}
.brand {
  display: flex;
  align-items: center;
  gap: 0.7rem;
}
.brand__mark {
  position: relative;
  display: inline-block;
  width: 2.1rem;
  height: 2.1rem;
  border: 1px solid color-mix(in srgb, var(--accent) 45%, var(--line));
  border-radius: 8px;
  background: color-mix(in srgb, var(--accent) 7%, transparent);
  overflow: hidden;
  flex: none;
}
.brand__mark i {
  position: absolute;
  width: 1px;
  height: 1.7rem;
  top: 0.2rem;
  background: var(--accent);
  transform: rotate(18deg);
  opacity: 0.75;
}
.brand__mark i:first-child {
  left: 0.72rem;
}
.brand__mark i:nth-child(2) {
  left: 1.14rem;
  opacity: 0.4;
}
.brand__mark b {
  position: absolute;
  right: 0.36rem;
  bottom: 0.38rem;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--accent-strong);
}
.brand__name {
  display: block;
  font-family: var(--font-display);
  font-size: 1.22rem;
  font-weight: 500;
  letter-spacing: -0.015em;
  line-height: 1.05;
}
.brand__name span {
  color: var(--accent);
  margin-left: 0.2rem;
}
.brand__sub {
  display: block;
  font-family: var(--font-mono);
  font-size: 0.56rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-faint);
  margin-top: 0.18rem;
}
.welcome__local {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-family: var(--font-mono);
  font-size: 0.6rem;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--text-faint);
}
.welcome__local span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ok);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--ok) 12%, transparent);
}
.back-icon {
  transform: rotate(180deg);
}
.welcome__main {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(320px, 1fr);
  gap: clamp(2rem, 5vw, 5rem);
  align-items: start;
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
  padding: clamp(2.5rem, 6vh, 5rem) 0 3rem;
}
.welcome__hero h1 {
  margin: 0.7rem 0 0;
  font-family: var(--font-display);
  font-size: clamp(2.6rem, 5.4vw, 5.1rem);
  font-weight: 390;
  line-height: 0.98;
  letter-spacing: -0.04em;
  max-width: 13ch;
  text-wrap: balance;
}
.welcome__lede {
  margin: 1.5rem 0 0;
  max-width: 56ch;
  color: var(--text-muted);
  font-size: 0.98rem;
  line-height: 1.7;
}
.welcome__principles {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
  margin-top: 2.2rem;
  padding-top: 1.4rem;
  border-top: 1px solid var(--line);
}
.principle {
  display: flex;
  gap: 0.55rem;
  align-items: flex-start;
}
.principle__icon {
  display: grid;
  place-items: center;
  width: 1.9rem;
  height: 1.9rem;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--accent);
  flex: none;
}
.principle strong {
  display: block;
  font-size: 0.78rem;
  font-weight: 600;
}
.principle p {
  margin: 0.25rem 0 0;
  color: var(--text-muted);
  font-size: 0.72rem;
  line-height: 1.5;
}
.welcome__start {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(220px, 0.8fr);
  gap: 1.6rem;
  margin-top: 2.6rem;
  padding: 1.5rem;
  border: 1px solid color-mix(in srgb, var(--accent) 22%, var(--line));
  border-radius: var(--radius-lg);
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--accent) 7%, transparent), transparent 55%),
    color-mix(in srgb, var(--surface) 92%, transparent);
}
.welcome__start-copy h2 {
  margin: 0.4rem 0 0;
  font-family: var(--font-display);
  font-size: clamp(1.45rem, 2.5vw, 2rem);
  font-weight: 440;
  letter-spacing: -0.025em;
}
.welcome__start-copy > p:not(.eyebrow) {
  margin: 0.6rem 0 0;
  color: var(--text-muted);
  font-size: 0.8rem;
  line-height: 1.6;
  max-width: 48ch;
}
.welcome__ai {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  margin-top: 0.9rem;
  padding: 0.3rem 0.55rem;
  border: 1px solid var(--line);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 0.6rem;
  color: var(--text-faint);
}
.welcome__ai-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--text-faint);
}
.welcome__ai--ok {
  color: var(--ok);
  border-color: color-mix(in srgb, var(--ok) 28%, var(--line));
}
.welcome__ai--ok .welcome__ai-dot {
  background: var(--ok);
}
.welcome__ai--warn {
  color: var(--warn);
  border-color: color-mix(in srgb, var(--warn) 28%, var(--line));
}
.welcome__ai--warn .welcome__ai-dot {
  background: var(--warn);
}
.welcome__ai-link {
  margin-left: 0.2rem;
  color: var(--accent);
  font-size: 0.6rem;
}
.welcome__actions {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0.55rem;
}
.welcome__fine {
  margin: 0.2rem 0 0;
  color: var(--text-faint);
  font-size: 0.65rem;
  line-height: 1.5;
}
.welcome__error {
  margin: 1rem 0 0;
  padding: 0.65rem 0.8rem;
  border-left: 2px solid var(--danger);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--danger) 7%, transparent);
  color: var(--danger);
  font-size: 0.78rem;
}
.welcome__steps {
  position: relative;
  padding-left: 1.6rem;
  border-left: 1px solid var(--line);
}
.steps-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.8rem;
  margin-bottom: 0.7rem;
}
.steps-head .mono {
  color: var(--text-faint);
  font-size: 0.58rem;
}
.step {
  display: flex;
  gap: 0.85rem;
  padding: 1.05rem 0.4rem;
  border-bottom: 1px solid var(--line);
  color: var(--text);
}
.step:hover {
  color: var(--text);
}
.step__number {
  font-family: var(--font-mono);
  font-size: 0.62rem;
  color: var(--accent);
  padding-top: 0.15rem;
}
.step h2 {
  margin: 0;
  font-size: 0.88rem;
  font-weight: 600;
}
.step p {
  margin: 0.3rem 0 0;
  color: var(--text-muted);
  font-size: 0.74rem;
  line-height: 1.55;
}
.step__link {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  margin-top: 0.45rem;
  font-family: var(--font-mono);
  font-size: 0.62rem;
  color: var(--accent);
}
.step:hover .step__link {
  color: var(--accent-strong);
}
.steps-note {
  display: flex;
  gap: 0.55rem;
  margin-top: 1.1rem;
  padding: 0.8rem;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--surface-2) 45%, transparent);
  color: var(--text-faint);
}
.steps-note .icon {
  color: var(--text-muted);
  margin-top: 0.05rem;
}
.steps-note p {
  margin: 0;
  font-size: 0.69rem;
  line-height: 1.5;
}
.welcome__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
  padding: 1rem 0 1.4rem;
  border-top: 1px solid var(--line);
  color: var(--text-faint);
  font-size: 0.64rem;
}
.welcome__foot .mono {
  font-size: 0.58rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

@media (max-width: 1050px) {
  .welcome__main {
    grid-template-columns: 1fr;
    gap: 2.4rem;
    padding-top: 2.5rem;
  }
  .welcome__hero h1 {
    max-width: 17ch;
  }
  .welcome__steps {
    padding-left: 0;
    border-left: 0;
    border-top: 1px solid var(--line);
  }
}
@media (max-width: 720px) {
  .welcome__principles {
    grid-template-columns: 1fr;
  }
  .welcome__start {
    grid-template-columns: 1fr;
  }
  .welcome__actions {
    padding-top: 0.3rem;
  }
  .welcome__foot {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
