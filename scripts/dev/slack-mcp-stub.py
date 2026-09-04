#!/usr/bin/env python3
"""QA-Stub für den Slack-MCP-Server (provectus/slack-mcp-server).

Spricht dasselbe MCP-Protokoll wie der echte Go-Server – Streamable HTTP
(`POST /mcp`, `Mcp-Session-Id`, Antworten als `application/json` **oder**
`text/event-stream`) und SSE (`GET /sse` → `endpoint`-Event → `POST /message`) –
antwortet aber aus dem Speicher statt gegen die Slack-API.

Zweck: Entwicklung, Preview und CI auf Rechnern **ohne** Docker/Go-Binary bzw.
ohne Slack-Credentials. Kein Ersatz für den echten Server – es gibt keine
Channels, Threads oder Nutzer aus einem Workspace.

Start:
    python3 scripts/dev/slack-mcp-stub.py                 # 0.0.0.0:13080
    SLACK_MCP_API_KEY=geheim python3 scripts/dev/slack-mcp-stub.py
    SLACK_MCP_STUB_PORT=13080 python3 scripts/dev/slack-mcp-stub.py

Backend dagegen laufen lassen:
    SLACK_MCP_URL=http://127.0.0.1:13080/mcp SLACK_NOTIFY_ENABLED=true \\
      uvicorn --app-dir backend main:app --port 8000

Inspektion der „geposteten" Nachrichten:  GET /stub/messages
"""
from __future__ import annotations

import json
import os
import queue
import uuid
from typing import Any, Dict, List, Optional

import uvicorn
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, Response, StreamingResponse

PROTOCOL_VERSION = "2024-11-05"
SERVER_INFO = {"name": "slack-mcp-server-stub", "version": "0.0.0-qa"}
API_KEY = os.environ.get("SLACK_MCP_API_KEY", "").strip()
PORT = int(os.environ.get("SLACK_MCP_STUB_PORT", "13080"))
HOST = os.environ.get("SLACK_MCP_STUB_HOST", "0.0.0.0")

CHANNELS_CSV = (
    "id,name,topic,purpose,memberCount\n"
    "C0001,#general,Allgemein,Firmenweit,42\n"
    "C0002,#secureguard-alerts,Alarme,SecureGuard-Alarme,7\n"
    "C0003,#ops-leitstand,Betrieb,Leitstand,5\n"
)

USERS_CSV = (
    "userID,userName,realName\n"
    "U0001,anna,Anna Beispiel\n"
    "U0002,ben,Ben Muster\n"
)

TOOLS: List[Dict[str, Any]] = [
    {
        "name": "channels_list",
        "description": "Get list of channels (Stub: statisches Verzeichnis)",
        "inputSchema": {
            "type": "object",
            "properties": {"channel_types": {"type": "string"}, "limit": {"type": "number"}},
        },
    },
    {
        "name": "conversations_add_message",
        "description": "Add a message to a channel (Stub: legt sie unter /stub/messages ab)",
        "inputSchema": {
            "type": "object",
            "properties": {
                "channel_id": {"type": "string"},
                "payload": {"type": "string"},
                "thread_ts": {"type": "string"},
                "content_type": {"type": "string"},
            },
            "required": ["channel_id", "payload"],
        },
    },
    {
        "name": "conversations_history",
        "description": "Get messages from a channel (Stub: letzte gepostete Meldungen)",
        "inputSchema": {
            "type": "object",
            "properties": {"channel_id": {"type": "string"}, "limit": {"type": "string"}},
            "required": ["channel_id"],
        },
    },
    {
        "name": "users_search",
        "description": "Search users (Stub: statische Liste)",
        "inputSchema": {"type": "object", "properties": {"user": {"type": "string"}}},
    },
]

# „Slack-Seite" des Stubs: gepostete Nachrichten + Session-Registry
POSTED: List[Dict[str, Any]] = []
SESSIONS: Dict[str, Dict[str, Any]] = {}
SSE_QUEUES: Dict[str, "queue.Queue[str]"] = {}

app = FastAPI(title="Slack-MCP Stub (SecureGuard QA)", version="0.0.0")


def _auth_ok(request: Request) -> bool:
    if not API_KEY:
        return True
    header = request.headers.get("authorization", "")
    token = header.removeprefix("Bearer ").strip()
    return token == API_KEY


def _tool_text(result: List[str]) -> Dict[str, Any]:
    return {
        "content": [{"type": "text", "text": "\n".join(result)}],
        "isError": False,
    }


def _tool_error(message: str) -> Dict[str, Any]:
    return {"content": [{"type": "text", "text": message}], "isError": True}


