import { defineConfig } from 'vite'

/**
 * SecureGuard 3D Operations Center – Build-Konfiguration.
 *
 * - `base: './'`  => das Build-Ergebnis läuft auch als `file:///android_asset/...`
 *                    im WebView der Android-App (relative Asset-Pfade).
 * - Proxy `/api`  => leitet an das FastAPI-Backend weiter, wenn es läuft.
 *                    Der Browser spricht immer nur relative URLs an.
 */
export default defineConfig({
  base: './',
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: true,
    allowedHosts: true,
    hmr: { clientPort: 443, protocol: 'wss' },
    proxy: {
      '/api': {
        target: process.env.SECUREGUARD_BACKEND || 'http://127.0.0.1:8000',
        changeOrigin: true,
        secure: false
      },
      // Echtzeit-Ereignisse des Backends. `ws: true` ist zwingend, sonst
      // beantwortet Vite den Upgrade-Request mit 404 und die Konsole faellt
      // still auf Polling zurueck.
      '/ws': {
        target: process.env.SECUREGUARD_BACKEND || 'http://127.0.0.1:8000',
        changeOrigin: true,
        secure: false,
        ws: true
      }
    }
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    target: 'es2020',
    chunkSizeWarningLimit: 1600,
    sourcemap: false
  }
})
