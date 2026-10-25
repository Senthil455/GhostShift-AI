import { defineStore } from 'pinia'

export type ToastTone = 'success' | 'error' | 'info' | 'warning'

export interface Toast {
  id: number
  tone: ToastTone
  title: string
  detail?: string
}

let nextId = 1

export const useToastStore = defineStore('toast', {
  state: () => ({
    items: [] as Toast[],
  }),
  actions: {
    push(tone: ToastTone, title: string, detail?: string) {
      const id = nextId++
      this.items.push({ id, tone, title, detail })
      window.setTimeout(() => this.dismiss(id), tone === 'error' ? 8000 : 4500)
      return id
    },
    success(title: string, detail?: string) {
      return this.push('success', title, detail)
    },
    error(title: string, detail?: string) {
      return this.push('error', title, detail)
    },
    info(title: string, detail?: string) {
      return this.push('info', title, detail)
    },
    warning(title: string, detail?: string) {
      return this.push('warning', title, detail)
    },
    dismiss(id: number) {
      this.items = this.items.filter((item) => item.id !== id)
    },
  },
})
