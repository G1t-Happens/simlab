<script setup lang="ts">
import { ref, useId, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { injectBackendHealth } from '@/composables/useBackendHealth'
import { useSimulationsStore } from '@/stores/simulations'

const store = useSimulationsStore()
const backend = injectBackendHealth()
const failure = ref<string>()
const headingId = useId()

async function load(): Promise<void> {
  failure.value = undefined
  try {
    await store.load()
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error)
    failure.value = `The simulations could not be loaded: ${reason}`
  }
}

// a backend that's back offers its simulations again
watch(backend.health, (health, previous) => {
  if (failure.value !== undefined && health === 'UP' && previous !== 'UP') {
    void load()
  }
})

void load()
</script>

<template>
  <section class="intro">
    <h1>Live physics simulations</h1>
    <p>
      simlab computes physics simulations on a Spring Boot server and streams them to the browser,
      where Vue and Three.js render them in real time.
    </p>
  </section>

  <section :aria-labelledby="headingId">
    <h2 :id="headingId">Simulations</h2>
    <p v-if="failure" class="failure" role="alert">{{ failure }}</p>
    <ul v-else-if="store.simulations" class="cards">
      <li v-for="simulation in store.simulations" :key="simulation.type">
        <RouterLink :to="{ name: 'simulation', params: { type: simulation.type } }" class="card">
          <strong>{{ simulation.name }}</strong>
          <span>{{ simulation.description }}</span>
        </RouterLink>
      </li>
    </ul>
    <p v-else class="loading">Loading…</p>
  </section>
</template>

<style scoped>
.intro {
  margin-bottom: 3rem;
}

h1 {
  margin: 0 0 1rem;
  font-size: clamp(2rem, 5vw, 3rem);
  letter-spacing: -0.02em;
  line-height: 1.1;
}

.intro p {
  max-width: 40rem;
  margin: 0;
  color: var(--color-text-muted);
  font-size: 1.125rem;
}

h2 {
  margin: 0 0 1rem;
  font-size: 1.25rem;
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(16rem, 1fr));
  gap: 1rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.card {
  display: grid;
  gap: 0.25rem;
  height: 100%;
  padding: 1.25rem;
  border: 1px solid var(--color-border);
  border-radius: 0.75rem;
  color: inherit;
  text-decoration: none;
  transition: border-color 0.15s;
}

.card:hover {
  border-color: var(--color-link);
}

.card span {
  color: var(--color-text-muted);
}

.loading,
.failure {
  margin: 0;
}

.loading {
  color: var(--color-text-muted);
}

.failure {
  color: var(--color-negative);
}
</style>
