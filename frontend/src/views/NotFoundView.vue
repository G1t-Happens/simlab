<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <section>
    <h1>Page not found</h1>
    <p>
      There is nothing at this address.
      <RouterLink to="/">Go to the start page</RouterLink>
    </p>
  </section>
</template>
