<script setup lang="ts">
import type { Readout } from '@/api/scene'
import { formatNumber } from '@/numbers'
import { withUnit } from '@/units'

const { readouts, values, highlighted } = defineProps<{
  readouts: readonly Readout[]
  /** latest value of every readout by name, NaN while it's unknown */
  values: Readonly<Record<string, number>>
  /** readout that lights up, like its symbol does in the lesson */
  highlighted: string | undefined
}>()

const emit = defineEmits<{
  highlight: [name: string | undefined]
}>()

/** value of a readout with its unit, or a dash while the value is unknown */
function displayedValue({ name, unit }: Readout): string {
  const value = values[name]
  return value !== undefined && Number.isFinite(value) ? withUnit(formatNumber(value), unit) : '–'
}
</script>

<template>
  <dl class="readout-list">
    <div
      v-for="readout in readouts"
      :key="readout.name"
      class="readout"
      :class="{ highlighted: readout.name === highlighted }"
      @mouseenter="emit('highlight', readout.name)"
      @mouseleave="emit('highlight', undefined)"
    >
      <!--
        the label stays a text node: Vue drops whitespace between two elements on separate lines
      -->
      <dt>
        {{ readout.label }}
        <span v-if="readout.name !== readout.label" class="name">{{ readout.name }}</span>
      </dt>
      <dd>{{ displayedValue(readout) }}</dd>
    </div>
  </dl>
</template>

<style scoped>
.readout-list {
  display: grid;
  gap: 0.5rem;
  margin: 0;
}

.readout {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0.75rem;
  align-items: baseline;
  border-radius: 0.25rem;
}

/* the shadow widens the highlight beyond the readout without moving it */
.readout.highlighted {
  background: var(--color-highlight);
  box-shadow: 0 0 0 0.375rem var(--color-highlight);
}

dt {
  font-size: 1.125rem;
  font-weight: 600;
}

.name {
  color: var(--color-text-muted);
  font-size: 0.875rem;
  font-weight: 400;
}

dd {
  margin: 0;
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}
</style>
