"""
Echtzeit- und Aktionsketten des Backends (Vertragstests).

Abgedeckt:
  * WebSocket-Kanal: Kommando (UI → Gateway) mit ack, Echo, Fehlerpfad
  * REST-Mutation → WebSocket-Broadcast (asset_update / telemetry / alert)
    – die App erwartet `{"type": …, "data": {…}}` (WebSocketService.kt:74-79)
  * Aktion → MQTT-Publish an `secureguard/<MAC>/command` + Command-Historie

Lauf:  pytest backend/tests/test_realtime_chains.py -q
"""
import asyncio
import json
import os
import sys
import threading
import time

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import main  # noqa: E402  (Backend-Modul im selben Ordner)


@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(main, "DB_PATH", str(tmp_path / "test.db"))
    main.init_db()
    with TestClient(main.app) as c:
        yield c


class FakeWebSocket:
    """Minimaler WS-Sink: nimmt Broadcasts entgegen, ohne Socket/Timeout-Risiko."""

    def __init__(self):
        self.sent = []

    async def send_text(self, text):
        self.sent.append(json.loads(text))


def wait_for_broadcast(fake, timeout=2.0):
    end = time.time() + timeout
    while time.time() < end:
        if fake.sent:
            return fake.sent[0]
        time.sleep(0.02)
    return None


# ---------------------------------------------------------------- WS-Kanal ---

def test_websocket_command_publishes_and_acks(client, monkeypatch):
    published = []
    monkeypatch.setattr(main, "publish_command",
                        lambda mac, action: published.append((mac, action)) or True)
    with client.websocket_connect("/ws") as ws:
        ws.send_text(json.dumps({"type": "command", "assetId": "AA:BB:CC:DD:EE:01",
                                 "action": "ALARM"}))
        ack = ws.receive_json()
    assert ack["type"] == "ack"
    assert ack["action"] == "ALARM"
    assert ack["delivered"] is True
    assert published == [("AA:BB:CC:DD:EE:01", "ALARM")]


def test_websocket_echo_and_invalid_json(client):
    with client.websocket_connect("/ws") as ws:
        ws.send_text(json.dumps({"type": "ping"}))
        assert ws.receive_json()["type"] == "echo"
        ws.send_text("das ist kein json")
        assert ws.receive_json()["type"] == "error"


# ------------------------------------------- REST-Mutation → WS-Broadcast ---

def test_asset_upsert_broadcasts_asset_update(client):
    fake = FakeWebSocket()
    main.active_websockets.add(fake)
    try:
        r = client.post("/api/assets", json={
            "id": "asset-ws-1", "name": "Roller WS", "mac": "AA:BB:CC:DD:EE:77",
            "short_name": "RWS", "status": "ONLINE",
        })
        assert r.status_code == 200
        msg = wait_for_broadcast(fake)
    finally:
        main.active_websockets.discard(fake)
    assert msg is not None, "kein Broadcast nach POST /api/assets"
    assert msg["type"] == "asset_update"
    assert msg["data"]["mac"] == "AA:BB:CC:DD:EE:77"
    assert msg["data"]["status"] == "ONLINE"


def test_detection_broadcasts_telemetry(client):
    fake = FakeWebSocket()
    main.active_websockets.add(fake)
    try:
        r = client.post("/api/detections", json={
            "asset_mac": "AA:BB:CC:DD:EE:77", "source_type": "MQTT", "rssi": -61,
        })
        assert r.status_code == 200
        msg = wait_for_broadcast(fake)
    finally:
        main.active_websockets.discard(fake)
    assert msg is not None, "kein Broadcast nach POST /api/detections"
    assert msg["type"] == "telemetry"
    assert msg["data"]["asset_mac"] == "AA:BB:CC:DD:EE:77"
    assert msg["data"]["rssi"] == -61


def test_alert_broadcasts_alert_event(client, monkeypatch):
    async def _no_slack(*args, **kwargs):
        return {"ok": False, "error": "slack deaktiviert (Test)"}

    monkeypatch.setattr(main, "notify_slack_alert", _no_slack)
    fake = FakeWebSocket()
    main.active_websockets.add(fake)
    try:
        r = client.post("/api/alerts", json={
            "asset_id": "AA:BB:CC:DD:EE:77", "type": "MOVEMENT",
            "severity": "CRITICAL", "message": "Bewegung im Lager",
        })
        assert r.status_code == 200
        msg = wait_for_broadcast(fake)
    finally:
        main.active_websockets.discard(fake)
    assert msg is not None, "kein Broadcast nach POST /api/alerts"
    assert msg["type"] == "alert"
    assert msg["data"]["severity"] == "CRITICAL"


def test_broadcast_without_loop_is_safe(client, monkeypatch):
    """Kein Crash, wenn keine Event-Loop läuft (z. B. CLI-/Skript-Kontext)."""
    monkeypatch.setattr(main, "main_event_loop", None)
    main.broadcast_event("asset_update", {"id": "x"})  # darf nicht werfen


# ------------------------------------------------- Aktion → MQTT-Kette ------

