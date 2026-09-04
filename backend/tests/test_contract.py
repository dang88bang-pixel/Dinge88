"""
Vollständige Vertragstests der Backend-API.

Ergänzt `test_api.py` (Smoke) um jeden verbleibenden Endpunkt, den
API-Key-Schutz, den WebSocket-Kanal und die Fehlerpfade. Jeder Test bekommt
eine eigene, temporäre SQLite-Datei – die Tests sind damit reihenfolgeunabhängig.

Lauf:
    pip install -r backend/requirements-dev.txt
    pytest backend/tests -q
"""
import os
import sys
from datetime import datetime, timedelta, timezone

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import main  # noqa: E402


@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(main, "DB_PATH", str(tmp_path / "contract.db"))
    monkeypatch.setattr(main, "API_KEY", "")           # ungeschützt, sofern nicht anders gesetzt
    monkeypatch.setattr(main, "publish_command", lambda mac, cmd: True)
    main.init_db()
    with TestClient(main.app) as c:
        yield c


@pytest.fixture()
def secured_client(tmp_path, monkeypatch):
    monkeypatch.setattr(main, "DB_PATH", str(tmp_path / "secured.db"))
    monkeypatch.setattr(main, "API_KEY", "geheim-123")
    main.init_db()
    with TestClient(main.app) as c:
        yield c


def make_asset(client, asset_id="asset-001", mac="DE:AD:B0:28:0B:00", status="ONLINE"):
    body = {
        "id": asset_id, "name": "E-Scooter Innenhafen", "mac": mac,
        "short_name": "SCOOT-01", "status": status,
        "latitude": 51.4344, "longitude": 6.7623, "rssi": -61,
    }
    r = client.post("/api/assets", json=body)
    assert r.status_code == 200, r.text
    return body


# ---------------------------------------------------------------- Assets

def test_assets_list_is_empty_on_fresh_db(client):
    assert client.get("/api/assets").json() == []


def test_asset_is_stored_and_returned_verbatim(client):
    sent = make_asset(client)
    rows = client.get("/api/assets").json()
    assert len(rows) == 1
    got = rows[0]
    assert got["id"] == sent["id"]
    assert got["mac"] == sent["mac"]
    assert got["short_name"] == sent["short_name"]
    assert got["status"] == "ONLINE"
    assert got["rssi"] == -61


def test_asset_upsert_does_not_duplicate(client):
    make_asset(client)
    make_asset(client, status="OFFLINE")
    rows = client.get("/api/assets").json()
    assert len(rows) == 1, "gleiche ID darf keinen zweiten Datensatz erzeugen"
    assert rows[0]["status"] == "OFFLINE"


def test_asset_rejects_incomplete_payload(client):
    r = client.post("/api/assets", json={"id": "x"})
    assert r.status_code == 422


# ------------------------------------------------------------ Detections

def test_detection_roundtrip(client):
    make_asset(client)
    r = client.post("/api/detections", json={
        "asset_mac": "DE:AD:B0:28:0B:00", "source_type": "BLE",
        "node_id": "gw-hafentor", "rssi": -70,
        "latitude": 51.4344, "longitude": 6.7623,
    })
    assert r.status_code == 200 and r.json()["status"] == "ok"
    rows = client.get("/api/detections").json()
    assert len(rows) == 1
    assert rows[0]["asset_mac"] == "DE:AD:B0:28:0B:00"
    assert rows[0]["source_type"] == "BLE"
    assert rows[0]["timestamp"], "Zeitstempel muss serverseitig gesetzt werden"


def test_detections_are_newest_first_and_limited(client):
    base = datetime.now(timezone.utc).replace(tzinfo=None)
    for i in range(5):
        client.post("/api/detections", json={
            "asset_mac": "AA:BB:CC:DD:EE:FF", "source_type": "WIFI", "rssi": -50 - i,
            "timestamp": (base + timedelta(minutes=i)).isoformat(),
        })
    rows = client.get("/api/detections", params={"limit": 3}).json()
    assert len(rows) == 3, "limit muss respektiert werden"
    stamps = [r["timestamp"] for r in rows]
    assert stamps == sorted(stamps, reverse=True), "neueste Detektion zuerst"


