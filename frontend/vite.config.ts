import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

const backend = 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), vueDevTools()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // the preview server uses this too. The Host header is passed through unchanged (no
    // changeOrigin) since Spring only accepts WebSocket handshakes if Origin and Host match
    proxy: {
      '/actuator': backend,
      '/api': backend,
      '/ws': { target: backend, ws: true },
    },
  },
  build: {
    // three.js alone is about 550 kB, only the simulation view loads it
    chunkSizeWarningLimit: 600,
    rolldownOptions: {
      output: {
        // in a chunk of its own three.js stays cached across releases
        codeSplitting: {
          groups: [{ name: 'three', test: /[\\/]node_modules[\\/]three[\\/]/ }],
        },
      },
    },
  },
})
