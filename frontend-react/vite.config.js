import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    historyApiFallback: true,
    proxy: {
      // Reenvía las llamadas al API Gateway de Spring (localhost:8080).
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