def handle_tool(name: str, arguments: Dict[str, Any]) -> Dict[str, Any]:
    """Führt ein Tool gegen den Stub-Zustand aus."""
    if name == "channels_list":
        return _tool_text([CHANNELS_CSV.strip()])
    if name == "users_search":
        return _tool_text([USERS_CSV.strip()])
    if name == "conversations_add_message":
        channel = str(arguments.get("channel_id", ""))
        payload = str(arguments.get("payload", ""))
        if not channel or not payload:
            return _tool_error("invalid_arguments: channel_id und payload nötig")
        POSTED.append(
            {
                "ts": f"{1700000000 + len(POSTED)}.000100",
                "channel_id": channel,
                "text": payload,
                "content_type": arguments.get("content_type", "text/markdown"),
                "thread_ts": arguments.get("thread_ts"),
            }
        )
        return _tool_text([f"Message posted to channel {channel}"])
    if name == "conversations_history":
        channel = str(arguments.get("channel_id", ""))
        rows = [
            f"{m['ts']},{m['channel_id']},{m['text'].splitlines()[0][:60]}"
            for m in POSTED
            if m["channel_id"] == channel or channel.startswith("#")
        ]
        return _tool_text(["ts,channel,text"] + (rows or ["(keine Nachrichten im Stub)"]))
    return _tool_error(f"unknown tool: {name}")


def jsonrpc_response(request_id: Any, result: Any) -> Dict[str, Any]:
    return {"jsonrpc": "2.0", "id": request_id, "result": result}


def jsonrpc_error(request_id: Any, code: int, message: str) -> Dict[str, Any]:
    return {"jsonrpc": "2.0", "id": request_id, "error": {"code": code, "message": message}}


def dispatch(body: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    """JSON-RPC-Dispatch; None = Notification (202 ohne Body)."""
    method = body.get("method")
    request_id = body.get("id")
    params = body.get("params") or {}

    if method == "initialize":
        return jsonrpc_response(
            request_id,
            {
                "protocolVersion": PROTOCOL_VERSION,
                "capabilities": {"tools": {"listChanged": False}, "resources": {}},
                "serverInfo": SERVER_INFO,
            },
        )
    if method is None or method.startswith("notifications/"):
        return None
    if method == "tools/list":
        return jsonrpc_response(request_id, {"tools": TOOLS, "nextCursor": ""})
    if method == "tools/call":
        return jsonrpc_response(
            request_id, handle_tool(str(params.get("name", "")), params.get("arguments") or {})
        )
    if method == "ping":
        return jsonrpc_response(request_id, {})
    return jsonrpc_error(request_id, -32601, f"method not found: {method}")


# ============ Streamable HTTP (Transport "http") ============

@app.post("/mcp")
async def mcp_post(request: Request):
    if not _auth_ok(request):
        return JSONResponse(status_code=401, content={"error": "unauthorized"})
    body = await request.json()
    response = dispatch(body)
    if response is None:
        return Response(status_code=202)
    headers = {}
    if body.get("method") == "initialize":
        session_id = uuid.uuid4().hex[:12]
        SESSIONS[session_id] = {"initialized": True}
        headers["Mcp-Session-Id"] = session_id
    # Deterministisch beide Antwortmodi bedienen, die Streamable HTTP erlaubt:
    # tools/call → text/event-stream, alles andere → application/json.
    # Der Backend-Client (backend/slack_mcp.py) muss beide parsen können.
    if body.get("method") == "tools/call":
        payload = f"event: message\ndata: {json.dumps(response)}\n\n"
        return Response(content=payload, media_type="text/event-stream", headers=headers)
    return JSONResponse(content=response, headers=headers)


# ============ SSE (Transport "sse") ============

@app.get("/sse")
async def sse_stream(request: Request):
    if not _auth_ok(request):
        return JSONResponse(status_code=401, content={"error": "unauthorized"})
    session_id = uuid.uuid4().hex[:12]
    events: "queue.Queue[str]" = queue.Queue()
    SSE_QUEUES[session_id] = events

    def stream():
        yield f"event: endpoint\ndata: /message?sessionId={session_id}\n\n"
        while True:
            try:
                chunk = events.get(timeout=30)
            except queue.Empty:
                yield ": keep-alive\n\n"
                continue
            if chunk == "__close__":
                return
            yield f"event: message\ndata: {chunk}\n\n"

    return StreamingResponse(stream(), media_type="text/event-stream")


@app.post("/message")
async def sse_message(request: Request, sessionId: str = ""):
    if not _auth_ok(request):
        return JSONResponse(status_code=401, content={"error": "unauthorized"})
    events = SSE_QUEUES.get(sessionId)
    if events is None:
        return Response(status_code=404)
    body = await request.json()
    response = dispatch(body)
    if response is not None:
        events.put(json.dumps(response))
    return Response(status_code=202)


# ============ Stub-Inspektion ============

@app.get("/stub/messages")
def stub_messages():
    return {"count": len(POSTED), "messages": POSTED}


@app.post("/stub/reset")
def stub_reset():
    POSTED.clear()
    return {"status": "ok", "count": 0}


@app.get("/stub/health")
def stub_health():
    return {"stub": True, "server": SERVER_INFO, "posted": len(POSTED)}


if __name__ == "__main__":
    print(f"Slack-MCP-Stub auf http://{HOST}:{PORT} (MCP: /mcp, SSE: /sse)")
    print(f"API-Key: {'gesetzt' if API_KEY else 'nicht gesetzt'}")
    uvicorn.run(app, host=HOST, port=PORT, log_level="info")
