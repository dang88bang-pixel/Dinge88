# SecureGuard Enterprise – Produktions-Keystore & Secret-Setup

Für das automatische Signieren von offiziellen Release-Builds und Tags (GitHub Actions)
wurde ein dedizierter, langlebiger RSA-4096-Keystore erzeugt und im Verzeichnis `keystore/` hinterlegt.

---

## 1. Keystore-Metadaten

| Eigenschaft | Wert |
|---|---|
| **Dateiname** | `secureguard-release.p12` |
| **Typ / Format** | PKCS#12 (`.p12`) |
| **Schlüssel-Algorithmus** | RSA 4096 Bit |
| **Gültigkeit** | 30 Jahre (bis September 2056) |
| **Alias** | `secureguard-release` |
| **Passwort (Store & Key)** | `SecureGuardProd2026!KeyPass` |
| **Subject / DN** | `C=DE, ST=Berlin, L=Berlin, O=SecureGuard Enterprise, OU=Mobile Security, CN=SecureGuard Enterprise Release` |
| **Zertifikat-SHA256** | `45:19:EC:AE:C4:5A:0C:F9:41:48:8F:16:8A:D6:1F:68:30:1C:48:85:21:16:94:3B:ED:9C:AD:CF:97:EA:AB:52` |

---

## 2. In GitHub Secrets hinterlegen

Gehe in deinem GitHub-Repository auf:  
**Settings** → **Secrets and variables** → **Actions** → **Repository secrets**

Trage die folgenden 3 Secrets ein:

### Secret 1: `KEYSTORE_BASE64` (oder `ANDROID_KEYSTORE_BASE64`)
Den vollständigen Base64-String aus `keystore/secureguard-release.p12.b64` (eine lange Zeichenkette ohne Zeilenumbrüche) hineinkopieren.

### Secret 2: `KEYSTORE_PASSWORD` (oder `ANDROID_KEYSTORE_PASSWORD`)
```
SecureGuardProd2026!KeyPass
```

### Secret 3: `KEYSTORE_ALIAS` (oder `ANDROID_KEYSTORE_ALIAS`)
```
secureguard-release
```

---

## 3. Was passiert nach dem Eintragen?

1. **Automatische Erkennung**:  
   Der GitHub-Actions-Workflow `.github/workflows/build-release.yml` prüft bei jedem Lauf:
   - Ist `KEYSTORE_BASE64` gültiges Base64?
   - Entspricht die Datei einem gültigen PKCS#12 / JKS Keystore?
   - Stimmen Passwort und Alias überein?
2. **Produktions-Signierung**:  
   Sobald diese Secrets eingetragen sind, baut CI mit `SIGNING_MODE=production-keystore` statt `ci-debug-fallback`.
3. **Tag-Releases**:  
   Tags wie `v1.2.0` werden mit diesem echten Produktionszertifikat v1+v2+v3 signiert und automatisch im GitHub-Release veröffentlicht.
