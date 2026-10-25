<script setup lang="ts">
import Icon from './Icon.vue'

withDefaults(defineProps<{ icon?: string; title: string; description?: string; compact?: boolean }>(), {
  icon: 'spark',
  compact: false,
})
</script>

<template>
  <div class="empty" :class="{ 'empty--compact': compact }">
    <span class="empty__icon"><Icon :name="icon" :size="compact ? 18 : 24" /></span>
    <div>
      <h3>{{ title }}</h3>
      <p v-if="description">{{ description }}</p>
    </div>
    <div v-if="$slots.default" class="empty__action">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.empty {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.9rem 1rem;
  align-items: start;
  padding: 1.5rem 1.4rem;
  border: 1px solid var(--line);
  border-left: 2px solid var(--accent);
  border-radius: var(--radius);
  background: var(--surface-2);
  box-shadow: var(--shadow-plate);
}
.empty--compact {
  padding: 1.1rem;
}
.empty__icon {
  display: grid;
  place-items: center;
  width: 2.3rem;
  height: 2.3rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--line-strong);
  color: var(--signal-strong);
}
.empty h3 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 1.14rem;
  font-weight: 500;
  letter-spacing: -0.01em;
}
.empty p {
  margin: 0.35rem 0 0;
  color: var(--text-muted);
  font-size: 0.82rem;
  line-height: 1.5;
  max-width: 55ch;
}
.empty__action {
  grid-column: 2;
  margin-top: 0.4rem;
}
</style>
