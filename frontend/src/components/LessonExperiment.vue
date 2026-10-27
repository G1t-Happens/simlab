<script setup lang="ts">
import { computed, useId } from 'vue'

import type { ParameterInfo } from '@/api/simulations'
import { withUnit } from '@/units'

const { question, explanation, values, parameters, disabled } = defineProps<{
  /** what the experiment asks, as HTML */
  question: string
  /** why the simulation behaves as it does, as HTML */
  explanation: string
  values: Readonly<Record<string, number>>
  /** parameters of the simulation, their labels and units name the values */
  parameters: readonly ParameterInfo[]
  disabled?: boolean
}>()

const emit = defineEmits<{ run: [] }>()

const questionId = useId()

/** the values as the controls name them, like `ρ = 0.5`, `v = 1 m/s` or `p = Euler` */
const settings = computed(() =>
  Object.entries(values)
    .map(([name, value]) => {
      const parameter = parameters.find((candidate) => candidate.name === name)
      return `${parameter?.label ?? name} = ${displayedValue(parameter, value)}`
    })
    .join(', '),
)

/** a value as the control of its parameter shows it: with its unit, or as its option */
function displayedValue(parameter: ParameterInfo | undefined, value: number): string {
  switch (parameter?.kind) {
    case 'Range':
      return withUnit(String(value), parameter.unit)
    case 'Choice':
      return parameter.options.find((option) => option.value === value)?.label ?? String(value)
    case undefined:
      return String(value)
  }
}

/** whether the experiment sets the other parameters back to their defaults */
const resetsOthers = computed(() => parameters.some((parameter) => !(parameter.name in values)))
</script>

<template>
  <div class="experiment" role="group" :aria-labelledby="questionId">
    <p class="label">Experiment</p>
    <!-- markdown-it keeps raw HTML as text, KaTeX adds no HTML but marks of symbols -->
    <!-- eslint-disable-next-line vue/no-v-html -->
    <div :id="questionId" class="question" v-html="question"></div>
    <p class="hint">Predict what happens, then try it.</p>
    <button type="button" :disabled="disabled" @click="emit('run')">Try {{ settings }}</button>
    <p v-if="resetsOthers" class="hint">The other parameters return to their defaults.</p>
    <details>
      <summary>What happens</summary>
      <!-- eslint-disable-next-line vue/no-v-html -->
      <div class="explanation" v-html="explanation"></div>
    </details>
  </div>
</template>

<style scoped>
.experiment {
  display: grid;
  justify-items: start;
  gap: 0.5rem;
  margin: 1.5rem 0;
  padding: 0.75rem 1rem;
  border: 1px solid var(--color-border);
  border-left: 0.25rem solid var(--color-link);
  border-radius: 0.5rem;
}

.label {
  margin: 0;
  color: var(--color-link);
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.question :deep(p) {
  margin: 0;
  font-weight: 600;
}

.hint {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 0.875rem;
}

button {
  border-color: var(--color-link);
  color: var(--color-link);
}

details {
  justify-self: stretch;
}

summary {
  color: var(--color-link);
  font-size: 0.875rem;
  cursor: pointer;
}

.explanation :deep(p) {
  margin: 0.5rem 0 0;
}
</style>
