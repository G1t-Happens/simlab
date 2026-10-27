<script setup lang="ts">
import { computed } from 'vue'

import { substituteValues } from '@/lesson/calculation'
import { renderFormula } from '@/lesson/markdown'

const { formula, values } = defineProps<{
  /** TeX formula that marks its symbols */
  formula: string
  /** current value of every symbol that has one, by the name of the symbol */
  values: Readonly<Record<string, number>>
}>()

/** the formula with the values in place of their symbols, as long as they're known */
const html = computed(() => renderFormula(substituteValues(formula, values)))
</script>

<template>
  <figure class="calculation">
    <figcaption>Live calculation</figcaption>
    <!-- KaTeX adds no HTML but marks of symbols, and the values in their place are numbers -->
    <!-- eslint-disable-next-line vue/no-v-html -->
    <div class="formula" v-html="html"></div>
  </figure>
</template>

<style scoped>
.calculation {
  margin: 1.5rem 0;
  padding: 0.5rem 1rem;
  border: 1px solid var(--color-border);
  border-left: 0.25rem solid var(--color-positive);
  border-radius: 0.5rem;
}

figcaption {
  color: var(--color-positive);
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

/* formulas too wide for the panel scroll sideways instead of overflowing it */
.formula :deep(.katex-display) {
  margin: 0.5rem 0;
  padding: 0.25rem 0;
  overflow: auto hidden;
}
</style>
