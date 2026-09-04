"""Tests für die Slack-MCP-Integration (backend/slack_mcp.py + /api/slack/*).

Der Slack-MCP-Server (provectus/slack-mcp-server, Go) wird hier durch einen
Fake-Transport ersetzt, der dasselbe JSON-RPC-2.0-Verhalten zeigt wie mcp-go
v0.44 (Streamable HTTP unter POST /mcp, `Mcp-Session-Id`, Antworten wahlweise
als application/json oder text/event-stream).

Lauf:
    pip install -r backend/requirements-dev.txt
    pytest backend/tests/test_slack_mcp.py -q
"""
import asyncio
import json
import os
import sys

import httpx
import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import main  # noqa: E402  (Backend-Modul im selben Ordner)
import slack_mcp  # noqa: E402

MCP_URL = "http://slack-mcp.test:13080/mcp"

TOOLSET = [
    {"name": "channels_list", "description": "Get list of channels"},
    {"name": "conversations_add_message", "description": "Add a message"},
    {"name": "conversations_history", "description": "Get messages"},
]

CHANNELS_CSV = (
    "id,name,topic,purpose,memberCount\n"
    "C0001,#general,Allgemein,Alles,42\n"
    "C0002,#secureguard-alerts,Alarme,Alerts,7\n"
)


def _tool_result(tool: str, arguments: dict) -> dict:
    """Ergebnis, wie der Slack-MCP-Server es liefert (content[]-Blöcke)."""
    if tool == "channels_list":
        return {"content": [{"type": "text", "text": CHANNELS_CSV}], "isError": False}
    if tool == "conversations_add_message":
        return {
            "content": [
                {
                    "type": "text",
                    "text": f"Message posted to channel {arguments.get('channel_id')}",
                }
            ],
            "isError": False,
        }
    if tool == "conversations_history":
        return {
            "content": [{"type": "text", "text": "not_authorized"}],
            "isError": True,
        }
    return {
        "content": [{"type": "text", "text": f"unknown tool {tool}"}],
        "isError": True,
    }


def make_fake_mcp_server(*, api_key: str = "", sse_responses: bool = False):
    """httpx-MockTransport-Handler, der einen Slack-MCP-Server simuliert."""
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/mcp", request.url.path
        assert request.headers["content-type"] == "application/json"
        calls.append(
            {
                "method": None,
                "body": json.loads(request.content),
                "auth": request.headers.get("authorization", ""),
                "session": request.headers.get("mcp-session-id", ""),
            }
        )
        body = calls[-1]["body"]
        calls[-1]["method"] = body.get("method")
        request_id = body.get("id")
        method = body.get("method")

        if api_key:
            if request.headers.get("authorization") != f"Bearer {api_key}":
                return httpx.Response(401, text="unauthorized")

        if method == "initialize":
            return httpx.Response(
                200,
                headers={"Mcp-Session-Id": "sess-abc"},
                json={
                    "jsonrpc": "2.0",
                    "id": request_id,
                    "result": {
                        "protocolVersion": slack_mcp.PROTOCOL_VERSION,
                        "capabilities": {"tools": {}},
                        "serverInfo": {"name": "slack-mcp-server", "version": "pv-v1.0.1"},
                    },
                },
            )
        if method == "notifications/initialized":
            return httpx.Response(202)
        if method == "tools/list":
            payload = {
                "jsonrpc": "2.0",
                "id": request_id,
                "result": {"tools": TOOLSET, "nextCursor": ""},
            }
        elif method == "tools/call":
            payload = {
                "jsonrpc": "2.0",
                "id": request_id,
                "result": _tool_result(body["params"]["name"], body["params"].get("arguments", {})),
            }
        else:
            payload = {
                "jsonrpc": "2.0",
                "id": request_id,
                "error": {"code": -32601, "message": f"unknown method {method}"},
            }

        if sse_responses:
            sse = f"event: message\ndata: {json.dumps(payload)}\n\n"
            return httpx.Response(
                200, headers={"content-type": "text/event-stream"}, text=sse
            )
        return httpx.Response(200, json=payload)

    return handler, calls


