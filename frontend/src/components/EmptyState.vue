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
  padding: 2rem 1.5rem;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius);
  background: color-mix(in srgb, var(--surface) 60%, transparent);
}
.empty--compact {
  padding: 1.2rem;
}
.empty__icon {
  display: grid;
  place-items: center;
  width: 2.4rem;
  height: 2.4rem;
  border-radius: 50%;
  border: 1px solid var(--line);
  color: var(--accent);
}
.empty h3 {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 600;
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