# ---------------------------------------------------------------- Alerts

def test_alert_roundtrip_and_unresolved_filter(client):
    make_asset(client)
    for msg in ("Geofence verlassen", "Akku niedrig"):
        r = client.post("/api/alerts", json={
            "asset_id": "asset-001", "type": "GEOFENCE",
            "severity": "CRITICAL", "message": msg,
        })
        assert r.status_code == 200

    all_alerts = client.get("/api/alerts").json()
    assert len(all_alerts) == 2
    assert all(a["resolved"] == 0 for a in all_alerts), "neue Alarme sind offen"

    open_only = client.get("/api/alerts", params={"unresolved_only": True}).json()
    assert len(open_only) == 2


def test_alert_requires_message(client):
    r = client.post("/api/alerts", json={"asset_id": "asset-001"})
    assert r.status_code == 422


# --------------------------------------------------------------- Actions

def test_action_is_queued_and_marked_delivered(client):
    make_asset(client)
    r = client.post("/api/actions/execute",
                    json={"asset_id": "asset-001", "action_type": "ALARM"})
    assert r.status_code == 200
    assert r.json()["status"] == "queued"

    cmds = client.get("/api/commands").json()
    assert len(cmds) == 1
    assert cmds[0]["command"] == "ALARM"
    # Der Hintergrund-Task läuft im TestClient synchron nach der Antwort.
    assert cmds[0]["status"] == "delivered"


def test_action_is_marked_failed_when_transport_is_down(client, monkeypatch):
    make_asset(client)
    monkeypatch.setattr(main, "publish_command", lambda mac, cmd: False)
    client.post("/api/actions/execute",
                json={"asset_id": "asset-001", "action_type": "MOTOR_OFF"})
    cmds = client.get("/api/commands").json()
    assert cmds[0]["status"] == "failed", "fehlgeschlagene Zustellung darf nicht als geliefert gelten"


def test_action_for_unknown_asset_is_recorded_not_lost(client):
    client.post("/api/actions/execute",
                json={"asset_id": "geist-999", "action_type": "LIGHT"})
    cmds = client.get("/api/commands").json()
    assert len(cmds) == 1 and cmds[0]["asset_id"] == "geist-999"


def test_action_with_note_keeps_wire_format(client):
    make_asset(client)
    client.post("/api/actions/execute",
                json={"asset_id": "asset-001", "action_type": "MESSAGE:Bitte abstellen"})
    assert client.get("/api/commands").json()[0]["command"] == "MESSAGE:Bitte abstellen"


def test_commands_limit(client):
    make_asset(client)
    for i in range(6):
        client.post("/api/actions/execute",
                    json={"asset_id": "asset-001", "action_type": f"CMD{i}"})
    assert len(client.get("/api/commands", params={"limit": 2}).json()) == 2


# ----------------------------------------------------------------- Stats

def test_stats_match_the_stored_rows(client):
    make_asset(client)
    client.post("/api/detections", json={"asset_mac": "DE:AD:B0:28:0B:00",
                                         "source_type": "BLE", "rssi": -60})
    client.post("/api/alerts", json={"asset_id": "asset-001", "message": "Test"})
    assert client.get("/api/stats").json() == {"assets": 1, "detections": 1, "alerts": 1}


def test_health_counts_agree_with_stats(client):
    make_asset(client)
    health = client.get("/api/health").json()
    stats = client.get("/api/stats").json()
    assert health["status"] == "ok"
    assert health["service"] == "secureguard-backend"
    for key in ("assets", "detections", "alerts"):
        assert health[key] == stats[key]


