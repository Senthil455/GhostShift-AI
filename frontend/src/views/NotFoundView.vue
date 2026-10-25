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
    <div class="slip">
      <span class="slip__stamp" aria-hidden="true">404</span>
      <h1>This corridor goes nowhere.</h1>
      <p class="slip__copy">
        The page may have moved, or the link may be incomplete. Nothing was changed, and no run was started.
      </p>
      <div class="slip__actions">
        <RouterLink to="/overview" class="btn btn--primary">
          <Icon name="home" :size="15" />
          Return to overview
        </RouterLink>
        <RouterLink to="/settings" class="slip__ghost">
          <Icon name="settings" :size="15" />
          Open settings
        </RouterLink>
      </div>

      <div class="slip__grid">
        <RouterLink
          v-for="destination in destinations"
          :key="destination.to"
          :to="destination.to"
          class="destination"
        >
          <span class="destination__icon"><Icon :name="destination.icon" :size="17" /></span>
          <div>
            <strong>{{ destination.label }}</strong>
            <p>{{ destination.copy }}</p>
          </div>
          <Icon name="arrow" :size="15" class="destination__arrow" />
        </RouterLink>
      </div>

      <p class="slip__foot">GhostShift AI · local workspace</p>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  display: grid;
  place-items: center;
  padding-top: clamp(0.5rem, 4vh, 2.5rem);
  perspective: 1400px;
}
.slip {
  position: relative;
  width: min(820px, 100%);
  padding: 2rem 2.2rem 1.6rem 2.8rem;
  border-radius: 2px;
  background:
    linear-gradient(166deg, rgb(255 255 255 / 0.5), transparent 38%),
    var(--paper);
  color: var(--paper-ink);
  box-shadow:
    0 1px 0 rgb(255 255 255 / 0.5) inset,
    0 34px 54px -38px rgb(0 0 0 / 0.95),
    0 2px 6px rgb(0 0 0 / 0.4);
  transform: rotate(-0.4deg);
}
.slip::before {
  content: '';
  position: absolute;
  inset: 0.8rem auto 0.8rem 0.7rem;
  width: 1px;
  background-image: radial-gradient(
    circle,
    color-mix(in srgb, var(--paper-ink) 32%, transparent) 0 2px,
    transparent 2.6px
  );
  background-size: 100% 15px;
}
.slip__stamp {
  position: absolute;
  top: 1.5rem;
  right: 1.8rem;
  display: grid;
  place-items: center;
  width: 4.4rem;
  height: 4.4rem;
  border: 2px solid color-mix(in srgb, #b23a18 75%, transparent);
  border-radius: 50%;
  color: #a4340f;
  font-family: var(--font-display);
  font-size: 1.4rem;
  transform: rotate(9deg);
  opacity: 0.85;
}
.slip h1 {
  max-width: 20ch;
  margin: 0;
  color: var(--paper-ink);
  font-family: var(--font-display);
  font-size: clamp(2.1rem, 4.2vw, 3.5rem);
  font-weight: 500;
  letter-spacing: -0.025em;
  line-height: 0.96;
}
.slip__copy {
  max-width: 54ch;
  margin: 1rem 0 0;
  color: color-mix(in srgb, var(--paper-ink) 70%, transparent);
  font-size: 0.94rem;
  line-height: 1.6;
}
.slip__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1rem;
  margin-top: 1.4rem;
}
.slip__ghost {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--paper-ink);
  font-size: 0.86rem;
  text-decoration: underline;
  text-underline-offset: 0.22em;
  text-decoration-color: color-mix(in srgb, var(--paper-ink) 35%, transparent);
}
.slip__ghost:hover {
  color: #a4340f;
}
.slip__grid {
  display: flex;
  flex-direction: column;
  margin-top: 1.9rem;
  border-top: 1px solid color-mix(in srgb, var(--paper-ink) 20%, transparent);
}
.destination {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  padding: 0.75rem 0.2rem;
  border-bottom: 1px solid color-mix(in srgb, var(--paper-ink) 13%, transparent);
  color: var(--paper-ink);
}
.destination:hover {
  color: var(--paper-ink);
}
.destination__icon {
  display: grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  border: 1px solid color-mix(in srgb, var(--paper-ink) 28%, transparent);
  border-radius: 1px;
  color: #a4340f;
  flex: none;
}
.destination div {
  flex: 1;
}
.destination strong {
  font-family: var(--font-display);
  font-size: 1.04rem;
  font-weight: 500;
}
.destination p {
  margin: 0.12rem 0 0;
  color: color-mix(in srgb, var(--paper-ink) 62%, transparent);
  font-size: 0.78rem;
}
.destination__arrow {
  color: color-mix(in srgb, var(--paper-ink) 45%, transparent);
  transition: transform 160ms var(--ease);
}
.destination:hover .destination__arrow {
  transform: translateX(3px);
  color: #a4340f;
}
.slip__foot {
  margin: 1.5rem 0 0;
  color: color-mix(in srgb, var(--paper-ink) 50%, transparent);
  font-size: 0.74rem;
}

@media (max-width: 620px) {
  .slip {
    padding: 1.5rem 1.3rem 1.3rem 1.9rem;
    transform: none;
  }
  .slip__stamp {
    position: static;
    width: 3.4rem;
    height: 3.4rem;
    margin-bottom: 1rem;
    font-size: 1.1rem;
  }
  .slip h1 {
    font-size: 2rem;
  }
}
</style>
