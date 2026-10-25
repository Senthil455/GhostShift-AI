<script setup lang="ts">
import { RouterLink } from 'vue-router'
import Icon from '@/components/Icon.vue'

const destinations = [
  { to: '/overview', label: 'Overview', icon: 'grid', copy: 'The current shift at a glance.' },
  { to: '/replay', label: 'GhostReplay', icon: 'play', copy: 'Review and run a workflow.' },
  { to: '/history', label: 'History', icon: 'clock', copy: 'Runs, activity, and reports.' },
]
</script>

<template>
  <div class="not-found">
    <p class="eyebrow">404 · Outside the map</p>
    <h1>This corridor goes nowhere.</h1>
    <p class="not-found__copy">
      The page may have moved, or the link may be incomplete. Nothing was changed, and no run was started.
    </p>
    <div class="not-found__actions">
      <RouterLink to="/overview" class="btn btn--primary">
        <Icon name="home" :size="15" />
        Return to overview
      </RouterLink>
      <RouterLink to="/settings" class="btn btn--ghost">
        <Icon name="settings" :size="15" />
        Open settings
      </RouterLink>
    </div>

    <div class="not-found__grid">
      <RouterLink v-for="destination in destinations" :key="destination.to" :to="destination.to" class="destination">
        <span class="destination__icon"><Icon :name="destination.icon" :size="18" /></span>
        <div>
          <strong>{{ destination.label }}</strong>
          <p>{{ destination.copy }}</p>
        </div>
        <Icon name="arrow" :size="15" class="destination__arrow" />
      </RouterLink>
    </div>

    <p class="not-found__foot mono">GhostShift AI · local workspace</p>
  </div>
</template>

<style scoped>
.not-found {
  max-width: 760px;
  margin: clamp(1rem, 6vh, 4rem) auto 0;
}
.not-found h1 {
  margin: 0.75rem 0 0;
  font-family: var(--font-display);
  font-size: clamp(2.4rem, 5vw, 4rem);
  font-weight: 400;
  letter-spacing: -0.035em;
  line-height: 1;
}
.not-found__copy {
  margin: 1rem 0 0;
  max-width: 52ch;
  color: var(--text-muted);
  font-size: 0.92rem;
  line-height: 1.65;
}
.not-found__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin-top: 1.5rem;
}
.not-found__grid {
  display: flex;
  flex-direction: column;
  margin-top: 2.2rem;
  border-top: 1px solid var(--line);
}
.destination {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  padding: 0.9rem 0.3rem;
  border-bottom: 1px solid var(--line);
  color: var(--text);
}
.destination:hover {
  color: var(--text);
}
.destination__icon {
  display: grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--accent);
  flex: none;
}
.destination div {
  flex: 1;
}
.destination strong {
  font-size: 0.83rem;
  font-weight: 600;
}
.destination p {
  margin: 0.15rem 0 0;
  color: var(--text-muted);
  font-size: 0.73rem;
}
.destination__arrow {
  color: var(--text-faint);
  transition: transform 160ms var(--ease);
}
.destination:hover .destination__arrow {
  transform: translateX(2px);
  color: var(--accent);
}
.not-found__foot {
  margin: 2rem 0 0;
  color: var(--text-faint);
  font-size: 0.6rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
</style>
