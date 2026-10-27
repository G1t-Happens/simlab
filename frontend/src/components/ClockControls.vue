<script setup lang="ts">
import { useId } from 'vue'

/** shares of real time the simulation can play at, from real time to slow motion */
const SPEEDS = [
  { value: 1, label: '1×' },
  { value: 0.5, label: '½×' },
  { value: 0.25, label: '¼×' },
  { value: 0.125, label: '⅛×' },
] as const

const { paused, speed, disabled } = defineProps<{
  paused: boolean
  /** share of real time at which the room plays the simulation */
  speed: number
  disabled?: boolean
}>()

const emit = defineEmits<{
  pause: []
  play: []
  /** advance the paused simulation by one tick */
  step: []
  speed: [value: number]
}>()

const speedId = useId()

function onSpeedChange(event: Event): void {
  if (event.target instanceof HTMLSelectElement) {
    emit('speed', Number(event.target.value))
  }
}
</script>

<template>
  <div class="clock-controls" role="group" aria-label="Time">
    <button type="button" :disabled="disabled" @click="paused ? emit('play') : emit('pause')">
      {{ paused ? 'Play' : 'Pause' }}
    </button>
    <button type="button" :disabled="disabled || !paused" @click="emit('step')">Step</button>
    <label :for="speedId">Speed</label>
    <select :id="speedId" :value="speed" :disabled="disabled" @change="onSpeedChange">
      <option v-for="option in SPEEDS" :key="option.value" :value="option.value">
        {{ option.label }}
      </option>
    </select>
  </div>
</template>

<style scoped>
.clock-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

label {
  margin-left: auto;
  color: var(--color-text-muted);
  font-size: 0.875rem;
}

select {
  padding: 0.375rem 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: 0.5rem;
  background: var(--color-background);
  color: inherit;
  font: inherit;
  font-size: 0.875rem;
}

select:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
</style>
