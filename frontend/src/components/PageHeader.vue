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
      <span class="page-head__tab" aria-hidden="true">{{ order }}</span>
      <div class="page-head__copy">
        <h1 class="page-head__title">{{ title }}</h1>
        <p v-if="description" class="page-head__lede">{{ description }}</p>
      </div>
      <div class="page-head__object">
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
  margin-bottom: 2.2rem;
}
.page-head::before {
  content: '';
  position: absolute;
  inset: 10px -10px -10px 10px;
  background: var(--accent);
  clip-path: polygon(0 0, calc(100% - 24px) 0, 100% 24px, 100% 100%, 0 100%);
  opacity: 0.7;
}
.page-head__plate {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 1.4rem;
  padding: 1.55rem 1.6rem 1.45rem 2.25rem;
  border: 1px solid var(--line-strong);
  background: var(--surface);
  clip-path: polygon(0 0, calc(100% - 24px) 0, 100% 24px, 100% 100%, 0 100%);
}
.page-head__tab {
  position: absolute;
  top: 0;
  left: 0;
  display: grid;
  place-items: center;
  width: 1.55rem;
  height: 1.55rem;
  background: var(--text);
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 0.6rem;
  font-weight: 500;
}
.page-head__copy {
  min-width: 0;
  max-width: 58rem;
}
.page-head__title {
  max-width: 20ch;
  margin: 0;
  color: var(--text);
  font-family: var(--font-display);
  font-size: clamp(2.55rem, 4.9vw, 5rem);
  font-weight: 600;
  letter-spacing: -0.06em;
  line-height: 0.9;
}
.page-head__lede {
  max-width: 65ch;
  margin: 0.85rem 0 0;
  color: var(--text-muted);
  font-size: 0.94rem;
  line-height: 1.55;
}
.page-head__object {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.55rem;
}
.page-head__context {
  max-width: 12rem;
  color: var(--text-faint);
  font-size: 0.72rem;
  font-weight: 600;
  line-height: 1.3;
  text-align: right;
}
.page-head__actions {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 0.6rem;
  margin-top: 0.15rem;
}
@media (max-width: 900px) {
  .page-head__plate {
    grid-template-columns: minmax(0, 1fr) auto;
    padding-left: 1.9rem;
  }
  .page-head__title {
    font-size: clamp(2.2rem, 6vw, 3.6rem);
  }
}
@media (max-width: 700px) {
  .page-head {
    margin-bottom: 1.5rem;
  }
  .page-head::before {
    inset: 7px -7px -7px 7px;
  }
  .page-head__plate {
    display: block;
    padding: 1.35rem 1.15rem 1.2rem 1.55rem;
  }
  .page-head__object {
    position: absolute;
    top: 0.9rem;
    right: 0.9rem;
  }
  .page-head__object .sigil {
    width: 4.4rem;
    height: 4.4rem;
  }
  .page-head__context {
    display: none;
  }
  .page-head__title {
    max-width: none;
    padding-right: 4.7rem;
    font-size: clamp(2rem, 8vw, 3.1rem);
  }
  .page-head__lede {
    margin-top: 0.7rem;
  }
  .page-head__actions {
    justify-content: flex-start;
    margin-top: 1rem;
  }
}
@media (max-width: 460px) {
  .page-head__object {
    display: none;
  }
  .page-head__title {
    padding-right: 0;
  }
}
</style>
