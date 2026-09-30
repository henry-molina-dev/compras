/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(import.meta.dirname, './src') },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    globals: true,
    // Varios archivos de jsdom en paralelo pueden triplicar el tiempo de un test pesado (p. ej. el
    // formulario de orden: ~2s solo, >5s con toda la suite); el valor por defecto de 5s daba fallos
    // intermitentes que no reflejaban ningun error real.
    testTimeout: 15000,
  },
})
