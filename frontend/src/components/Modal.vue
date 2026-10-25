<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import Icon from './Icon.vue'

const props = defineProps<{
  open: boolean
  title: string
  subtitle?: string
  wide?: boolean
}>()

const emit = defineEmits<{ close: [] }>()

function onKey(event: KeyboardEvent) {
  if (event.key === 'Escape') emit('close')
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      document.addEventListener('keydown', onKey)
      document.body.classList.add('modal-open')
    } else {
      document.removeEventListener('keydown', onKey)
      document.body.classList.remove('modal-open')
    }
  },
)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKey)
  document.body.classList.remove('modal-open')
})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="open" class="overlay" role="dialog" aria-modal="true" :aria-label="title" @click.self="emit('close')">
        <div class="modal" :class="{ 'modal--wide': wide }">
          <header class="modal__head">
            <div>
              <h2 class="modal__title">{{ title }}</h2>
              <p v-if="subtitle" class="modal__sub">{{ subtitle }}</p>
            </div>
            <button class="icon-btn" type="button" aria-label="Close dialog" @click="emit('close')">
              <Icon name="x" :size="18" />
            </button>
          </header>
          <div class="modal__body">
            <slot />
          </div>
          <footer v-if="$slots.footer" class="modal__foot">
            <slot name="footer" />
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding: 1.2rem;
  background: rgb(0 0 0 / 0.68);
}
.modal {
  width: min(560px, 100%);
  max-height: min(85vh, 920px);
  overflow: auto;
  background: var(--surface);
  border: 1px solid var(--line-strong);
  border-radius: 0;
  box-shadow: 8px 8px 0 var(--accent);
}
.modal--wide {
  width: min(900px, 100%);
}
.modal__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.4rem 1.5rem;
  border-bottom: 1px solid var(--line);
}
.modal__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 1.7rem;
  font-weight: 600;
  letter-spacing: -0.035em;
}
.modal__sub {
  margin: 0.4rem 0 0;
  color: var(--text-muted);
  font-size: 0.85rem;
}
.modal__body {
  padding: 1.5rem;
}
.modal__foot {
  display: flex;
  justify-content: flex-end;
  gap: 0.6rem;
  padding: 1rem 1.5rem;
  border-top: 1px solid var(--line);
  position: sticky;
  bottom: 0;
  background: var(--surface);
}
.modal-enter-active,
.modal-leave-active {
  transition: opacity 160ms ease;
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
.modal-enter-active .modal,
.modal-leave-active .modal {
  transition: transform 180ms ease;
}
.modal-enter-from .modal,
.modal-leave-to .modal {
  transform: translateY(10px) scale(0.99);
}
</style>
