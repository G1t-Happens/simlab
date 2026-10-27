import { fileURLToPath } from 'node:url'
import { mergeConfig, defineConfig, configDefaults } from 'vitest/config'
import viteConfig from './vite.config.ts'

export default mergeConfig(
  viteConfig,
  defineConfig({
    server: {
      fs: {
        // besides the frontend, tests read the lessons of the simulations in sim-core
        allow: ['.', '../backend/sim-core/src/main/resources/dev/simlab/core/simulations'],
      },
    },
    test: {
      environment: 'jsdom',
      exclude: [...configDefaults.exclude, 'e2e/**'],
      root: fileURLToPath(new URL('./', import.meta.url)),
      mockReset: true,
      restoreMocks: true,
      unstubGlobals: true,
      coverage: {
        include: ['src/**/*.{ts,vue}'],
        exclude: ['src/**/__tests__/**'],
      },
    },
  }),
)
