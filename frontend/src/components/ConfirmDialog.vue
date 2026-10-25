<script setup lang="ts">
import Modal from './Modal.vue'

withDefaults(
  defineProps<{
    open: boolean
    title: string
    description?: string
    confirmLabel?: string
    cancelLabel?: string
    tone?: 'primary' | 'danger'
    busy?: boolean
  }>(),
  {
    description: '',
    confirmLabel: 'Confirm',
    cancelLabel: 'Cancel',
    tone: 'primary',
    busy: false,
  },
)

const emit = defineEmits<{ close: []; confirm: [] }>()
</script>

<template>
  <Modal :open="open" :title="title" :subtitle="description" @close="emit('close')">
    <slot />
    <template #footer>
      <button class="btn btn--ghost" type="button" :disabled="busy" @click="emit('close')">
        {{ cancelLabel }}
      </button>
      <button
        class="btn"
        :class="tone === 'danger' ? 'btn--danger' : 'btn--primary'"
        type="button"
        :disabled="busy"
        @click="emit('confirm')"
      >
        <span v-if="busy" class="spinner" />
        {{ confirmLabel }}
      </button>
    </template>
  </Modal>
</template>
