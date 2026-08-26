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
./gradlew :app:assembleRelease
```

CI: Secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
(siehe `.github/workflows/build-release.yml`) – Werte vom Anwender.

## Network Security

| Build | Cleartext |
|-------|-----------|
| `debug` | erlaubt (LAN/Emulator) |
| `release` | **verboten** – HTTPS/WSS/TLS nötig |

MQTT in Release: `ssl://broker:8883` + Broker-Zertifikat.
