<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { moduleForPath } from '@/lib/modules'
import ModuleSigil from './ModuleSigil.vue'

const props = defineProps<{
  eyebrow?: string
  title: string
  description?: string
}>()

const route = useRoute()
const module = computed(() => moduleForPath(route.path))
const icon = computed(() => module.value?.icon ?? 'grid')
const order = computed(() => module.value?.order ?? '00')
const tone = computed(() => module.value?.tone ?? 'cobalt')
const context = computed(() => props.eyebrow || module.value?.group || 'Workspace')
</script>

<template>
  <header class="page-head">
    <div class="page-head__plate">
      <span class="page-head__spine" aria-hidden="true" />
      <div class="page-head__copy">
        <h1 class="page-head__title">{{ title }}</h1>
        <p v-if="description" class="page-head__lede">{{ description }}</p>
      </div>
      <div class="page-head__side">
        <ModuleSigil :icon="icon" :order="order" :tone="tone" />
        <span class="page-head__context">{{ context }}</span>
      </div>
      <div v-if="$slots.actions" class="page-head__actions">
        <slot name="actions" />
      </div>
    </div>
  </header>
</template>

<style scoped>
.page-head {
  position: relative;
  margin-bottom: 1.9rem;
}
.page-head__plate {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: 1.2rem 2rem;
  padding: 1.5rem 1.6rem 1.35rem 1.75rem;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background:
    linear-gradient(180deg, rgb(255 255 255 / 0.025), transparent 55%),
    var(--surface);
  box-shadow: var(--shadow-plate);
  overflow: hidden;
}
/* the ticket stub: a perforated spine down the left edge */
.page-head__spine {
  position: absolute;
  inset: 0 auto 0 0;
  width: 0.8rem;
  border-right: 1px dashed color-mix(in srgb, var(--line-strong) 55%, transparent);
  background-image: radial-gradient(
    circle at 50% 50%,
    color-mix(in srgb, var(--ink) 90%, transparent) 0 2.5px,
    transparent 3px
  );
  background-size: 100% 16px;
  background-position: 50% 4px;
  opacity: 0.85;
}
.page-head__copy {
  min-width: 0;
  padding-left: 0.55rem;
}
.page-head__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.9rem, 3.2vw, 2.9rem);
  font-weight: 500;
  letter-spacing: -0.02em;
  line-height: 1;
  text-wrap: balance;
}
.page-head__lede {
  max-width: 62ch;
  margin: 0.7rem 0 0;
  color: var(--text-muted);
  font-size: 0.95rem;
  line-height: 1.58;
}
.page-head__side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.5rem;
}
.page-head__context {
  color: var(--text-faint);
  font-size: 0.72rem;
}
.page-head__actions {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  padding-top: 0.95rem;
  border-top: 1px solid color-mix(in srgb, var(--line) 75%, transparent);
}

@media (max-width: 760px) {
  .page-head__plate {
    grid-template-columns: minmax(0, 1fr);
    padding: 1.2rem 1.1rem 1.1rem 1.35rem;
  }
  .page-head__side {
    flex-direction: row;
    align-items: center;
    gap: 0.75rem;
  }
  .page-head__title {
    font-size: 1.85rem;
  }
  .page-head__actions {
    padding-top: 0.8rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .page-head__plate {
    transition: none;
  }
}
</style>
