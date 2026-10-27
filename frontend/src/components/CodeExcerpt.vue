<script setup lang="ts">
import { computed } from 'vue'

import type { CodeBlock, Origin } from '@/api/lessons'

const ORIGIN_LABELS: Record<Origin, string> = {
  SIMULATION: 'Simulation',
  TOOLKIT: 'Toolkit',
}

const { block, html } = defineProps<{
  block: CodeBlock
  /** the code highlighted as HTML, an element of the class `line` per line */
  html: string
}>()

const location = computed(
  () => `${block.path}:${String(block.firstLine)}–${String(block.lastLine)}`,
)
</script>

<template>
  <figure class="code-excerpt">
    <figcaption>
      <span class="origin" :data-origin="block.origin">{{ ORIGIN_LABELS[block.origin] }}</span>
      <code class="reference">{{ block.reference }}</code>
      <a v-if="block.url" class="location" :href="block.url" target="_blank">
        {{ location }} <span class="visually-hidden">(on GitHub, opens in a new tab)</span>
      </a>
      <span v-else class="location">{{ location }}</span>
    </figcaption>
    <!-- Shiki escapes the code, so its HTML holds no markup but its own -->
    <!-- eslint-disable-next-line vue/no-v-html -->
    <div class="code" :style="{ counterReset: `line ${block.firstLine - 1}` }" v-html="html"></div>
  </figure>
</template>

<style scoped>
.code-excerpt {
  margin: 1.5rem 0;
  border: 1px solid var(--color-border);
  border-radius: 0.5rem;
  overflow: hidden;
}

figcaption {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.25rem 0.75rem;
  padding: 0.5rem 0.75rem;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.8125rem;
}

.origin {
  padding: 0 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  color: var(--color-text-muted);
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.origin[data-origin='TOOLKIT'] {
  border-color: var(--color-link);
  color: var(--color-link);
}

.reference {
  font-family: var(--font-mono);
  font-weight: 600;
}

.location {
  overflow-wrap: anywhere;
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  font-size: 0.75rem;
}

a.location {
  color: var(--color-link);
}

.code :deep(pre) {
  margin: 0;
  padding: 0.75rem 0;
  overflow-x: auto;
  color: var(--shiki-light);
  background: var(--shiki-light-bg);
  font-family: var(--font-mono);
  font-size: 0.8125rem;
  line-height: 1.6;
}

.code :deep(span) {
  color: var(--shiki-light);
}

@media (prefers-color-scheme: dark) {
  .code :deep(pre) {
    color: var(--shiki-dark);
    background: var(--shiki-dark-bg);
  }

  .code :deep(span) {
    color: var(--shiki-dark);
  }
}

/* real line numbers of the file, which neither selections nor screen readers pick up */
.code :deep(.line)::before {
  display: inline-block;
  width: 3.5em;
  padding-right: 1em;
  color: var(--color-text-muted);
  text-align: right;
  counter-increment: line;
  content: counter(line);
  content: counter(line) / '';
  user-select: none;
}
</style>