def test_health_reports_503_when_database_is_broken(client, monkeypatch, tmp_path):
    # Ein Verzeichnis statt einer Datei: sqlite kann nicht öffnen.
    broken = tmp_path / "nicht-oeffnbar"
    broken.mkdir()
    monkeypatch.setattr(main, "DB_PATH", str(broken))
    r = client.get("/api/health")
    assert r.status_code == 503, "Monitoring muss den Ausfall sehen"
    assert r.json()["status"] == "degraded"


# ------------------------------------------------------------ Crowd-Suche

def test_crowd_report_and_search_roundtrip(client):
    r = client.post("/api/crowd/report", json={
        "mac": "de:ad:b0:28:0b:00", "reporter_id": "app-42",
        "rssi": -77, "latitude": 51.43, "longitude": 6.76,
    })
    assert r.json()["status"] == "ok"

    hits = client.get("/api/crowd/search", params={"mac": "DE:AD:B0:28:0B:00"}).json()
    assert len(hits) == 1, "frisch gemeldete Sichtung muss sofort auffindbar sein (UTC-Falle)"
    assert hits[0]["mac"] == "DE:AD:B0:28:0B:00", "MAC wird normalisiert gespeichert"
    assert hits[0]["reporter_id"] == "app-42"


def test_crowd_search_is_case_insensitive(client):
    client.post("/api/crowd/report", json={"mac": "AA:BB:CC:DD:EE:FF"})
    assert len(client.get("/api/crowd/search", params={"mac": "aa:bb:cc:dd:ee:ff"}).json()) == 1


def test_crowd_report_without_mac_is_rejected(client):
    assert client.post("/api/crowd/report", json={"rssi": -60}).json()["status"] == "error"


def test_crowd_search_respects_the_time_window(client):
    client.post("/api/crowd/report", json={"mac": "AA:BB:CC:DD:EE:FF"})
    # Negatives Fenster ⇒ Grenze liegt in der Zukunft ⇒ kein Treffer.
    assert client.get("/api/crowd/search",
                      params={"mac": "AA:BB:CC:DD:EE:FF", "hours": -1}).json() == []


def test_crowd_search_for_unknown_mac_is_empty(client):
    assert client.get("/api/crowd/search", params={"mac": "00:00:00:00:00:00"}).json() == []


# ------------------------------------------------------- MCP / Temp-Mail

def test_otp_flow_end_to_end(client):
    inbox = client.post("/api/mcp/create_inbox", json={"prefix": "qa"}).json()
    assert inbox["email"].startswith("qa-")
    assert inbox["email"].endswith("@temp.secureguard.local")
    assert inbox["token"] and inbox["inboxId"]

    r = client.post("/api/mcp/inject_message", json={
        "token": inbox["token"], "subject": "Ihr Code",
        "body": "Ihr Bestätigungscode lautet 481529. Gültig 10 Minuten.",
    })
    assert r.json() == {"status": "ok", "count": 1}

    otp = client.get("/api/mcp/wait_for_otp",
                     params={"token": inbox["token"], "timeout": 2}).json()
    assert otp["success"] is True
    assert otp["otp"] == "481529"
    assert otp["email"] == inbox["email"]


def test_magic_link_is_extracted_and_trimmed(client):
    inbox = client.post("/api/mcp/create_inbox").json()
    client.post("/api/mcp/inject_message", json={
        "token": inbox["token"],
        "body": "Anmelden: https://ops.secureguard.local/login?t=abc123.",
    })
    res = client.get("/api/mcp/extract_magic_link", params={"token": inbox["token"]}).json()
    assert res["success"] is True
    assert res["magicLink"] == "https://ops.secureguard.local/login?t=abc123"


def test_otp_times_out_without_message(client):
    inbox = client.post("/api/mcp/create_inbox").json()
    res = client.get("/api/mcp/wait_for_otp",
                     params={"token": inbox["token"], "timeout": 1}).json()
    assert res == {"success": False, "error": "timeout"}


