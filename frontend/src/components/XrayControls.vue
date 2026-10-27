<script setup lang="ts">
import type { Color, ColorScale, SceneObject } from '@/api/scene'
import { formatNumber } from '@/numbers'
import { withUnit } from '@/units'

const { objects, shown, disabled } = defineProps<{
  /** x-ray objects, the legend explains them while the x-ray shows */
  objects: readonly SceneObject[]
  shown: boolean
  disabled?: boolean
}>()

const emit = defineEmits<{
  show: [shown: boolean]
}>()

function onChange(event: Event): void {
  if (event.target instanceof HTMLInputElement) {
    emit('show', event.target.checked)
  }
}

function cssColor({ red, green, blue }: Color): string {
  return `rgb(${String(red)} ${String(green)} ${String(blue)})`
}

/** colors of a scale from left to right, CSS spreads them evenly like the scale does */
function gradient({ colors }: ColorScale): string {
  return `linear-gradient(to right, ${colors.map(cssColor).join(', ')})`
}

/** an end of a scale with its unit, like `−2 %` */
function scaleValue(value: number, { unit }: ColorScale): string {
  return withUnit(formatNumber(value), unit)
}
</script>

<template>
  <div class="xray-controls">
    <label class="toggle">
      <input type="checkbox" :checked="shown" :disabled="disabled" @change="onChange" />
      X-ray
    </label>
    <ul v-if="shown" class="legend" aria-label="What the x-ray shows">
      <template v-for="(object, index) in objects" :key="index">
        <li v-if="object.kind === 'Lines'">
          {{ object.label }}
          <span class="ramp" :style="{ background: gradient(object.scale) }"></span>
          <span class="ends">
            <span>{{ scaleValue(object.scale.min, object.scale) }}</span>
            <span class="quantity">{{ object.scale.label }}</span>
            <span>{{ scaleValue(object.scale.max, object.scale) }}</span>
          </span>
        </li>
        <li v-else-if="object.kind === 'Cubes'" class="cubes">
          <span class="swatch" :style="{ borderColor: cssColor(object.color) }"></span>
          {{ object.label }}
        </li>
      </template>
    </ul>
  </div>
</template>

<style scoped>
.xray-controls {
  display: grid;
  gap: 0.75rem;
}

.toggle {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
}

.toggle input {
  margin: 0;
  accent-color: var(--color-link);
}

.toggle:has(input:disabled) {
  cursor: not-allowed;
  opacity: 0.5;
}

.legend {
  display: grid;
  gap: 0.75rem;
  margin: 0;
  padding: 0;
  color: var(--color-text-muted);
  font-size: 0.875rem;
  list-style: none;
}

.legend li {
  display: grid;
  gap: 0.375rem;
}

.ramp {
  height: 0.5rem;
  border-radius: 0.25rem;
}

.ends {
  display: flex;
  justify-content: space-between;
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}

.quantity {
  color: var(--color-text);
  font-family: inherit;
  font-weight: 600;
}

.cubes {
  grid-template-columns: auto 1fr;
  align-items: center;
  gap: 0.5rem;
}

.swatch {
  width: 0.875rem;
  height: 0.875rem;
  border: 2px solid;
}
</style>
