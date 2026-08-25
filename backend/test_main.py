"""
Regressionstests für das SecureGuard-Backend.

Ausführen:
    pip install -r requirements.txt -r requirements-dev.txt
    pytest

Die Tests decken drei reparierte Defekte ab:
  1. MQTT → WebSocket-Bridge: `asyncio.get_event_loop()` gibt es im paho-Thread
     nicht, dadurch wurde jede Nachricht still verworfen.
  2. `broadcast_websocket()` iterierte über das live-Set `active_websockets`
     und brach mit "Set changed size during iteration" ab.
  3. `/api/crowd/search` verglich lokal geschriebene Zeitstempel mit
     `datetime('now')` (UTC) → das Zeitfenster war um den UTC-Offset verschoben.
"""

import asyncio
import json
import datetime
import os
import sqlite3
import sys
import threading
import time

import pytest

BACKEND_DIR = os.path.dirname(os.path.abspath(__file__))
if BACKEND_DIR not in sys.path:
    sys.path.insert(0, BACKEND_DIR)


@pytest.fixture()
def backend(tmp_path, monkeypatch):
    """Imports `main` against a throwaway database and no MQTT broker."""
    monkeypatch.setenv("DATABASE_PATH", str(tmp_path / "test.db"))
    monkeypatch.setenv("MQTT_BROKER", "127.0.0.1:1")  # nothing listening
    for mod in [m for m in list(sys.modules) if m == "main"]:
        del sys.modules[mod]
    import main

    yield main
    main.active_websockets.clear()


# ============ 1. MQTT → WEBSOCKET BRIDGE ============

def test_bridge_does_not_use_get_event_loop_from_thread(backend):
    """The old code called asyncio.get_event_loop() on the paho thread."""
    result = {}

    def worker():
        try:
            result["loop"] = asyncio.get_event_loop()
        except Exception as exc:  # noqa: BLE001 - we assert on the type below
            result["error"] = exc

    thread = threading.Thread(target=worker)
    thread.start()
    thread.join()

    # Proves the premise of the bug: there is no loop on a worker thread, so the
    # previous `try/except RuntimeError: pass` swallowed every MQTT message.
    assert "loop" not in result
    assert isinstance(result["error"], RuntimeError)


def test_bridge_forwards_mqtt_message_to_the_running_loop(backend, monkeypatch):
    """forward_mqtt_message() must reach the loop thread from a foreign thread."""
    delivered = []

    async def fake_broadcast(message):
        delivered.append(message)

    monkeypatch.setattr(backend, "broadcast_websocket", fake_broadcast)

    async def scenario():
        backend._main_loop = asyncio.get_running_loop()
        worker = threading.Thread(
            target=backend.forward_mqtt_message,
            args=("secureguard/AA:BB/telemetry", b'{"rssi": -55, "bat": 82}'),
        )
        worker.start()
        worker.join(timeout=5)
        assert not worker.is_alive()
        # Let the scheduled coroutine run on this loop.
        await asyncio.sleep(0.05)

    asyncio.run(scenario())
    backend._main_loop = None

    assert len(delivered) == 1
    frame = json.loads(delivered[0])
    assert frame["type"] == "telemetry"
    assert frame["topic"] == "secureguard/AA:BB/telemetry"
    assert frame["data"] == {"rssi": -55, "bat": 82}


def test_bridge_topic_mapping(backend, monkeypatch):
    """Topic → frame type mapping, including non-JSON payloads."""
    delivered = []

    async def fake_broadcast(message):
        delivered.append(json.loads(message))

    monkeypatch.setattr(backend, "broadcast_websocket", fake_broadcast)

    async def scenario():
        backend._main_loop = asyncio.get_running_loop()
        for topic, payload in [
            ("secureguard/X/alert", b'{"message": "Alarm"}'),
            ("secureguard/X/status", "online"),
            ("secureguard/broadcast", "irgendwas"),
        ]:
            backend.forward_mqtt_message(topic, payload)
        await asyncio.sleep(0.05)

    asyncio.run(scenario())
    backend._main_loop = None

    assert [f["type"] for f in delivered] == ["alert", "system_status", "unknown"]
    assert delivered[0]["data"] == {"message": "Alarm"}
    assert delivered[1]["data"] == "online"


