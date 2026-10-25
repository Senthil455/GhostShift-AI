<script setup lang="ts">
import { computed, ref } from 'vue'
import { api } from '@/api/client'
import type { RegisteredDirectory } from '@/api/types'
import { useSessionStore } from '@/stores/session'
import { useToastStore } from '@/stores/toast'
import { errorMessage } from '@/lib/errors'
import Icon from './Icon.vue'
import Modal from './Modal.vue'

const props = withDefaults(
  defineProps<{
    modelValue: number | null
    label?: string
    hint?: string
    allowCreate?: boolean
    organizeDefault?: boolean
    disabled?: boolean
    showSelect?: boolean
  }>(),
  {
    label: 'Folder',
    hint: '',
    allowCreate: true,
    organizeDefault: false,
    disabled: false,
    showSelect: true,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: number | null]
  created: [directory: RegisteredDirectory]
}>()

const session = useSessionStore()
const toast = useToastStore()

const open = ref(false)
const busy = ref(false)
const refreshing = ref(false)
const error = ref('')
const path = ref('')
const displayName = ref('')
const allowOrganize = ref(props.organizeDefault)

const directories = computed(() => session.directories.filter((directory) => directory.active))
const selected = computed(() => directories.value.find((directory) => directory.id === props.modelValue) ?? null)

function select(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  emit('update:modelValue', value ? Number(value) : null)
}

async function refresh() {
  refreshing.value = true
  error.value = ''
  try {
    await session.refreshDirectories()
  } catch (cause) {
    error.value = errorMessage(cause, 'Folders could not be loaded.')
  } finally {
    refreshing.value = false
  }
}

function showCreate() {
  error.value = ''
  path.value = ''
  displayName.value = ''
  allowOrganize.value = props.organizeDefault
  open.value = true
}

async function register() {
  if (!path.value.trim()) {
    error.value = 'Enter the full path of a folder on this machine.'
    return
  }
  busy.value = true
  error.value = ''
  try {
    const directory = await api.registerDirectory({
      path: path.value.trim(),
      displayName: displayName.value.trim() || undefined,
      directoryType: 'GENERAL',
      allowOrganize: allowOrganize.value,
    })
    await session.refreshDirectories()
    emit('update:modelValue', directory.id)
    emit('created', directory)
    toast.success('Folder registered', directory.displayName)
    open.value = false
    path.value = ''
    displayName.value = ''
  } catch (cause) {
    error.value = errorMessage(cause, 'That folder could not be registered.')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="field directory">
    <label :for="showSelect ? `directory-${label.replace(/\s+/g, '-').toLowerCase()}` : undefined">{{ label }}</label>
    <div class="directory__row">
      <select
        v-if="showSelect"
        :id="`directory-${label.replace(/\s+/g, '-').toLowerCase()}`"
        :value="modelValue ?? ''"
        :disabled="disabled"
        @change="select"
      >
        <option value="">{{ directories.length ? 'Choose a registered folder…' : 'No folders registered yet' }}</option>
        <option v-for="directory in directories" :key="directory.id" :value="directory.id">
          {{ directory.displayName }}{{ directory.allowOrganize ? '' : ' · read only' }}
        </option>
      </select>
      <button v-if="showSelect" class="icon-btn" type="button" aria-label="Refresh folders" :disabled="refreshing" @click="refresh">
        <span v-if="refreshing" class="spinner" />
        <Icon v-else name="refresh" :size="16" />
      </button>
      <button v-if="allowCreate" class="btn btn--sm" type="button" :disabled="disabled" @click="showCreate">
        <Icon name="plus" :size="14" />
        Add folder
      </button>
    </div>
    <p v-if="hint" class="field__hint">{{ hint }}</p>
    <p v-if="selected" class="directory__path mono">{{ selected.path }}</p>
    <p v-if="selected && !selected.allowOrganize" class="directory__warning">
      <Icon name="lock" :size="13" />
      This folder is read-only. File organization needs a folder registered with organize permission.
    </p>
  </div>

  <Modal
    :open="open"
    title="Register a folder"
    subtitle="GhostShift can only touch files inside folders you explicitly register."
    @close="open = false"
  >
    <form class="form-grid directory__form" @submit.prevent="register">
      <div class="field field--wide">
        <label for="register-path">Absolute folder path</label>
        <input id="register-path" v-model="path" type="text" autocomplete="off" placeholder="C:\Users\you\Downloads" />
        <p class="field__hint">The path is canonicalized and checked on the backend. Symlinks that escape the folder are skipped.</p>
      </div>
      <div class="field">
        <label for="register-name">Display name</label>
        <input id="register-name" v-model="displayName" type="text" autocomplete="off" placeholder="Downloads" />
      </div>
      <div class="field directory__permission">
        <label>Permission</label>
        <label class="check">
          <input v-model="allowOrganize" type="checkbox" />
          Allow move operations inside this folder
        </label>
        <p class="field__hint">Without this, workflows can read but never move files here.</p>
      </div>
      <p v-if="error" class="form-error field--wide" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <button class="btn btn--ghost" type="button" @click="open = false">Cancel</button>
      <button class="btn btn--primary" type="button" :disabled="busy" @click="register">
        <span v-if="busy" class="spinner" />
        <Icon v-else name="folder" :size="15" />
        Register folder
      </button>
    </template>
  </Modal>
</template>

<style scoped>
.directory__row {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
.directory__row select {
  flex: 1;
  min-width: 0;
}
.directory__path {
  margin: 0.1rem 0 0;
  color: var(--text-faint);
  font-size: 0.64rem;
  overflow-wrap: anywhere;
}
.directory__warning {
  display: flex;
  align-items: flex-start;
  gap: 0.35rem;
  margin: 0.1rem 0 0;
  color: var(--warn);
  font-size: 0.72rem;
  line-height: 1.45;
}
.directory__warning .icon {
  margin-top: 0.08rem;
}
.directory__form {
  margin-top: 0.2rem;
}
.directory__permission {
  justify-content: flex-start;
}
.form-error {
  margin: 0;
  color: var(--danger);
  font-size: 0.78rem;
}
</style>
