<script setup lang="ts">
import { computed } from 'vue'
import MarkdownInline from './MarkdownInline.vue'

type Block =
  | { kind: 'heading'; level: number; text: string }
  | { kind: 'paragraph'; text: string }
  | { kind: 'list'; ordered: boolean; items: string[] }
  | { kind: 'code'; text: string; lang: string }
  | { kind: 'quote'; text: string }
  | { kind: 'hr' }

const props = defineProps<{ content: string }>()

function parse(content: string): Block[] {
  const lines = content.replace(/\r\n?/g, '\n').split('\n')
  const blocks: Block[] = []
  let index = 0

  while (index < lines.length) {
    const line = lines[index]
    const trimmed = line.trim()

    if (!trimmed) {
      index++
      continue
    }

    if (trimmed.startsWith('```')) {
      const lang = trimmed.slice(3).trim()
      const body: string[] = []
      index++
      while (index < lines.length && !lines[index].trim().startsWith('```')) {
        body.push(lines[index])
        index++
      }
      index++
      blocks.push({ kind: 'code', text: body.join('\n'), lang })
      continue
    }

    const heading = /^(#{1,6})\s+(.*)$/.exec(trimmed)
    if (heading) {
      blocks.push({ kind: 'heading', level: Math.min(3, heading[1].length), text: heading[2].trim() })
      index++
      continue
    }

    if (/^([-*_])(?:\s*\1){2,}$/.test(trimmed)) {
      blocks.push({ kind: 'hr' })
      index++
      continue
    }

    if (trimmed.startsWith('>')) {
      const quote: string[] = []
      while (index < lines.length && lines[index].trim().startsWith('>')) {
        quote.push(lines[index].trim().replace(/^>\s?/, ''))
        index++
      }
      blocks.push({ kind: 'quote', text: quote.join(' ') })
      continue
    }

    const listMatch = /^(\s*)([-*•]|\d+[.)])\s+(.*)$/.exec(line)
    if (listMatch) {
      const ordered = /^\d/.test(listMatch[2])
      const items: string[] = []
      while (index < lines.length) {
        const item = /^(\s*)([-*•]|\d+[.)])\s+(.*)$/.exec(lines[index])
        if (!item || /^\d/.test(item[2]) !== ordered) break
        items.push(item[3].trim())
        index++
      }
      blocks.push({ kind: 'list', ordered, items })
      continue
    }

    const paragraph: string[] = []
    while (index < lines.length) {
      const current = lines[index].trim()
      if (!current) break
      if (
        current.startsWith('```') ||
        /^(#{1,6})\s+/.test(current) ||
        /^(\s*)([-*•]|\d+[.)])\s+/.test(lines[index]) ||
        current.startsWith('>') ||
        /^([-*_])(?:\s*\1){2,}$/.test(current)
      ) {
        break
      }
      paragraph.push(current)
      index++
    }
    if (paragraph.length > 0) {
      blocks.push({ kind: 'paragraph', text: paragraph.join(' ') })
    } else {
      index++
    }
  }
  return blocks
}

const blocks = computed(() => parse(props.content))
</script>

<template>
  <div class="markdown">
    <template v-for="(block, index) in blocks" :key="index">
      <h1 v-if="block.kind === 'heading' && block.level === 1"><MarkdownInline :text="block.text" /></h1>
      <h2 v-else-if="block.kind === 'heading' && block.level === 2"><MarkdownInline :text="block.text" /></h2>
      <h3 v-else-if="block.kind === 'heading'"><MarkdownInline :text="block.text" /></h3>
      <hr v-else-if="block.kind === 'hr'" />
      <blockquote v-else-if="block.kind === 'quote'"><MarkdownInline :text="block.text" /></blockquote>
      <pre v-else-if="block.kind === 'code'"><code>{{ block.text }}</code></pre>
      <ol v-else-if="block.kind === 'list' && block.ordered">
        <li v-for="(item, itemIndex) in block.items" :key="itemIndex"><MarkdownInline :text="item" /></li>
      </ol>
      <ul v-else-if="block.kind === 'list'">
        <li v-for="(item, itemIndex) in block.items" :key="itemIndex"><MarkdownInline :text="item" /></li>
      </ul>
      <p v-else-if="block.kind === 'paragraph'"><MarkdownInline :text="block.text" /></p>
    </template>
  </div>
</template>

<style scoped>
.markdown {
  color: var(--text-muted);
  font-size: 0.88rem;
  line-height: 1.68;
  overflow-wrap: anywhere;
}
.markdown :deep(h1),
.markdown :deep(h2),
.markdown :deep(h3) {
  font-family: var(--font-display);
  color: var(--text);
  font-weight: 470;
  letter-spacing: -0.015em;
  line-height: 1.15;
  margin: 1.5em 0 0.55em;
}
.markdown :deep(h1) {
  font-size: 1.65rem;
}
.markdown :deep(h2) {
  font-size: 1.28rem;
}
.markdown :deep(h3) {
  font-size: 1.05rem;
}
.markdown :deep(h1:first-child),
.markdown :deep(h2:first-child) {
  margin-top: 0;
}
.markdown :deep(p) {
  margin: 0.65em 0;
}
.markdown :deep(ul),
.markdown :deep(ol) {
  margin: 0.6em 0;
  padding-left: 1.25rem;
}
.markdown :deep(li) {
  margin: 0.3em 0;
}
.markdown :deep(strong) {
  color: var(--text);
  font-weight: 600;
}
.markdown :deep(code) {
  font-family: var(--font-mono);
  font-size: 0.82em;
  background: var(--surface-3);
  border-radius: 3px;
  padding: 0.08em 0.3em;
  color: var(--text);
}
.markdown :deep(pre) {
  margin: 0.9em 0;
  padding: 0.9rem;
  background: var(--ink);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  overflow-x: auto;
}
.markdown :deep(pre code) {
  background: none;
  padding: 0;
  font-size: 0.76rem;
  line-height: 1.55;
}
.markdown :deep(blockquote) {
  margin: 1em 0;
  padding: 0.55rem 0.9rem;
  border-left: 2px solid var(--accent);
  background: color-mix(in srgb, var(--accent) 5%, transparent);
  color: var(--text-muted);
  font-size: 0.82rem;
}
.markdown :deep(hr) {
  border: 0;
  border-top: 1px solid var(--line);
  margin: 1.5rem 0;
}
.markdown :deep(a) {
  color: var(--accent);
  text-decoration: underline;
  text-underline-offset: 2px;
}
</style>