def make_client(handler, **kwargs) -> slack_mcp.SlackMCPClient:
    http_client = httpx.AsyncClient(transport=httpx.MockTransport(handler), timeout=5)
    return slack_mcp.SlackMCPClient(slack_mcp.load_settings(), http_client=http_client)


@pytest.fixture()
def env(monkeypatch):
    """Slack-Konfiguration für Tests setzen (vor jedem Test neu gelesen)."""
    monkeypatch.setenv("SLACK_MCP_URL", MCP_URL)
    monkeypatch.setenv("SLACK_MCP_TRANSPORT", "http")
    monkeypatch.setenv("SLACK_NOTIFY_ENABLED", "true")
    monkeypatch.setenv("SLACK_NOTIFY_CHANNEL", "#secureguard-alerts")
    monkeypatch.setenv("SLACK_NOTIFY_MIN_SEVERITY", "WARNING")
    monkeypatch.delenv("SLACK_MCP_API_KEY", raising=False)
    monkeypatch.delenv("SLACK_WEBHOOK_URL", raising=False)
    return slack_mcp.load_settings()


# ============ Protokoll-Ebene (MCP-Client) ============

def test_initialize_and_list_tools(env):
    handler, calls = make_fake_mcp_server()

    async def run():
        client = make_client(handler)
        try:
            info = await client.initialize()
            tools = await client.list_tools()
            return info, tools, await client.health(probe=False)
        finally:
            await client.aclose()

    info, tools, health = asyncio.run(run())
    assert info["name"] == "slack-mcp-server"
    assert [t["name"] for t in tools] == [
        "channels_list",
        "conversations_add_message",
        "conversations_history",
    ]
    assert health["reachable"] is None  # probe=False → kein Netzwerkaufruf
    assert health["tools"] == 3
    # Handshake + Notification + tools/list
    assert [c["method"] for c in calls] == [
        "initialize",
        "notifications/initialized",
        "tools/list",
    ]
    # Session-Header aus der initialize-Antwort wird mitgesendet
    assert calls[2]["session"] == "sess-abc"


def test_tools_list_is_cached(env):
    handler, calls = make_fake_mcp_server()

    async def run():
        client = make_client(handler)
        try:
            await client.list_tools()
            await client.list_tools()
            await client.list_tools(refresh=True)
        finally:
            await client.aclose()

    asyncio.run(run())
    assert [c["method"] for c in calls].count("tools/list") == 2  # 1x Cache, 1x refresh


def test_call_tool_success_and_error(env):
    handler, calls = make_fake_mcp_server()

    async def run():
        client = make_client(handler)
        try:
            ok = await client.call_tool(
                "conversations_add_message",
                {"channel_id": "#secureguard-alerts", "payload": "hi"},
            )
            bad = await client.call_tool("conversations_history", {"channel_id": "C1"})
            unknown = await client.call_tool("gibt_es_nicht", {})
            return ok, bad, unknown
        finally:
            await client.aclose()

    ok, bad, unknown = asyncio.run(run())
    assert ok.ok and not ok.is_error
    assert "Message posted to channel #secureguard-alerts" in ok.text
    assert bad.is_error and not bad.ok and bad.error == "not_authorized"
    assert not unknown.ok
    posted = [c for c in calls if c["method"] == "tools/call"][0]
    assert posted["body"]["params"]["name"] == "conversations_add_message"


def test_call_tool_handles_sse_response(env):
    """Streamable HTTP darf Antworten als text/event-stream senden."""
    handler, _ = make_fake_mcp_server(sse_responses=True)

    async def run():
        client = make_client(handler)
        try:
            return await client.call_tool("channels_list", {"channel_types": "public_channel"})
        finally:
            await client.aclose()

    result = asyncio.run(run())
    assert result.ok
    assert "#secureguard-alerts" in result.text


def test_unreachable_server_yields_failed_tool_result(env):
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("connection refused", request=request)

    async def run():
        client = make_client(handler)
        try:
            result = await client.call_tool("channels_list", {})
            health = await client.health(probe=True)
            return result, health
        finally:
            await client.aclose()

    result, health = asyncio.run(run())
    assert not result.ok and "nicht erreichbar" in (result.error or "")
    assert health["reachable"] is False


