"""Tests für /api/system/dependencies – die Abhängigkeits-Inventur, die das
App-Einstellungsmenü unter „Anbindungen & Abhängigkeiten" anzeigt.

Lauf:
    pytest backend/tests/test_dependencies.py -q
"""
import os
import sys

import httpx
import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import main  # noqa: E402
import slack_mcp  # noqa: E402
from tests.test_slack_mcp import MCP_URL, make_fake_mcp_server  # noqa: E402


@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(main, "DB_PATH", str(tmp_path / "dep.db"))
    main.init_db()
    monkeypatch.setattr(main, "MQTT_BROKER", "mqtt:1883")
    monkeypatch.setattr(main, "MQTT_USERNAME", "secureguard")
    monkeypatch.setattr(main, "_mqtt_client", None)
    monkeypatch.setattr(main, "NODERED_URL", "http://nodered.test:1880")
    monkeypatch.setenv("SLACK_MCP_URL", MCP_URL)
    monkeypatch.setenv("SLACK_MCP_TRANSPORT", "http")
    monkeypatch.setenv("SLACK_NOTIFY_CHANNEL", "#secureguard-alerts")
    monkeypatch.setenv("SLACK_NOTIFY_MIN_SEVERITY", "WARNING")
    monkeypatch.setenv("SLACK_WEBHOOK_URL", "https://hooks.slack.com/services/T/B/X")
    slack_mcp.set_notifier(None)
    with TestClient(main.app) as c:
        yield c
    slack_mcp.set_notifier(None)


def _dep(body: dict, dep_id: str) -> dict:
    for dep in body["dependencies"]:
        if dep["id"] == dep_id:
            return dep
    raise AssertionError(f"{dep_id} fehlt in {[d['id'] for d in body['dependencies']]}")


def test_lists_all_server_dependencies(client):
    r = client.get("/api/system/dependencies?probe=false")
    assert r.status_code == 200
    body = r.json()
    assert body["count"] == 5 == len(body["dependencies"])
    assert [d["id"] for d in body["dependencies"]] == [
        "database",
        "mqtt",
        "slack-mcp",
        "slack-webhook",
        "nodered",
    ]
    # Jeder Eintrag trägt dieselben Felder (die App rendert daraus eine Zeile)
    for dep in body["dependencies"]:
        assert {"id", "name", "kind", "configured", "reachable", "target", "detail"} <= set(dep)


def test_database_entry_reports_counts(client):
    client.post(
        "/api/assets",
        json={"id": "a1", "name": "Scooter", "mac": "AA:BB:CC:DD:EE:01", "short_name": "S1"},
    )
    dep = _dep(client.get("/api/system/dependencies?probe=false").json(), "database")
    assert dep["reachable"] is True
    assert "assets=1" in dep["detail"]
    assert dep["target"].endswith("dep.db")


def test_mqtt_entry_shows_broker_and_auth(client):
    dep = _dep(client.get("/api/system/dependencies?probe=false").json(), "mqtt")
    assert dep["target"] == "mqtt:1883"
    assert dep["reachable"] is False      # kein Client in der Testumgebung
    assert "Auth: ja" in dep["detail"]


def test_slack_entry_uses_live_mcp_probe(client, monkeypatch):
    handler, calls = make_fake_mcp_server()
    http_client = httpx.AsyncClient(transport=httpx.MockTransport(handler), timeout=5)
    slack_mcp.set_notifier(
        slack_mcp.SlackNotifier(
            slack_mcp.SlackMCPClient(slack_mcp.load_settings(), http_client=http_client)
        )
    )
    dep = _dep(client.get("/api/system/dependencies").json(), "slack-mcp")
    assert dep["configured"] is True
    assert dep["reachable"] is True
    assert dep["target"].endswith("/mcp")
    assert "3 Tools" in dep["detail"]
    assert "#secureguard-alerts" in dep["detail"]
    assert [c["method"] for c in calls][0] == "initialize"


def test_slack_webhook_entry_never_probes(client):
    dep = _dep(client.get("/api/system/dependencies").json(), "slack-webhook")
    assert dep["configured"] is True
    assert dep["reachable"] is None       # kein Probe-Post an den Webhook
    assert dep["target"] == "gesetzt"


def test_nodered_probe_reports_failure(client):
    dep = _dep(client.get("/api/system/dependencies").json(), "nodered")
    assert dep["configured"] is True
    assert dep["target"] == "http://nodered.test:1880"
    # Host existiert nicht → Probe schlägt fehl, darf aber nicht crashen
    assert dep["reachable"] in (False, None)


def test_probe_false_skips_network_checks(client):
    body = client.get("/api/system/dependencies?probe=false").json()
    assert body["probed"] is False
    nodered = _dep(body, "nodered")
    assert nodered["reachable"] is None
    assert "Probe übersprungen" in nodered["detail"]
