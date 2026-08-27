"""
Smoke-/Vertragstests für die SecureGuard-Backend-API.

Lauf (lokal oder CI):
    pip install -r backend/requirements-dev.txt
    pytest backend/tests -q
"""
import os
import sys

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import main  # noqa: E402  (Backend-Modul im selben Ordner)


@pytest.fixture()
def client(tmp_path, monkeypatch):
    # Isolierte, temporäre SQLite-DB pro Test
    monkeypatch.setattr(main, "DB_PATH", str(tmp_path / "test.db"))
    main.init_db()
    with TestClient(main.app) as c:
        yield c


def test_health_ok(client):
    r = client.get("/api/health")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "ok"
    for key in ("assets", "detections", "alerts"):
        assert key in body


def test_asset_crud_roundtrip(client):
    asset = {
        "id": "asset-test-1",
        "name": "E-Scooter Test",
        "mac": "AA:BB:CC:DD:EE:FF",
        "short_name": "Test",
        "status": "ONLINE",
    }
    r = client.post("/api/assets", json=asset)
    assert r.status_code == 200
    r = client.get("/api/assets")
    assert any(a["id"] == "asset-test-1" for a in r.json())


def test_crowd_report_and_search(client):
    report = {
        "mac": "aa:bb:cc:dd:ee:01",
        "rssi": -70,
        "latitude": 52.52,
        "longitude": 13.405,
    }
    r = client.post("/api/crowd/report", json=report)
    assert r.status_code == 200 and r.json()["status"] == "ok"
    r = client.get("/api/crowd/search", params={"mac": "AA:BB:CC:DD:EE:01"})
    assert r.status_code == 200
    sightings = r.json()
    assert len(sightings) == 1
    # MAC muss normalisiert (upper) gespeichert worden sein
    assert sightings[0]["mac"] == "AA:BB:CC:DD:EE:01"


def test_detections_roundtrip(client):
    det = {
        "asset_mac": "AA:BB:CC:DD:EE:02",
        "source_type": "BLE",
        "rssi": -55,
    }
    assert client.post("/api/detections", json=det).status_code == 200
    r = client.get("/api/detections")
    assert any(d["asset_mac"] == "AA:BB:CC:DD:EE:02" for d in r.json())


def test_alert_roundtrip(client):
    alert = {"asset_id": "asset-test-1", "type": "SECURITY", "message": "Testalarm"}
    assert client.post("/api/alerts", json=alert).status_code == 200
    r = client.get("/api/alerts", params={"unresolved_only": True})
    assert any(a["message"] == "Testalarm" for a in r.json())


def test_publish_command_normalizes_mac(monkeypatch):
    """Topic-MAC muss wie in der App GROSSGESCHRIEBEN sein (case-sensitiv!)."""
    captured = {}

    class FakeClient:
        def publish(self, topic, payload, qos=0):
            captured["topic"] = topic
            captured["payload"] = payload
            return object()

    monkeypatch.setattr(main, "get_mqtt_client", lambda: FakeClient())
    ok = main.publish_command("aa:bb:cc:dd:ee:99", "ALARM")
    assert ok is True
    assert captured["topic"] == "secureguard/AA:BB:CC:DD:EE:99/command"
    assert captured["payload"] == "ALARM"


def test_health_degraded_returns_503(tmp_path, monkeypatch):
    """Defekte DB → 503 (nicht 200), damit Monitoring echte Probleme sieht."""
    monkeypatch.setattr(main, "DB_PATH", "/proc/definitiv/nicht/schreibbar/x.db")
    with TestClient(main.app) as c:
        r = c.get("/api/health")
    assert r.status_code == 503
    assert r.json()["status"] == "degraded"