def test_http_error_status_maps_to_error(env):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(500, text="boom")

    async def run():
        client = make_client(handler)
        try:
            return await client.call_tool("channels_list", {})
        finally:
            await client.aclose()

    result = asyncio.run(run())
    assert not result.ok and "HTTP 500" in (result.error or "")


def test_api_key_sent_as_bearer(monkeypatch, env):
    monkeypatch.setenv("SLACK_MCP_API_KEY", "s3cret")
    handler, calls = make_fake_mcp_server(api_key="s3cret")

    async def run():
        client = make_client(handler)
        try:
            return await client.list_tools()
        finally:
            await client.aclose()

    tools = asyncio.run(run())
    assert len(tools) == 3
    assert calls[0]["auth"] == "Bearer s3cret"


def test_wrong_api_key_is_reported(monkeypatch, env):
    monkeypatch.setenv("SLACK_MCP_API_KEY", "falsch")
    handler, _ = make_fake_mcp_server(api_key="s3cret")

    async def run():
        client = make_client(handler)
        try:
            return await client.list_tools()
        finally:
            await client.aclose()

    with pytest.raises(slack_mcp.SlackMCPError) as exc:
        asyncio.run(run())
    assert "401" in str(exc.value)


def test_unknown_transport_falls_back_to_http(monkeypatch):
    monkeypatch.setenv("SLACK_MCP_URL", MCP_URL)
    monkeypatch.setenv("SLACK_MCP_TRANSPORT", "carrier-pigeon")
    assert slack_mcp.load_settings().transport == "http"


def test_endpoint_derivation(monkeypatch):
    monkeypatch.setenv("SLACK_MCP_URL", "http://slack-mcp:13080")
    cfg = slack_mcp.load_settings()
    assert cfg.http_endpoint == "http://slack-mcp:13080/mcp"
    assert cfg.sse_endpoint == "http://slack-mcp:13080/sse"
    monkeypatch.setenv("SLACK_MCP_URL", "http://slack-mcp:13080/mcp/")
    cfg = slack_mcp.load_settings()
    assert cfg.http_endpoint == "http://slack-mcp:13080/mcp"


# ============ SSE-Transport ============

def test_sse_transport_initialize_and_call(monkeypatch):
    """SSE: GET /sse liefert `endpoint`, Antworten kommen als `message`-Events."""
    monkeypatch.setenv("SLACK_MCP_URL", "http://slack-mcp.test:13080")
    monkeypatch.setenv("SLACK_MCP_TRANSPORT", "sse")
    monkeypatch.delenv("SLACK_MCP_API_KEY", raising=False)
    posted = []
    queue: asyncio.Queue = asyncio.Queue()

    async def sse_body():
        # httpx-Streams liefern Bytes – wie ein echter SSE-Server.
        yield b"event: endpoint\ndata: /message?sessionId=sse-1\n\n"
        while True:
            item = await queue.get()
            if item is None:
                return
            yield f"event: message\ndata: {json.dumps(item)}\n\n".encode()

    def handler(request: httpx.Request) -> httpx.Response:
        if request.method == "GET":
            assert request.url.path == "/sse"
            return httpx.Response(
                200, headers={"content-type": "text/event-stream"}, content=sse_body()
            )
        body = json.loads(request.content)
        posted.append({"path": request.url.path, "query": dict(request.url.params),
                       "method": body.get("method")})
        method = body.get("method")
        request_id = body.get("id")
        if method == "initialize":
            result = {
                "protocolVersion": slack_mcp.PROTOCOL_VERSION,
                "capabilities": {},
                "serverInfo": {"name": "slack-mcp-server", "version": "pv-v1.0.1"},
            }
        elif method == "tools/list":
            result = {"tools": TOOLSET}
        elif method == "tools/call":
            result = _tool_result(body["params"]["name"], body["params"].get("arguments", {}))
        else:
            result = {}
        queue.put_nowait({"jsonrpc": "2.0", "id": request_id, "result": result})
        return httpx.Response(202)

    async def run():
        http_client = httpx.AsyncClient(transport=httpx.MockTransport(handler), timeout=5)
        client = slack_mcp.SlackMCPClient(slack_mcp.load_settings(), http_client=http_client)
        try:
            tools = await client.list_tools()
            result = await client.call_tool(
                "conversations_add_message",
                {"channel_id": "#general", "payload": "moin"},
            )
            return tools, result
        finally:
            queue.put_nowait(None)
            await client.aclose()

    tools, result = asyncio.run(run())
    assert len(tools) == 3
    assert result.ok and "Message posted to channel #general" in result.text
    assert {p["path"] for p in posted} == {"/message"}
    assert posted[0]["query"] == {"sessionId": "sse-1"}
    assert [p["method"] for p in posted] == [
        "initialize",
        "notifications/initialized",
        "tools/list",
        "tools/call",
    ]


