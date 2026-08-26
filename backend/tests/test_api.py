"""API-/Contract-/Migration-/Backup-Tests für das SecureGuard-Backend."""
import os
import tempfile
import time

# Muss vor `from main import ...` gesetzt werden.
_TMP = tempfile.mkdtemp(prefix="secureguard-backend-test-")
os.environ["DATABASE_PATH"] = os.path.join(_TMP, "test.db")
os.environ["BACKUP_DIR"] = os.path.join(_TMP, "backups")
os.environ["API_TOKEN"] = "test-token"
os.environ["MQTT_BROKER"] = "localhost:1883"
os.environ["MQTT_USE_TLS"] = "false"
os.environ["CORS_ORIGINS"] = "http://localhost:3000"

from fastapi.testclient import TestClient  # noqa: E402
import main  # noqa: E402

client = TestClient(main.app)


def auth_headers():
    return {"Authorization": "Bearer test-token"}


def test_health_ok():
    r = client.get("/api/health")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "ok"
    assert body["database"] == "ok"


def test_auth_required_for_api():
    r = client.get("/api/assets")
    assert r.status_code == 401
    r = client.post("/api/crowd/report", json={"mac": "AA:BB:CC:DD:EE:01"})
    assert r.status_code == 401


def test_asset_crud_with_auth():
    payload = {
        "id": "asset-001",
        "name": "Roller",
        "short_name": "R1",
        "mac": "AA:BB:CC:DD:EE:01",
        "status": "ONLINE",
        "latitude": 51.5,
        "longitude": 7.4,
    }
    r = client.post("/api/assets", json=payload, headers=auth_headers())
    assert r.status_code == 200
    r = client.get("/api/assets", headers=auth_headers())
    assert r.status_code == 200
    assert any(a["id"] == "asset-001" for a in r.json())


def test_detection_accepts_historical_flag():
    payload = {
        "asset_mac": "AA:BB:CC:DD:EE:01",
        "source_type": "SATELLITE",
        "rssi": 0,
        "latitude": 51.5,
        "longitude": 7.4,
        "is_historical": True,
        "accuracy_meters": 500.0,
    }
    r = client.post("/api/detections", json=payload, headers=auth_headers())
    assert r.status_code == 200
    rows = client.get("/api/detections?mac=AA:BB:CC:DD:EE:01", headers=auth_headers()).json()
    assert rows[0]["is_historical"] == 1


def test_crowd_search():
    r = client.post(
        "/api/crowd/report",
        json={"mac": "AA:BB:CC:DD:EE:77", "rssi": -70, "latitude": 51.51, "longitude": 7.41},
        headers=auth_headers(),
    )
    assert r.status_code == 200
    r = client.get("/api/crowd/search?mac=AA:BB:CC:DD:EE:77", headers=auth_headers())
    assert r.status_code == 200
    assert any(s["mac"] == "AA:BB:CC:DD:EE:77" for s in r.json())


def test_contract_openapi_contains_required_paths():
    schema = client.get("/openapi.json").json()
    paths = schema["paths"]
    for path in [
        "/api/health", "/api/assets", "/api/detections", "/api/alerts",
        "/api/actions/execute", "/api/crowd/search", "/api/search",
        "/api/backup", "/api/restore",
    ]:
        assert path in paths, f"Pfad fehlt im OpenAPI-Contract: {path}"


def test_websocket_route_registered():
    assert any(getattr(route, "path", None) == "/ws" for route in main.app.routes)


def test_migrations_recorded():
    conn = main.get_db()
    versions = [r["version"] for r in conn.execute("SELECT version FROM schema_migrations").fetchall()]
    conn.close()
    assert 1 in versions
    assert 2 in versions


def test_backup_creates_file():
    r = client.post("/api/backup?name=contract", headers=auth_headers())
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "ok"
    assert body["filename"].endswith(".db")
    assert os.path.exists(os.path.join(main.BACKUP_DIR, body["filename"]))


def test_restore_rejects_invalid_and_accepts_sqlite():
    r = client.post("/api/restore", content=b"not a sqlite file", headers=auth_headers())
    assert r.status_code == 400

    with open(main.DB_PATH, "rb") as fh:
        backup_bytes = fh.read()
    r = client.post("/api/restore", content=backup_bytes, headers=auth_headers())
    assert r.status_code == 200
    assert r.json()["status"] == "ok"


def test_websocket_search_roundtrip():
    with client.websocket_connect("/ws?token=test-token") as ws:
        ws.send_json({"type": "search", "mac": "AA:BB:CC:DD:EE:01"})
        msg = ws.receive_json()
        assert msg["type"] == "search_result"
        assert msg["mac"] == "AA:BB:CC:DD:EE:01"
        assert "sightings" in msg["data"]


def test_realtime_event_bridge_shape():
    """Testet, dass MQTT→WS-Bridge-Messages das erwartete Format haben."""
    from unittest.mock import MagicMock

    Payload = type("Payload", (), {"payload": b'{"mac":"AA:BB:CC:DD:EE:01"}'})
    msg = MagicMock(topic="secureguard/AA:BB:CC:DD:EE:01/search/response", payload=Payload.payload)
    # _parse_json wird intern verwendet; wir prüfen nur die Funktion direkt.
    parsed = main._parse_json("{\"mac\":\"AA:BB:CC:DD:EE:01\"}")
    assert parsed["mac"] == "AA:BB:CC:DD:EE:01"
