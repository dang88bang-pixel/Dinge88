import { defineConfig } from 'vitest/config'

/**
 * Testkonfiguration des 3D Operations Center.
 *
 * `environment: 'jsdom'` erlaubt es, die vollständige HUD-Verdrahtung aus
 * `src/main.js` zu booten und echte Interaktionsketten zu fahren (Klick →
 * Zustand → Zustellung → Rückmeldung). Die WebGL-Szene (`src/scene/world.js`)
 * wird in `test/setup/world.mock.js` ersetzt – jsdom hat keinen WebGL-Kontext,
 * und die Szene ist bewusst so gekapselt, dass die Bedienlogik ohne sie prüfbar
 * bleibt.
 */
export default defineConfig({
  test: {
    environment: 'jsdom',
    globals: false,
    include: ['test/**/*.test.js'],
    testTimeout: 20000,
    hookTimeout: 20000,
    // Die App-Boot-Tests teilen einen Modulzustand (main.js ist ein
    // Einstiegspunkt mit Seiteneffekten) – deshalb Dateien nacheinander.
    fileParallelism: false,
    reporters: 'verbose'
  }
})