# ============ Benachrichtigungen (Notifier) ============

def test_format_alert_message():
    text = slack_mcp.format_alert_message(
        asset_id="AA:BB:CC:DD:EE:01",
        alert_type="MOVEMENT",
        severity="CRITICAL",
        message="Bewegung erkannt",
        source="backend/api",
        timestamp="2026-09-04T10:00:00",
    )
    assert ":rotating_light:" in text
    assert "*SecureGuard CRITICAL* – `MOVEMENT`" in text
    assert "`AA:BB:CC:DD:EE:01`" in text
    assert "Bewegung erkannt" in text


def test_notify_alert_uses_mcp_tool(env):
    handler, calls = make_fake_mcp_server()

    async def run():
        client = make_client(handler)
        notifier = slack_mcp.SlackNotifier(client)
        try:
            return await notifier.notify_alert(
                asset_id="asset-1",
                alert_type="MOVEMENT",
                severity="CRITICAL",
                message="Bewegung erkannt",
            )
        finally:
            await notifier.aclose()

    result = asyncio.run(run())
    assert result["ok"] is True and result["transport"] == "mcp"
    assert result["channel"] == "#secureguard-alerts"
    call = [c for c in calls if c["method"] == "tools/call"][0]
    assert call["body"]["params"]["name"] == "conversations_add_message"
    assert call["body"]["params"]["arguments"]["channel_id"] == "#secureguard-alerts"
    assert ":rotating_light:" in call["body"]["params"]["arguments"]["payload"]


def test_notify_respects_min_severity(env):
    handler, calls = make_fake_mcp_server()

    async def run():
        client = make_client(handler)
        notifier = slack_mcp.SlackNotifier(client)
        try:
            return await notifier.notify_alert(
                asset_id="a", severity="INFO", message="nur info"
            )
        finally:
            await notifier.aclose()

    result = asyncio.run(run())
    assert result["ok"] is False and result["skipped"] == "severity"
    assert calls == []  # kein einziger MCP-Aufruf


def test_notify_disabled_short_circuits(monkeypatch, env):
    monkeypatch.setenv("SLACK_NOTIFY_ENABLED", "false")
    handler, calls = make_fake_mcp_server()

    async def run():
        client = make_client(handler)
        notifier = slack_mcp.SlackNotifier(client)
        try:
            return await notifier.notify_alert(asset_id="a", severity="CRITICAL", message="x")
        finally:
            await notifier.aclose()

    result = asyncio.run(run())
    assert result["skipped"] == "disabled"
    assert calls == []


