import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,        // binds to 0.0.0.0, reachable from LAN
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
