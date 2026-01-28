import { defineConfig } from 'vite'
import solid from 'vite-plugin-solid'

const apiUrl = process.env.API_URL || 'http://localhost:8080'

export default defineConfig({
  plugins: [solid()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: apiUrl,
        changeOrigin: true,
      },
      '/ws': {
        target: apiUrl,
        ws: true,
      },
    },
  },
})