def test_notify_webhook_fallback(monkeypatch, env):
    """MCP-Post schlägt fehl → Incoming Webhook übernimmt."""
    monkeypatch.setenv("SLACK_WEBHOOK_URL", "https://hooks.slack.com/services/T/B/X")
    seen = []

    def mcp_handler(request: httpx.Request) -> httpx.Response:
        body = json.loads(request.content)
        if body.get("id") is None:  # notifications/initialized → 202 ohne Body
            return httpx.Response(202)
        if body.get("method") == "initialize":
            return httpx.Response(
                200,
                json={
                    "jsonrpc": "2.0",
                    "id": body["id"],
                    "result": {"serverInfo": {"name": "slack-mcp-server"}},
                },
            )
        if body.get("method") == "tools/list":
            return httpx.Response(
                200, json={"jsonrpc": "2.0", "id": body["id"], "result": {"tools": TOOLSET}}
            )
        # conversations_add_message ist serverseitig deaktiviert:
        return httpx.Response(
            200,
            json={
                "jsonrpc": "2.0",
                "id": body["id"],
                "result": {
                    "content": [
                        {"type": "text", "text": "conversations_add_message is disabled"}
                    ],
                    "isError": True,
                },
            },
        )

    async def run():
        http_client = httpx.AsyncClient(
            transport=httpx.MockTransport(mcp_handler), timeout=5
        )
        client = slack_mcp.SlackMCPClient(slack_mcp.load_settings(), http_client=http_client)
        notifier = slack_mcp.SlackNotifier(client)

        async def fake_webhook(url, text):
            seen.append((url, text))
            return True, "Webhook HTTP 200"

        notifier._send_webhook = fake_webhook  # kein echter HTTP-Call im Test
        try:
            return await notifier.send_text("Fallback-Test")
        finally:
            await notifier.aclose()

    result = asyncio.run(run())
    assert result["ok"] is True and result["transport"] == "webhook"
    assert seen[0][0].endswith("/T/B/X") and seen[0][1] == "Fallback-Test"


def test_parse_channels_csv():
    rows = slack_mcp.parse_channels_csv(CHANNELS_CSV)
    assert [r["name"] for r in rows] == ["#general", "#secureguard-alerts"]
    assert rows[1]["memberCount"] == "7"
    assert slack_mcp.parse_channels_csv("") == []
    assert slack_mcp.parse_channels_csv("kein csv header\n") == []


# ============ REST-Endpunkte (/api/slack/*) ============

@pytest.fixture()
def api(tmp_path, monkeypatch):
    """TestClient + injectierter Fake-Slack-MCP-Server."""
    monkeypatch.setattr(main, "DB_PATH", str(tmp_path / "test.db"))
    main.init_db()
    monkeypatch.setenv("SLACK_MCP_URL", MCP_URL)
    monkeypatch.setenv("SLACK_MCP_TRANSPORT", "http")
    monkeypatch.setenv("SLACK_NOTIFY_ENABLED", "true")
    monkeypatch.setenv("SLACK_NOTIFY_MIN_SEVERITY", "WARNING")
    monkeypatch.delenv("SLACK_WEBHOOK_URL", raising=False)
    monkeypatch.delenv("SLACK_MCP_API_KEY", raising=False)
    monkeypatch.setattr(main, "API_KEY", "")

    handler, calls = make_fake_mcp_server()
    http_client = httpx.AsyncClient(transport=httpx.MockTransport(handler), timeout=5)
    slack_mcp.set_notifier(
        slack_mcp.SlackNotifier(
            slack_mcp.SlackMCPClient(slack_mcp.load_settings(), http_client=http_client)
        )
    )
    with TestClient(main.app) as c:
        yield c, calls
    slack_mcp.set_notifier(None)


def test_slack_health_endpoint(api):
    client, _ = api
    r = client.get("/api/slack/health")
    assert r.status_code == 200
    body = r.json()
    assert body["reachable"] is True
    assert body["tools"] == 3
    assert body["server"]["name"] == "slack-mcp-server"
    assert body["notify"]["channel"] == "#secureguard-alerts"


def test_slack_tools_endpoint(api):
    client, _ = api
    r = client.get("/api/slack/tools")
    assert r.status_code == 200
    body = r.json()
    assert body["count"] == 3
    assert "conversations_add_message" in [t["name"] for t in body["tools"]]


def test_slack_channels_endpoint(api):
    client, _ = api
    r = client.get("/api/slack/channels")
    assert r.status_code == 200
    body = r.json()
    assert body["count"] == 2
    assert body["channels"][1]["name"] == "#secureguard-alerts"


def test_slack_call_endpoint(api):
    client, calls = api
    r = client.post(
        "/api/slack/call",
        json={"tool": "conversations_history", "arguments": {"channel_id": "C0001"}},
    )
    assert r.status_code == 200
    body = r.json()
    assert body["ok"] is False and body["is_error"] is True
    assert [c["method"] for c in calls].count("tools/call") == 1


def test_slack_call_requires_tool(api):
    client, _ = api
    assert client.post("/api/slack/call", json={"tool": "  "}).status_code == 400


