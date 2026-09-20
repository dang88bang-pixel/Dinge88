# SQLCipher & Release-Signing

## SQLCipher (Room at-rest)

Ab Version mit `net.zetetic:sqlcipher-android`:

1. Beim ersten Start erzeugt `DatabaseKeyManager` eine 256-Bit-Passphrase.
2. Die Passphrase wird mit einem AndroidKeyStore-AES-Key (GCM) verwahrt.
3. Room öffnet die DB über `SupportOpenHelperFactory(passphrase)`.
4. Existiert noch eine **unverschlüsselte** Alt-DB, migriert
   `SqlCipherHelper.migratePlainToEncryptedIfNeeded()` einmalig per
   `sqlcipher_export` und legt `secureguard.db.plain.bak` ab.

### Backup/Restore

- Backups sind **verschlüsselte** SQLCipher-Dateien (kein Klartext-Header).
- Restore nur auf demselben Gerät bzw. mit exportiertem Key – gerätegebunden
  über AndroidKeyStore. Gerätewechsel: vorher CSV/PDF-Export.

### Abschalten (nur Debug/Notfall)

Nicht empfohlen. Falls nötig: Dependency + `.openHelperFactory` entfernen und
DB-Datei löschen (Datenverlust).

## Release-Keystore

**Passwörter legt der Anwender selbst fest** – das Script generiert keine.

```bash
export KEYSTORE_PASSWORD='…dein starkes Passwort…'
export KEY_PASSWORD='…dein Key-Passwort…'   # optional, default = KEYSTORE_PASSWORD
export KEY_ALIAS=secureguard                # optional
./scripts/create-release-keystore.sh
# optional Secrets interaktiv:
./scripts/create-release-keystore.sh --repo OWNER/NAME
```

Erzeugt:

- `app/secureguard-keystore.jks` (gitignored)
- `app/secureguard-keystore.b64` (für `KEYSTORE_BASE64`)
- Hinweise in `local.properties` (ohne Klartext-Passwort)

Lokal:

```bash
export KEYSTORE_PASSWORD=... KEY_ALIAS=secureguard KEY_PASSWORD=...
# Keystore wird automatisch gefunden unter (erste Fundstelle gewinnt):
#   $SECUREGUARD_KEYSTORE, ./secureguard-keystore.p12, ./secureguard-keystore.jks,
#   ./app/secureguard-keystore.p12, ./app/secureguard-keystore.jks
./gradlew :app:assembleRelease
```

### Signatur-Modi (seit 1.2.0)

| Modus | Wann | Keystore | Kennzeichnung |
|-------|------|----------|---------------|
| `production` | Produktions-Keystore gefunden (Secret/Env/Datei) | euer Keystore, Passwörter aus `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` | `BuildConfig.SIGNING_MODE = "production"` |
| `ci-debug-fallback` | kein Produktions-Keystore | `app/keystore/secureguard-ci-debug.p12` (eingecheckt, Alias `androiddebugkey`, Passwort `android`) | `BuildConfig.SIGNING_MODE = "ci-debug-fallback"`, `::warning::` in CI, `signing:` in `BUILD-INFO.txt` |

Der CI-Debug-Keystore ist **bewusst öffentlich** (wie der Android-Debug-Key) und
dient nur dazu, dass jede APK aus der CI – Debug **und** Release – signiert,
installierbar und über vorherige Builds **updatefähig** ist (stabile Signatur
statt eines zufälligen `~/.android/debug.keystore` pro Runner). Er bietet
keinerlei Herkunftsnachweis. Für produktive Auslieferung/MDM daher immer den
Produktions-Keystore hinterlegen; **Tag-Releases (`v*`) schlagen ohne
Produktions-Keystore bewusst fehl**, und der GitHub-Release-Job prüft den Modus.

Ein Wechsel von `ci-debug-fallback` auf `production` ändert die Signatur → auf
Geräten muss die App einmalig deinstalliert werden (Android verweigert Updates
mit anderer Signatur). Danach bleibt sie stabil.

### CI-Secrets

Der Workflow (`.github/workflows/build-release.yml`) akzeptiert **beide**
Namensschemata – das erste gesetzte gewinnt:

| Zweck | Variante A | Variante B (Legacy, `scripts/create-release-keystore.sh`) |
|-------|------------|------------------------------------------------------------|
| Keystore (Base64, JKS oder PKCS12) | `ANDROID_KEYSTORE_BASE64` | `KEYSTORE_BASE64` |
| Keystore-Passwort | `ANDROID_KEYSTORE_PASSWORD` | `KEYSTORE_PASSWORD` |
| Key-Alias | `ANDROID_KEY_ALIAS` | `KEY_ALIAS` |
| Key-Passwort | `ANDROID_KEY_PASSWORD` | `KEY_PASSWORD` |

```bash
gh secret set KEYSTORE_BASE64 --repo OWNER/NAME < app/secureguard-keystore.b64
gh secret set KEYSTORE_PASSWORD --repo OWNER/NAME
gh secret set KEY_ALIAS --repo OWNER/NAME        # z. B. secureguard
gh secret set KEY_PASSWORD --repo OWNER/NAME
```

Die CI verifiziert jede APK mit `apksigner verify` (v2-Signatur Pflicht) und
prüft, dass ein als `production` erwarteter Build **nicht** mit dem
CI-Debug-Zertifikat (SHA-256 `02:8C:8F:5A:…:ED:B0`) signiert wurde.

## Network Security

| Build | Cleartext |
|-------|-----------|
| `debug` | erlaubt (LAN/Emulator) |
| `release` | **verboten** – HTTPS/WSS/TLS nötig |

MQTT in Release: `ssl://broker:8883` + Broker-Zertifikat.