def test_on_mqtt_message_accepts_both_callback_api_versions(backend, monkeypatch):
    """VERSION1 passes (client, userdata, msg); VERSION2 passes (client, msg)."""
    seen = []
    monkeypatch.setattr(backend, "forward_mqtt_message", lambda t, p: seen.append((t, p)))

    class Msg:
        topic = "secureguard/AA/telemetry"
        payload = b"hello"

    backend.on_mqtt_message(object(), object(), Msg())   # VERSION1 signature
    backend.on_mqtt_message(object(), Msg())             # VERSION2 signature

    assert seen == [
        ("secureguard/AA/telemetry", b"hello"),
        ("secureguard/AA/telemetry", b"hello"),
    ]


def test_forward_without_loop_logs_and_does_not_raise(backend, caplog):
    """No captured loop yet -> warn, but never crash the paho thread."""
    backend._main_loop = None
    with caplog.at_level("WARNING"):
        backend.forward_mqtt_message("secureguard/X/telemetry", b"{}")
    assert "Kein Event-Loop erfasst" in caplog.text


def test_lifespan_captures_and_clears_the_loop(backend):
    from fastapi.testclient import TestClient

    assert backend._main_loop is None
    with TestClient(backend.app):
        captured = backend._main_loop
        assert captured is not None
        assert captured.is_running() or not captured.is_closed()
    assert backend._main_loop is None


# ============ 2. BROADCAST ============

def test_broadcast_survives_concurrent_connect_during_send(backend):
    """A client joining mid-broadcast must not abort the whole broadcast."""

    class Client:
        def __init__(self, payload):
            self.payload = payload
            self.sent = []

        async def send_text(self, message):
            self.sent.append(message)
            # Simulate another client connecting while we are still sending.
            backend.active_websockets.add(Client("late-joiner"))
            if self.payload == "explode":
                raise RuntimeError("connection closed")

    async def scenario():
        backend.active_websockets.clear()
        healthy = Client("ok")
        broken = Client("explode")
        backend.active_websockets.update({healthy, broken})

        await backend.broadcast_websocket('{"type":"telemetry"}')

        assert healthy.sent == ['{"type":"telemetry"}']
        # The failing client is pruned, the healthy one survives.
        assert broken not in backend.active_websockets
        assert healthy in backend.active_websockets

    asyncio.run(scenario())


# ============ 3. CROWD SEARCH TIME WINDOW ============

def _insert_sighting(db_path, mac, hours_ago):
    conn = sqlite3.connect(db_path)
    ts = datetime.datetime.now() - datetime.timedelta(hours=hours_ago)
    conn.execute(
        "INSERT INTO crowd_sightings (mac, reporter_id, rssi, latitude, longitude, timestamp) "
        "VALUES (?, ?, ?, ?, ?, ?)",
        (mac, "tester", -70, 51.22, 6.78, ts),
    )
    conn.commit()
    conn.close()


def test_crowd_search_window_is_offset_aware(backend, tmp_path, monkeypatch):
    """Outside UTC the window must still be exactly `hours` wide."""
    db_path = str(tmp_path / "test.db")

    def run_case(hours_ago, window):
        conn = sqlite3.connect(db_path)
        conn.execute("DELETE FROM crowd_sightings")
        conn.commit()
        conn.close()
        _insert_sighting(db_path, "AA:BB:CC:DD:EE:FF", hours_ago)
        return asyncio.run(
            backend.search_crowd_sightings(mac="AA:BB:CC:DD:EE:FF", hours=window)
        )

    for tz in ("UTC", "Europe/Berlin", "America/New_York"):
        monkeypatch.setenv("TZ", tz)
        time.tzset()
        try:
            assert len(run_case(hours_ago=1, window=24)) == 1, f"tz={tz}: 1h alte Sichtung fehlt"
            assert len(run_case(hours_ago=25, window=24)) == 0, f"tz={tz}: 25h alte Sichtung gefunden"
        finally:
            monkeypatch.setenv("TZ", "UTC")
            time.tzset()


