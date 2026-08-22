# Betriebsvereinbarung

**zur Einführung und Nutzung des Systems „SecureGuard Enterprise" auf
Honeywell-CT45P-Geräten zur Ortung und Überwachung von Betriebsvermögen**

zwischen

**[Name der Gesellschaft / des Arbeitgebers]**, vertreten durch [Name, Funktion]
(im Folgenden: **Arbeitgeber**)

und

**dem Betriebsrat**, vertreten durch [Name der Vorsitzende/n]
(im Folgenden: **Betriebsrat**)

wird gemäß § 87 Abs. 1 Nr. 6, Nr. 1 lit. b, Nr. 2 und Nr. 4 BetrVG folgende
Vereinbarung getroffen.

> ⚠️ **Hinweis:** Dieses Dokument ist eine **Blaupause/Vorlage** für den
> Pilotbetrieb. Es entfaltet erst dann Rechtswirkung, wenn es von beiden
> Seiten unterzeichnet und im Unternehmen wirksam bekannt gegeben wird.

---

## § 1 Zweck und Gegenstand

1. Der Arbeitgeber setzt das System „SecureGuard Enterprise" (im Folgenden:
   **SecureGuard**) auf enterprisefähigen Mobilcomputern (Honeywell CT45P) ein,
   um **eigenes, gekennzeichnetes Betriebsvermögen** (z. B. E-Scooter,
   E-Bikes, Tablets, Schlüsselanhänger) zu schützen und im Falle von
   Diebstahl oder Verlust zu orten.
2. Gegenstand dieser Vereinbarung sind die Rechte und Pflichten beider Seiten
   bei der Beschäftigung von Mitarbeiterinnen und Mitarbeitern, die das
   SecureGuard-System bedienen, sowie die Verarbeitung personenbezogener
   Daten im Rahmen dieser Nutzung.

## § 2 Geltungsbereich und Nutzungskreis

1. SecureGuard wird ausschließlich durch **beauftragtes Personal** mit
   der in § 5 festgelegten Rollen-Zuordnung (ADMIN, OPERATOR, VIEWER)
   bedient.
2. Die Geräte und die App sind **arbeitszeugen**; eine private Nutzung der
   App durch Mitarbeiter ist nicht vorgesehen.
3. Gesucht und ortet wird **ausschließlich** Vermögen, das in der
   Asset-Datenbank der App als geschützt (Whitelist) angelegt wurde.

## § 3 Verarbeitete Daten

1. Folgende Daten werden lokal auf dem CT45P-Gerät verarbeitet und
   protokolliert:
   - Asset-Daten (Name, Typ, MAC-Adresse, VIN, Status, letzte Position),
   - Detektionen (Kanal, RSSI, Koordinaten, Zeitstempel),
   - ausgeführte Aktionen (Alarm, Licht, Motor, Batterie, Nachricht,
     Position, Neustart, Telemetrie),
   - Audit-Log (Aktion, Ausführender, Zeitstempel),
   - lokale Aktivitäts-Log-Datei (`SecureGuard/Logs/activity_log.txt`).
2. Personenbezogene Daten von **Dritten** werden nicht erhoben, sofern eine
   Einwilligung nicht vorliegt. Externe Crowdsource-Kanäle (Apple/Google
   Find My) werden **nur** für Assets mit gesetztem Einwilligungsschalter
   (`externalAllowed`) und nur nach aktiver Freischaltung in den
   Einstellungen genutzt.
3. Alle Daten verbleiben lokal auf dem Gerät bzw. in der lokalen
   Datenbank; eine Weitergabe an Dritte erfolgt nicht.

## § 4 Datenschutz (DSGVO)

1. Die Verarbeitung erfolgt auf Grundlage von Art. 6 Abs. 1 lit. c (
   Schutz von Betriebsvermögen, Erfüllung von Pflichten) i. V. m.
   § 26 BDSG; soweit erforderlich, wird eine Einwilligung eingeholt.
2. Der Betriebsrat ist vor der Einführung und bei wesentlichen Änderungen
   des Systems beteiligt (§ 80, § 87 Abs. 1 Nr. 6 BetrVG).
3. Daten werden nur so lange aufbewahrt, wie es für den Schutz des
   Vermögens und die interne Nachverfolgbarkeit erforderlich ist.
   Das Audit-Log wird automatisiert bereinigt (Retention, vgl.
   `DatabaseCleanup`).
4. Die Rechte der Beschäftigten auf Auskunft, Berichtigung und Löschung
   (§ 34 BDSG) bleiben unberührt.

## § 5 Rollen und Berechtigungen (RBAC)

| Rolle | Berechtigungen |
|-------|----------------|
| **VIEWER** | Einsicht in Dashboard, Asset-Liste, Karte, Aktivitätsverlauf |
| **OPERATOR** | Suchen starten, Aktionen ausführen, Exporte |
| **ADMIN** | Zusätzlich: Asset-Verwaltung, Agent-Konfiguration, Einstellungen |

Die Rollenzuweisung erfolgt durch den Arbeitgeber nach dem
Bedarfs- und Wirtschaftlichkeitsprinzip und wird im Audit-Log dokumentiert.

## § 6 Mitbestimmung des Betriebsrats

1. Der Betriebsrat wird über jede **Veränderung** des Systems
   (neue Ortungskanäle, neue Aktionen, neue Datenarten) **vor**
   deren Einführung informiert und einbezogen.
2. Unvereinbare Abweichungen von dieser Vereinbarung werden dem
   Betriebsrat unverzüglich mitgeteilt.
3. Die Nutzung von SecureGuard dient **nicht** der Leistungskontrolle
   der Beschäftigten im Sinne des § 87 Abs. 1 Nr. 6 BetrVG.

## § 7 Inkrafttreten und Schlussbestimmungen

1. Diese Vereinbarung tritt mit der Unterzeichnung durch beide Seiten in
   Kraft.
2. Sie gilt unbefristet und kann nur schriftlich geändert oder gekündigt
   werden.
3. Im Übrigen gelten die einschlägigen Vorschriften des BDSG, der DSGVO
   und des BetrVG.

| | |
|---|---|
| Ort, Datum: ______________ | Ort, Datum: ______________ |
| Unterschrift Arbeitgeber | Unterschrift Betriebsrat |
