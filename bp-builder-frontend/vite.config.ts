import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

// Vite config — https://vitejs.dev/config/
// The dev server runs on port 8443 unless PORT is set.
const port = parseInt(process.env.PORT || '8443')

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    host: 'localhost',
    port,
    strictPort: true,
  },
  preview: {
    host: 'localhost',
    port,
  },
})
