import { defineConfig } from 'vite'

// The exact same build is used in the browser (dev/preview) and inside the
// Android WebView. For the WebView the bundle is loaded from local assets via
// the origin https://ops.secureguard.local/, so assets must be referenced by
// absolute path (base: '/'). No CDN: `three` and its examples are bundled by
// rollup at build time.
export default defineConfig({
  base: '/',
  build: {
    outDir: 'dist',
    target: 'es2019',
    sourcemap: false,
    minify: 'esbuild',
    cssCodeSplit: false,
    chunkSizeWarningLimit: 1600
  }
})