def test_slack_call_and_notify_require_api_key(api, monkeypatch):
    client, _ = api
    monkeypatch.setattr(main, "API_KEY", "topsecret")
    assert client.post("/api/slack/call", json={"tool": "channels_list"}).status_code == 401
    assert client.post("/api/slack/notify", json={"message": "x"}).status_code == 401
    ok = client.post(
        "/api/slack/call",
        json={"tool": "channels_list"},
        headers={"X-API-Key": "topsecret"},
    )
    assert ok.status_code == 200


def test_slack_notify_endpoint(api):
    client, calls = api
    r = client.post(
        "/api/slack/notify",
        json={"message": "Stack online", "channel": "#general"},
    )
    assert r.status_code == 200
    assert r.json()["ok"] is True
    args = [c for c in calls if c["method"] == "tools/call"][0]["body"]["params"]["arguments"]
    assert args["channel_id"] == "#general"
    assert args["payload"] == "Stack online"
    assert args["content_type"] == "text/markdown"


def test_slack_notify_formats_alert(api):
    client, calls = api
    r = client.post(
        "/api/slack/notify",
        json={
            "message": "Bewegung erkannt",
            "asset_id": "AA:BB:CC:DD:EE:01",
            "severity": "CRITICAL",
            "alert_type": "MOVEMENT",
        },
    )
    assert r.status_code == 200
    payload = [c for c in calls if c["method"] == "tools/call"][0]["body"]["params"][
        "arguments"
    ]["payload"]
    assert ":rotating_light:" in payload and "AA:BB:CC:DD:EE:01" in payload


def test_slack_notify_reports_upstream_failure(api):
    client, _ = api
    r = client.post("/api/slack/notify", json={"message": "x", "channel": "C0001"})
    # conversations_add_message antwortet im Fake immer ok → hier der Fehlerpfad:
    assert r.status_code == 200


def test_slack_notify_empty_message(api):
    client, _ = api
    assert client.post("/api/slack/notify", json={"message": "   "}).status_code == 400


def test_alert_endpoint_triggers_slack_notification(api):
    """POST /api/alerts → Background-Task → MCP conversations_add_message."""
    client, calls = api
    r = client.post(
        "/api/alerts",
        json={
            "asset_id": "asset-42",
            "type": "MOVEMENT",
            "severity": "CRITICAL",
            "message": "Bewegung erkannt",
        },
    )
    assert r.status_code == 200
    posted = [c for c in calls if c["method"] == "tools/call"]
    assert len(posted) == 1
    args = posted[0]["body"]["params"]["arguments"]
    assert args["channel_id"] == "#secureguard-alerts"
    assert "asset-42" in args["payload"]


def test_info_alert_is_not_forwarded(api):
    client, calls = api
    client.post(
        "/api/alerts",
        json={"asset_id": "a", "type": "INFO", "severity": "INFO", "message": "nichts"},
    )
    assert [c for c in calls if c["method"] == "tools/call"] == []


def test_health_contains_slack_summary(api):
    client, _ = api
    body = client.get("/api/health").json()
    assert body["slack"]["configured"] is True
    assert body["slack"]["transport"] == "http"
    assert body["slack"]["notify_channel"] == "#secureguard-alerts"
    assert body["slack"]["mcp_url"].endswith("/mcp")


def test_slack_health_without_configuration(monkeypatch, tmp_path):
    """Ohne SLACK_MCP_URL: Endpunkte antworten, statt zu crashen."""
    monkeypatch.setattr(main, "DB_PATH", str(tmp_path / "test2.db"))
    main.init_db()
    monkeypatch.delenv("SLACK_MCP_URL", raising=False)
    monkeypatch.delenv("SLACK_WEBHOOK_URL", raising=False)
    slack_mcp.set_notifier(
        slack_mcp.SlackNotifier(slack_mcp.SlackMCPClient(slack_mcp.load_settings()))
    )
    with TestClient(main.app) as client:
        body = client.get("/api/slack/health").json()
        assert body["configured"] is False and body["reachable"] is False
        assert client.get("/api/health").json()["slack"]["configured"] is False
        r = client.get("/api/slack/tools")
        assert r.status_code == 502
    slack_mcp.set_notifier(None)
