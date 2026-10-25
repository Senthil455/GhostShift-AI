<script setup lang="ts">
defineProps<{
  eyebrow?: string
  title: string
  description?: string
}>()
</script>

<template>
  <header class="page-head">
    <div class="page-head__copy">
      <p v-if="eyebrow" class="eyebrow">{{ eyebrow }}</p>
      <h1 class="display">{{ title }}</h1>
      <p v-if="description" class="lede">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-head__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 2rem;
  padding-bottom: 1.6rem;
  border-bottom: 1px solid var(--line);
  margin-bottom: 2rem;
}
.page-head__copy {
  max-width: 62ch;
}
.eyebrow {
  font-family: var(--font-mono);
  text-transform: uppercase;
  letter-spacing: 0.16em;
  font-size: 0.66rem;
  color: var(--accent);
  margin: 0 0 0.7rem;
}
.display {
  margin: 0;
  font-family: var(--font-display);
  font-weight: 420;
  font-size: clamp(2rem, 3.6vw, 3.15rem);
  line-height: 1.02;
  letter-spacing: -0.025em;
  color: var(--text);
}
.lede {
  margin: 0.8rem 0 0;
  color: var(--text-muted);
  font-size: 0.95rem;
  line-height: 1.6;
  max-width: 58ch;
}
.page-head__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
  padding-bottom: 0.2rem;
}
@media (max-width: 860px) {
  .page-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 1.2rem;
  }
}
</style>
