import { defineConfig } from 'vitest/config'
import react, { reactCompilerPreset } from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'

// https://vite.dev/config/
export default defineConfig({
  base: '/ui/',
  plugins: [
    react(),
    babel({ presets: [reactCompilerPreset()] })
  ],
  server: {
    // dev loop against the running backend (mvn -pl factory-backend spring-boot:run)
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './test/shared/setup.ts',
    // the app's own sheets stay readable as text for the colour contract of R-WP-29; everything else,
    // Bootstrap above all, stays stubbed — jsdom lays out nothing either way
    css: { include: [/\/src\/[^/]+\.css(\?|$)/] },
  },
})