def test_action_execute_publishes_mqtt_and_records_history(client, monkeypatch):
    published = []
    monkeypatch.setattr(main, "publish_command",
                        lambda mac, action: published.append((mac, action)) or True)
    client.post("/api/assets", json={"id": "AA:BB:CC:DD:EE:01", "name": "Roller #1",
                                     "mac": "AA:BB:CC:DD:EE:01", "status": "ONLINE"})
    r = client.post("/api/actions/execute",
                    json={"asset_id": "AA:BB:CC:DD:EE:01", "action_type": "ALARM"})
    assert r.status_code == 200
    assert r.json()["status"] == "queued"

    # Background-Task der TestClient-Session abarbeiten lassen
    end = time.time() + 2.0
    while time.time() < end and not published:
        time.sleep(0.02)
    assert published, "kein MQTT-Publish für die Aktion"
    assert published[0] == ("AA:BB:CC:DD:EE:01", "ALARM")

    cmds = client.get("/api/commands").json()
    assert any(c["command"] == "ALARM" and c["status"] == "delivered" for c in cmds)


# ------------------------------------- MQTT-Reconnect → Re-Subscribe ---------

class FakeMqttClient:
    """paho-Double: zeichnet subscribe() auf und liefert die Callbacks zurück."""

    def __init__(self, connected=True):
        self.subscribed = []
        self.on_connect = None
        self.on_disconnect = None
        self.on_message = None
        self._connected = connected

    def subscribe(self, topic, qos=0):
        self.subscribed.append((topic, qos))

    def is_connected(self):
        return self._connected


def test_subscriber_resubscribes_on_every_connect(monkeypatch):
    monkeypatch.setattr(main, "MQTT_RESUBSCRIBE_INTERVAL", 0)
    fake = FakeMqttClient(connected=False)     # CONNACK kommt erst später
    monkeypatch.setattr(main, "get_mqtt_client", lambda: fake)
    main._mqtt_subscribed = False

    main.start_mqtt_subscriber()
    assert fake.subscribed == [], "vor dem CONNACK darf noch nichts abonniert sein"
    assert callable(fake.on_connect) and callable(fake.on_disconnect)

    fake.on_connect(fake, None, {}, 0)         # erster Verbindungsaufbau
    first = list(fake.subscribed)
    assert main._mqtt_subscribed is True
    assert len(first) == len(main.MQTT_SUBSCRIPTIONS)

    fake.on_disconnect(fake, None, 7)          # Broker weg
    assert main._mqtt_subscribed is False

    fake.on_connect(fake, None, {}, 0)         # Reconnect → erneut abonnieren
    assert len(fake.subscribed) == 2 * len(main.MQTT_SUBSCRIPTIONS), \
        "nach einem Reconnect müssen die Topics erneut abonniert werden"


def test_subscriber_rejects_failed_connect(monkeypatch):
    monkeypatch.setattr(main, "MQTT_RESUBSCRIBE_INTERVAL", 0)
    fake = FakeMqttClient(connected=False)
    monkeypatch.setattr(main, "get_mqtt_client", lambda: fake)
    main._mqtt_subscribed = False
    main.start_mqtt_subscriber()
    fake.on_connect(fake, None, {}, 5)         # rc != 0 → nicht autorisiert
    assert fake.subscribed == []
    assert main._mqtt_subscribed is False


def test_subscriber_without_paho_is_safe(monkeypatch):
    monkeypatch.setattr(main, "get_mqtt_client", lambda: None)
    main.start_mqtt_subscriber()               # darf nicht werfen


def test_watchdog_resubscribes_while_connected(monkeypatch):
    """Selbstheilung: Der Watchdog abonniert erneut, solange die Verbindung lebt."""
    fake = FakeMqttClient(connected=True)
    main._mqtt_subscribed = False
    assert main.mqtt_resubscribe_once(fake) is True
    assert len(fake.subscribed) == len(main.MQTT_SUBSCRIPTIONS)
    assert main._mqtt_subscribed is True
    # idempotent: ein zweiter Durchlauf verdoppelt nur die Aufrufe, nicht die Topics
    assert main.mqtt_resubscribe_once(fake) is True
    assert len(fake.subscribed) == 2 * len(main.MQTT_SUBSCRIPTIONS)


def test_watchdog_skips_when_disconnected(monkeypatch):
    fake = FakeMqttClient(connected=False)
    main._mqtt_subscribed = True
    assert main.mqtt_resubscribe_once(fake) is False
    assert fake.subscribed == []
    assert main._mqtt_subscribed is False


def test_watchdog_survives_broker_errors(monkeypatch):
    class BrokenMqtt(FakeMqttClient):
        def subscribe(self, topic, qos=0):
            raise RuntimeError("broker weg")

    fake = BrokenMqtt(connected=True)
    assert main.mqtt_resubscribe_once(fake) is False
    assert main._mqtt_subscribed is False


def test_subscriber_starts_watchdog_thread(monkeypatch):
    # Alt-Threads aus früheren Tests beenden, damit die Prüfung eindeutig ist.
    if main._mqtt_watchdog_stop is not None:
        main._mqtt_watchdog_stop.set()
    for t in [t for t in threading.enumerate()
              if t.name == "mqtt-resubscribe-watchdog"]:
        t.join(timeout=3)

    before = {t.ident for t in threading.enumerate()}
    fake = FakeMqttClient(connected=True)
    monkeypatch.setattr(main, "get_mqtt_client", lambda: fake)
    monkeypatch.setattr(main, "MQTT_RESUBSCRIBE_INTERVAL", 1)
    main._mqtt_watchdog_stop = None

    main.start_mqtt_subscriber()
    assert main._mqtt_watchdog_stop is not None

    started = [t for t in threading.enumerate()
               if t.ident not in before and t.name == "mqtt-resubscribe-watchdog"]
    assert started, "Watchdog-Thread wurde nicht gestartet"

    main._mqtt_watchdog_stop.set()
    started[0].join(timeout=3)
    assert not started[0].is_alive(), "Watchdog-Thread beendet sich nicht"
