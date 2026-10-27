<script setup lang="ts">
import { useId } from 'vue'

import type { ParameterInfo, RangeParameter } from '@/api/simulations'
import { withUnit } from '@/units'

const { parameters, values, disabled, highlighted } = defineProps<{
  parameters: readonly ParameterInfo[]
  /** current value of every parameter, by name */
  values: Readonly<Record<string, number>>
  disabled?: boolean
  /** parameter that lights up, like its symbol does in the lesson */
  highlighted: string | undefined
}>()

const emit = defineEmits<{
  change: [name: string, value: number]
  /** pointer or focus moved to the control of a parameter, or away from it */
  highlight: [name: string | undefined]
}>()

const idPrefix = useId()

function inputId(parameter: ParameterInfo): string {
  return `${idPrefix}-${parameter.name}`
}

/**
 * Value of a parameter with its unit: two decimals, or as many as the smallest positive value of
 * its range needs, like three for 0.001
 */
function displayedValue(parameter: RangeParameter): string {
  const decimals = parameter.min > 0 ? Math.max(2, -Math.floor(Math.log10(parameter.min))) : 2
  return withUnit(values[parameter.name]?.toFixed(decimals) ?? '', parameter.unit)
}

function onInput(parameter: ParameterInfo, event: Event): void {
  if (event.target instanceof HTMLInputElement) {
    emit('change', parameter.name, event.target.valueAsNumber)
  }
}
</script>

<template>
  <div class="parameter-controls">
    <template v-for="parameter in parameters" :key="parameter.name">
      <div
        v-if="parameter.kind === 'Range'"
        class="parameter"
        :class="{ highlighted: parameter.name === highlighted }"
        @mouseenter="emit('highlight', parameter.name)"
        @mouseleave="emit('highlight', undefined)"
        @focusin="emit('highlight', parameter.name)"
        @focusout="emit('highlight', undefined)"
      >
        <!--
          the symbol stays a text node: Vue drops whitespace between two elements on separate lines
        -->
        <label :for="inputId(parameter)">
          {{ parameter.label }} <span class="name">{{ parameter.name }}</span>
        </label>
        <output :for="inputId(parameter)">{{ displayedValue(parameter) }}</output>
        <input
          :id="inputId(parameter)"
          type="range"
          :min="parameter.min"
          :max="parameter.max"
          step="any"
          :value="values[parameter.name] ?? parameter.defaultValue"
          :disabled="disabled"
          @input="onInput(parameter, $event)"
        />
      </div>
      <fieldset
        v-else
        class="parameter choice"
        :class="{ highlighted: parameter.name === highlighted }"
        :disabled="disabled"
        @mouseenter="emit('highlight', parameter.name)"
        @mouseleave="emit('highlight', undefined)"
        @focusin="emit('highlight', parameter.name)"
        @focusout="emit('highlight', undefined)"
      >
        <legend>
          {{ parameter.label }} <span class="name">{{ parameter.name }}</span>
        </legend>
        <div class="options">
          <label v-for="option in parameter.options" :key="option.value" class="option">
            <input
              type="radio"
              :name="inputId(parameter)"
              :value="option.value"
              :checked="(values[parameter.name] ?? parameter.defaultValue) === option.value"
              @change="emit('change', parameter.name, option.value)"
            />
            {{ option.label }}
          </label>
        </div>
      </fieldset>
    </template>
  </div>
</template>

<style scoped>
.parameter-controls {
  display: grid;
  gap: 1.25rem;
}

.parameter {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0.25rem 0.75rem;
  align-items: baseline;
  border-radius: 0.25rem;
}

/* the shadow widens the highlight beyond the control without moving it */
.parameter.highlighted {
  background: var(--color-highlight);
  box-shadow: 0 0 0 0.375rem var(--color-highlight);
}

label,
legend {
  font-size: 1.125rem;
  font-weight: 600;
}

.name {
  color: var(--color-text-muted);
  font-size: 0.875rem;
  font-weight: 400;
}

output {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}

input[type='range'] {
  grid-column: 1 / -1;
  width: 100%;
  accent-color: var(--color-link);
}

/* a fieldset lays out like the other controls, without a frame of its own */
.choice {
  margin: 0;
  padding: 0;
  border: none;
}

.choice legend {
  float: left;
  padding: 0;
}

.options {
  display: flex;
  flex-wrap: wrap;
  grid-column: 1 / -1;
  gap: 0.25rem 1rem;
}

.option {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  font-size: 1rem;
  font-weight: 400;
}

.option input {
  margin: 0;
  accent-color: var(--color-link);
}

.choice:disabled .option {
  cursor: not-allowed;
  opacity: 0.5;
}
</style>
