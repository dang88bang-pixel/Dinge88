# Betriebsvereinbarung zum Einsatz von „SecureGuard Enterprise"

**zwischen** dem Unternehmen (nachfolgend „Arbeitgeber")
**und** dem Betriebsrat / der Personalvertretung (nachfolgend „Betriebsrat")

Diese Vereinbarung regelt den Einsatz des Systems „SecureGuard Enterprise"
zum Schutz und zur Wiederbeschaffung mobiler Unternehmenswerte (E-Scooter,
Fahrräder, Schlüsselfinder, Tablets u. ä.) auf dienstlich genutzten Geräten.

---

## § 1 Zweck und Geltungsbereich

1. Das System dient ausschließlich der Ortung, Überwachung und Wiederbeschaffung
   **dienstlicher Assets**, die im System als Whitelist erfasst sind.
2. Eine Ortung oder Überwachung von Mitarbeiterinnen und Mitarbeitern ist
   **nicht** Zweck des Systems und ist unzulässig. Private Geräte werden nicht
   erfasst.
3. Die Vereinbarung gilt für alle Geräte, auf denen die App betrieben wird.

## § 2 Betroffene Daten

1. Es werden verarbeitet: Gerätekennungen (MAC-Adressen), Signalstärken
   (RSSI), Positionsdaten der Assets, Telemetriedaten (Batterie, Motorstatus,
   Betriebsstunden), Zeitstempel sowie Protokolldaten (Audit-Log).
2. Positionsdaten von **Mitarbeiter-Endgeräten** (GPS-Referenz des Suchgeräts)
   werden nur zur unmittelbaren Suchunterstützung genutzt und automatisch nach
   der konfigurierten Aufbewahrungsfrist (Standard 30 Tage) gelöscht.
3. Eine Personenbeziehbarkeit von Asset-Positionsdaten wird durch
   Pseudonymisierung (Anzeige nur über Asset-Namen) minimiert.

## § 3 Datenspeicherung und -löschung

1. Alle Daten werden **lokal auf dem Gerät** in einer verschlüsselten
   Datenbank (AES/GCM, Android Keystore) gespeichert.
2. Retentionsfristen (Standard): Detektionen 30 Tage, Alarme 90 Tage,
   Audit-Log 365 Tage. Die Löschung erfolgt automatisch durch den
   Wartungs-Worker.
3. Backups und Exporte (CSV/PDF) erfolgen ausschließlich durch berechtigte
   Rollen (siehe § 5) und werden protokolliert.

## § 4 Externe Quellen und Datenübermittlung

1. Externe Quellen (Crowdsourcing-Netzwerke, Satelliten-/API-Dienste,
   Partner-Infrastruktur) sind **standardmäßig deaktiviert**.
2. Ihre Aktivierung erfolgt pro Asset durch ausdrückliche Einwilligung
   (Schalter „Externe Quellen erlauben"). In Crowd-Netzwerke werden ausschließlich
   **SHA-256-Hashes** von Gerätekennungen übermittelt – keine Klartext-Daten.
3. Übermittlungen an externe Dienste werden im Audit-Log protokolliert.

## § 5 Rollen und Berechtigungen (RBAC)

| Rolle | Rechte |
|---|---|
| ADMIN | Vollzugriff inkl. Konfiguration, Nutzer- und Löschverwaltung |
| MANAGER | Alle Assets sehen/bearbeiten, Aktionen ausführen, Logs einsehen |
| OPERATOR | Assets sehen, Aktionen für zugewiesene Assets ausführen |
| VIEWER | Assets nur lesen |

Auf Einzelgeräten läuft die App standardmäßig im ADMIN-Kontext; die
Rollenprüfung ist technisch aktiv (RoleManager) und greift bei jeder Aktion.

## § 6 Mitbestimmung und Transparenz

1. Vor jeder Aktivierung neuer externer Quellen erfolgt eine Anhörung des
   Betriebsrats.
2. Der Arbeitgeber stellt dem Betriebsrat auf Verlangen Einblick in Audit-Log
   und Konfiguration (Exportfunktion) zur Verfügung.
3. Jede/r Beschäftigte kann über den Status-Screen einsehen, welche Kanäle
   aktiv sind und welche Berechtigungen erteilt wurden.

## § 7 Schutzmaßnahmen

- App-Sperre per PIN (PBKDF2-Hash, Auto-Lock nach Inaktivität, Sperre nach
  Fehlversuchen)
- Ende-zu-Ende-Verschlüsselung lokaler Exporte (AES/GCM)
- Transportverschlüsselung für MQTT/WebSocket (TLS-fähige Endpunkte)
- Nachvollziehbarkeit aller sicherheitsrelevanten Handlungen im Audit-Log

## § 8 Laufzeit und Inkrafttreten

1. Die Vereinbarung tritt mit Unterzeichnung in Kraft und wird bei
   wesentlichen Änderungen des Systems erneut verhandelt.
2. Sie kann mit einer Frist von vier Wochen gekündigt werden; anschließend ist
   der Systembetrieb einzustellen.

---

| Ort, Datum | Arbeitgeber | Betriebsrat |
|---|---|---|
| ____________ | ____________ | ____________ |

> **Hinweis:** Diese Vereinbarung ist eine **Blaupause** für Pilotprojekte und
> vor Einsatz rechtsfachlich zu prüfen (Betriebsverfassungsgesetz, DSGVO Art. 26,
> § 26 BDSG). Sie wird im Repository hinterlegt, um dem Transparenzgebot (§ 6)
> zu entsprechen.
