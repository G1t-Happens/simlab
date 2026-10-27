<script setup lang="ts">
import { provide } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'

import BackendStatus from '@/components/BackendStatus.vue'
import { backendHealthKey, useBackendHealth } from '@/composables/useBackendHealth'

const backend = useBackendHealth()
const { health } = backend
provide(backendHealthKey, backend)

const route = useRoute()
</script>

<template>
  <header class="app-header">
    <RouterLink to="/" class="brand">simlab</RouterLink>
    <BackendStatus :health="health" />
  </header>
  <main class="app-main">
    <!-- keyed by its path, so a view starts over when the address changes to another simulation -->
    <RouterView :key="route.path" />
  </main>
</template>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid var(--color-border);
}

.brand {
  color: var(--color-text);
  font-size: 1.125rem;
  font-weight: 700;
  letter-spacing: -0.01em;
  text-decoration: none;
}

.app-main {
  max-width: 64rem;
  margin: 0 auto;
  padding: 3rem 1.5rem;
}
</style>
