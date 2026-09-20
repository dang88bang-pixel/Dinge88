# SecureGuard Enterprise – Produktions-Keystore & Secret-Setup

Für das automatische Signieren von offiziellen Release-Builds und Tags (GitHub Actions)
wurde ein dedizierter, langlebiger RSA-2048-Keystore erzeugt.

---

## 1. Keystore-Metadaten

| Eigenschaft | Wert |
|---|---|
| **Dateiname** | `secureguard-release.p12` |
| **Typ / Format** | PKCS#12 (`.p12`) |
| **Schlüssel-Algorithmus** | RSA 2048 Bit |
| **Gültigkeit** | 30 Jahre (bis September 2056) |
| **Alias** | `secureguard-release` |
| **Passwort (Store & Key)** | `SecureGuardProd2026!KeyPass` |
| **Subject / DN** | `CN=SecureGuard Enterprise, OU=Release, O=SecureGuard, L=Berlin, ST=Berlin, C=DE` |
| **Zertifikat-SHA256** | `31:30:5E:72:DE:3D:0B:5E:3B:AF:C8:A7:34:09:00:7A:E5:9C:DB:C6:03:9B:78:ED:E5:94:93:A3:9C:67:17:3D` |

---

## 2. In GitHub Secrets eintragen

Gehe in deinem GitHub-Repository auf:  
**Settings** → **Secrets and variables** → **Actions** → **Repository secrets**

Trage die folgenden 3 Secrets ein:

### Secret 1: `KEYSTORE_BASE64` (oder `ANDROID_KEYSTORE_BASE64`)
Kopiere diesen exakten Base64-Block hinein (exakt 3.792 Zeichen):

```text
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
```

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
Sobald diese Secrets eingetragen sind, baut CI automatisch mit `SIGNING_MODE=production-keystore` statt `ci-debug-fallback`.