def test_unknown_inbox_token_is_rejected_everywhere(client):
    assert client.get("/api/mcp/wait_for_otp",
                      params={"token": "fake", "timeout": 1}).json()["success"] is False
    assert client.get("/api/mcp/extract_magic_link",
                      params={"token": "fake"}).json()["success"] is False
    assert client.post("/api/mcp/inject_message",
                       json={"token": "fake", "body": "x"}).json()["status"] == "error"


def test_inbox_tokens_are_unique(client):
    tokens = {client.post("/api/mcp/create_inbox").json()["token"] for _ in range(5)}
    assert len(tokens) == 5


# ------------------------------------------------------------- API-Key

def test_write_endpoints_require_the_key_when_configured(secured_client):
    payloads = [
        ("/api/assets", {"id": "a", "name": "n", "mac": "m", "short_name": "s"}),
        ("/api/detections", {"asset_mac": "m", "source_type": "BLE"}),
        ("/api/alerts", {"asset_id": "a", "message": "m"}),
        ("/api/actions/execute", {"asset_id": "a", "action_type": "ALARM"}),
        ("/api/crowd/report", {"mac": "AA:BB:CC:DD:EE:FF"}),
        ("/api/mcp/create_inbox", {}),
    ]
    for path, body in payloads:
        r = secured_client.post(path, json=body)
        assert r.status_code == 401, f"{path} war ohne Schlüssel erreichbar"


def test_wrong_key_is_rejected(secured_client):
    r = secured_client.post("/api/assets", headers={"X-API-Key": "falsch"},
                            json={"id": "a", "name": "n", "mac": "m", "short_name": "s"})
    assert r.status_code == 401


def test_correct_key_is_accepted(secured_client):
    r = secured_client.post("/api/assets", headers={"X-API-Key": "geheim-123"},
                            json={"id": "a", "name": "n", "mac": "m", "short_name": "s"})
    assert r.status_code == 200


def test_read_endpoints_stay_open(secured_client):
    for path in ("/api/health", "/api/assets", "/api/detections",
                 "/api/alerts", "/api/commands", "/api/stats"):
        assert secured_client.get(path).status_code == 200, path


# ----------------------------------------------------------- WebSocket

def test_websocket_echoes_unknown_messages(client):
    with client.websocket_connect("/ws") as ws:
        ws.send_json({"type": "hello", "v": 1})
        assert ws.receive_json() == {"type": "echo", "data": {"type": "hello", "v": 1}}


def test_websocket_acknowledges_commands(client):
    make_asset(client)
    with client.websocket_connect("/ws") as ws:
        ws.send_json({"type": "command", "assetId": "asset-001", "action": "ALARM"})
        ack = ws.receive_json()
    assert ack == {"type": "ack", "assetId": "asset-001", "action": "ALARM", "delivered": True}


def test_websocket_reports_failed_delivery(client, monkeypatch):
    monkeypatch.setattr(main, "publish_command", lambda mac, cmd: False)
    with client.websocket_connect("/ws") as ws:
        ws.send_json({"type": "command", "assetId": "asset-001", "action": "RESTART"})
        assert ws.receive_json()["delivered"] is False


def test_websocket_survives_invalid_json(client):
    with client.websocket_connect("/ws") as ws:
        ws.send_text("kein json")
        assert ws.receive_json() == {"type": "error", "message": "invalid json"}
        # Verbindung muss danach weiter nutzbar sein
        ws.send_json({"ping": True})
        assert ws.receive_json()["type"] == "echo"


def test_websocket_registry_is_cleaned_up(client):
    before = len(main.active_websockets)
    with client.websocket_connect("/ws") as ws:
        ws.send_json({"ping": 1})
        ws.receive_json()
        assert len(main.active_websockets) == before + 1
    assert len(main.active_websockets) == before, "getrennte Sockets müssen entfernt werden"
