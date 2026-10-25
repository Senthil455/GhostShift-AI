<script setup lang="ts">
interface Token {
  kind: 'text' | 'code' | 'strong' | 'link'
  text: string
  href?: string
}

import { computed } from 'vue'

const props = defineProps<{ text: string }>()

const pattern = /(\*\*[^*\n]+\*\*|__[^_\n]+__|`[^`\n]+`|\[[^\]\n]+\]\(https?:\/\/[^\s)]+\))/g

function parseInline(input: string): Token[] {
  const tokens: Token[] = []
  let last = 0
  for (const match of input.matchAll(pattern)) {
    const index = match.index ?? 0
    if (index > last) tokens.push({ kind: 'text', text: input.slice(last, index) })
    const raw = match[0]
    if (raw.startsWith('**') || raw.startsWith('__')) {
      tokens.push({ kind: 'strong', text: raw.slice(2, -2) })
    } else if (raw.startsWith('`')) {
      tokens.push({ kind: 'code', text: raw.slice(1, -1) })
    } else {
      const link = /^\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)$/.exec(raw)
      if (link) {
        tokens.push({ kind: 'link', text: link[1], href: link[2] })
      } else {
        tokens.push({ kind: 'text', text: raw })
      }
    }
    last = index + raw.length
  }
  if (last < input.length) tokens.push({ kind: 'text', text: input.slice(last) })
  return tokens.length > 0 ? tokens : [{ kind: 'text', text: input }]
}

const tokens = computed(() => parseInline(props.text))
</script>

<template>
  <template v-for="(token, index) in tokens" :key="index">
    <code v-if="token.kind === 'code'">{{ token.text }}</code>
    <strong v-else-if="token.kind === 'strong'">{{ token.text }}</strong>
    <a v-else-if="token.kind === 'link' && token.href" :href="token.href" target="_blank" rel="noopener noreferrer">{{ token.text }}</a>
    <template v-else>{{ token.text }}</template>
  </template>
</template>
