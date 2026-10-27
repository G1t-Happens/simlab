<script setup lang="ts">
import { computed } from 'vue'

import type { BackendHealth } from '@/composables/useBackendHealth'

const { health } = defineProps<{ health: BackendHealth }>()

type Tone = 'positive' | 'negative' | 'warning' | 'neutral'

const APPEARANCES: Record<BackendHealth, { label: string; tone: Tone }> = {
  PENDING: { label: 'checking…', tone: 'neutral' },
  UP: { label: 'UP', tone: 'positive' },
  DOWN: { label: 'DOWN', tone: 'negative' },
  OUT_OF_SERVICE: { label: 'OUT OF SERVICE', tone: 'negative' },
  UNKNOWN: { label: 'UNKNOWN', tone: 'warning' },
  UNREACHABLE: { label: 'unreachable', tone: 'negative' },
}

const appearance = computed(() => APPEARANCES[health])
</script>

<template>
  <p class="backend-status" role="status" :data-tone="appearance.tone">
    <span class="indicator" aria-hidden="true"></span>
    Backend <strong>{{ appearance.label }}</strong>
  </p>
</template>

<style scoped>
.backend-status {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0;
  padding: 0.25rem 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  color: var(--color-text-muted);
  font-size: 0.875rem;
}

.backend-status[data-tone='positive'] {
  --tone: var(--color-positive);
}

.backend-status[data-tone='negative'] {
  --tone: var(--color-negative);
}

.backend-status[data-tone='warning'] {
  --tone: var(--color-warning);
}

.backend-status[data-tone='neutral'] {
  --tone: var(--color-neutral);
}

.indicator {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
  background: var(--tone);
}

strong {
  color: var(--color-text);
  font-weight: 600;
}
</style>