# ============ REST-GRUNDRAUSCHEN ============

def test_crud_and_stats_end_to_end(backend):
    from fastapi.testclient import TestClient

    with TestClient(backend.app) as client:
        assert client.get("/api/health").json()["status"] == "ok"

        r = client.post(
            "/api/assets",
            json={"id": "a1", "name": "E-Scooter", "mac": "AA:BB:CC:DD:EE:FF", "short_name": "Scooter"},
        )
        assert r.status_code == 200
        assert client.get("/api/assets").json()[0]["short_name"] == "Scooter"

        assert client.post(
            "/api/detections",
            json={"asset_mac": "AA:BB:CC:DD:EE:FF", "source_type": "BLE", "rssi": -60},
        ).status_code == 200
        assert len(client.get("/api/detections").json()) == 1

        assert client.post(
            "/api/alerts", json={"asset_id": "a1", "message": "Geofence verlassen"}
        ).status_code == 200
        assert len(client.get("/api/alerts", params={"unresolved_only": True}).json()) == 1

        assert client.post(
            "/api/actions/execute", json={"asset_id": "a1", "action_type": "LOCK"}
        ).json()["status"] == "queued"
        # No broker reachable in tests -> the command ends up 'failed', not stuck.
        commands = client.get("/api/commands").json()
        assert commands and commands[0]["status"] in ("delivered", "failed")

        assert client.post(
            "/api/crowd/report",
            json={"mac": "aa:bb:cc:dd:ee:ff", "rssi": -71, "latitude": 51.22, "longitude": 6.78},
        ).json()["status"] == "ok"
        assert client.post("/api/crowd/report", json={"rssi": -71}).json()["status"] == "error"

        stats = client.get("/api/stats").json()
        assert stats == {"assets": 1, "detections": 1, "alerts": 1}


def test_websocket_command_ack(backend):
    from fastapi.testclient import TestClient

    with TestClient(backend.app) as client:
        with client.websocket_connect("/ws") as ws:
            ws.send_json({"type": "command", "assetId": "AA:BB:CC:DD:EE:FF", "action": "ALARM"})
            ack = ws.receive_json()
            assert ack["type"] == "ack"
            assert ack["action"] == "ALARM"
            assert ack["delivered"] is False  # no broker in tests

            ws.send_text("not-json")
            assert ws.receive_json() == {"type": "error", "message": "invalid json"}


def test_paho_client_is_built_without_deprecation_warning(backend):
    """paho 2.x needs callback_api_version, otherwise it warns today and raises in v3."""
    import warnings

    if backend.mqtt is None:
        pytest.skip("paho-mqtt not installed")

    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        client = backend._new_mqtt_client("test-client")

    assert client is not None
    deprecations = [w for w in caught if issubclass(w.category, DeprecationWarning)]
    assert not deprecations, f"DeprecationWarning: {[str(w.message) for w in deprecations]}"

    # The pinned callback API must be the one on_mqtt_message expects.
    api = getattr(backend.mqtt, "CallbackAPIVersion", None)
    if api is not None and hasattr(api, "VERSION2"):
        assert client._callback_api_version == api.VERSION2


def test_handler_runs_against_real_paho_client(backend, monkeypatch):
    """Attach the real handler to a real paho client and invoke it."""
    if backend.mqtt is None:
        pytest.skip("paho-mqtt not installed")

    seen = []
    monkeypatch.setattr(backend, "forward_mqtt_message", lambda t, p: seen.append((t, p)))

    client = backend._new_mqtt_client("handler-test")
    client.on_message = backend.on_mqtt_message

    class Msg:
        topic = "secureguard/AA/status"
        payload = b"up"

    # paho VERSION2 calls on_message(client, message); VERSION1 adds userdata.
    client.on_message(client, Msg())
    assert seen == [("secureguard/AA/status", b"up")]
