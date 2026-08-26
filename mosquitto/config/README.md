# Mosquitto-Konfiguration

| Datei | Zweck |
|-------|-------|
| `mosquitto.conf` | Entwicklungs-/Pilot-Broker (anonym, Port 1883 + 9001), läuft in `docker compose` |
| `mosquitto-tls.conf.example` | Produktionsvorlage mit Passwort-Datei, ACL und TLS-Port 8883 |
| `passwd` | ⚠️ Datei NICHT committen; mit `mosquitto_passwd -c` erzeugen |
| `acl` | ⚠️ ACL-Regeln NICHT committen, falls identifizierbar |

## Lokal mit Benutzer/Passwort

```bash
mkdir -p mosquitto/config
docker run --rm -v "$PWD/mosquitto/config:/config" eclipse-mosquitto:2.0 \
  mosquitto_passwd -c /config/passwd secureguard
```

Passwort-Datei erzeugen, dann `allow_anonymous false` in `mosquitto.conf` setzen.
Das Passwort gehört in `.env` (`MQTT_USERNAME`, `MQTT_PASSWORD`) und in `local.properties`.

## TLS (Produktion)

1. Zertifikate nach `mosquitto/certs/` legen (nicht in Git).
2. `mosquitto-tls.conf.example` nach `mosquitto.conf` kopieren.
3. In `docker-compose.yml` den Ordner `./mosquitto/certs` in den Container mounten.
4. In `local.properties` setzen: `MQTT_BROKER_URL=mqtts://host:8883`, `MQTT_TLS=true`.
5. Backend: `MQTT_BROKER=host:8883`, `MQTT_USE_TLS=true`, `MQTT_TLS_CA=/pfad/ca.crt`.

## Themen-Schema

```
secureguard/<device>/telemetry      # ESP32/Asset-Telemetrie
secureguard/<device>/alert          # Alarme
secureguard/<device>/status         # Online-Status
secureguard/<device>/command        # App → Asset
secureguard/<device>/search/response # Antwort auf Suchanfrage
secureguard/search/request          # App → alle Gateways (Suchanfrage)
secureguard/broadcast               # Broadcast-Nachrichten
```
