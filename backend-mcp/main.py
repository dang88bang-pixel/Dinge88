# SecureGuard Enterprise – MCP-Server (Temp-Mail / OTP)
# ------------------------------------------------------
# Minimaler JSON-RPC-über-WebSocket-Server, kompatibel mit dem MCPClient
# der Android-App (app/src/main/java/com/secureguard/enterprise/mcp/MCPClient.kt).
#
# Tools (MCP "tools/call"):
#   - create_inbox        → {"email","token","inboxId"}
#   - wait_for_otp        → {"otp","email","from","subject"}  (Long-Polling)
#   - extract_magic_link  → {"magicLink","email"}
#   - tools/list          → MCP-Standard-Toolverzeichnis
#
# E-Mails werden nicht per SMTP abgeholt, sondern über eine simple REST-API
# injiziert (für Testumgebungen / Node-RED / CI). Der OTP wird per Regex
# aus dem Mail-Body extrahiert (4–8 Ziffern), Magic Links ebenfalls.
#
# Datenschutz: Alles nur im RAM (kein Persistieren), Inboxes laufen nach
# INBOX_TTL_SECONDS ab. NUR für legitime Zwecke (QA/E2E-Testkonten,
# firmeninterne Testregistrierungen) verwenden.
#
# Start:  uvicorn main:app --host 0.0.0.0 --port 8001
# Docker: docker compose up mcp

import asyncio
import json
import logging
import os
import re
import secrets
import time
from typing import Any, Dict, Optional

import uvicorn
from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("secureguard-mcp")

INBOX_TTL_SECONDS = int(os.environ.get("INBOX_TTL_SECONDS", "3600"))
OTP_POLL_INTERVAL = 0.5

app = FastAPI(title="SecureGuard MCP Server", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ============ SPEICHER (nur RAM) ============

# inboxId -> {"email", "token", "created", "mails": [ {from, subject, body, received} ]}
inboxes: Dict[str, Dict[str, Any]] = {}


def prune_expired() -> None:
    now = time.time()
    expired = [k for k, v in inboxes.items() if now - v["created"] > INBOX_TTL_SECONDS]
    for k in expired:
        inboxes.pop(k, None)


def find_inbox(token_or_id: str) -> Optional[Dict[str, Any]]:
    prune_expired()
    inbox = inboxes.get(token_or_id)
    if inbox is None:  # Fallback: per Token suchen
        inbox = next((v for v in inboxes.values() if v["token"] == token_or_id), None)
    return inbox


def extract_otp(body: str) -> Optional[str]:
    match = re.search(r"\b(\d{4,8})\b", body or "")
    return match.group(1) if match else None


def extract_magic_link(body: str) -> Optional[str]:
    match = re.search(r"https?://[^\s<>\"']+", body or "")
    return match.group(0) if match else None


def tool_response(request_id: Any, text_payload: Dict[str, Any]) -> Dict[str, Any]:
    """Baut eine MCP-konforme Tool-Antwort: result.content[0].text = JSON-String."""
    return {
        "jsonrpc": "2.0",
        "id": request_id,
        "result": {"content": [{"type": "text", "text": json.dumps(text_payload)}]},
    }


def error_response(request_id: Any, message: str) -> Dict[str, Any]:
    return {
        "jsonrpc": "2.0",
        "id": request_id,
        "error": {"code": -32603, "message": message},
    }


# ============ WEBSOCKET (JSON-RPC) ============

TOOLS = [
    {"name": "create_inbox", "description": "Erstellt eine temporäre Inbox.",
     "inputSchema": {"type": "object", "properties": {}}},
    {"name": "wait_for_otp", "description": "Wartet auf eine OTP-Mail (Long-Polling).",
     "inputSchema": {"type": "object", "properties": {"token": {"type": "string"}, "timeout": {"type": "number"}}}},
    {"name": "extract_magic_link", "description": "Extrahiert einen Magic Link aus der letzten Mail.",
     "inputSchema": {"type": "object", "properties": {"token": {"type": "string"}}}},
]


async def handle_rpc(message: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    request_id = message.get("id")
    params = message.get("params") or {}
    name = params.get("name")
    args: Dict[str, Any] = params.get("arguments") or {}

    if name == "create_inbox":
        inbox_id = secrets.token_hex(6)
        token = secrets.token_urlsafe(24)
        domain = os.environ.get("MAIL_DOMAIN", "temp.secureguard.local")
        email = f"{inbox_id}@{domain}"
        inboxes[inbox_id] = {
            "email": email, "token": token, "inboxId": inbox_id,
            "created": time.time(), "mails": [],
        }
        logger.info("Inbox erstellt: %s", email)
        return tool_response(request_id, {"email": email, "token": token, "inboxId": inbox_id})

    if name == "wait_for_otp":
        inbox = find_inbox(str(args.get("token", "")))
        if inbox is None:
            return tool_response(request_id, {"otp": None, "error": "Inbox nicht gefunden / abgelaufen"})
        timeout = float(args.get("timeout", 45))
        deadline = time.time() + min(timeout, 120)
        while time.time() < deadline:
            for mail in reversed(inbox["mails"]):
                otp = extract_otp(mail["body"])
                if otp:
                    return tool_response(request_id, {
                        "otp": otp, "email": inbox["email"],
                        "from": mail["from"], "subject": mail["subject"],
                    })
            await asyncio.sleep(OTP_POLL_INTERVAL)
        return tool_response(request_id, {"otp": None, "error": "Timeout ohne OTP"})

    if name == "extract_magic_link":
        inbox = find_inbox(str(args.get("token", "")))
        if inbox is None:
            return tool_response(request_id, {"magicLink": None, "error": "Inbox nicht gefunden / abgelaufen"})
        for mail in reversed(inbox["mails"]):
            link = extract_magic_link(mail["body"])
            if link:
                return tool_response(request_id, {"magicLink": link, "email": inbox["email"]})
        return tool_response(request_id, {"magicLink": None, "error": "Kein Magic Link gefunden"})

    if message.get("method") == "tools/list" or name is None:
        return tool_response(request_id, {"tools": TOOLS})

    return error_response(request_id, f"Unbekanntes Tool: {name}")


@app.websocket("/ws")
@app.websocket("/")
async def websocket_endpoint(websocket: WebSocket):
    """JSON-RPC-Endpunkt – akzeptiert Requests auf /ws und / (MCPClient
    verbindet gegen die Basis-URL ohne Pfad)."""
    await websocket.accept()
    logger.info("MCP-Client verbunden: %s", websocket.client)
    try:
        while True:
            raw = await websocket.receive_text()
            try:
                message = json.loads(raw)
                response = await handle_rpc(message)
                if response is not None:
                    await websocket.send_text(json.dumps(response))
            except json.JSONDecodeError:
                await websocket.send_text(json.dumps(error_response(None, "Ungültiges JSON")))
    except WebSocketDisconnect:
        logger.info("MCP-Client getrennt")


# ============ REST: MAIL-INJECTION & ADMIN ============

class IncomingMail(BaseModel):
    token: str
    sender: str = "noreply@example.com"
    subject: str = "Ihr Code"
    body: str


@app.post("/api/mcp/mail")
async def inject_mail(mail: IncomingMail):
    """Injiziert eine E-Mail in eine Inbox (SMTP-Ersatz für Testumgebungen)."""
    inbox = find_inbox(mail.token)
    if inbox is None:
        raise HTTPException(status_code=404, detail="Inbox nicht gefunden / abgelaufen")
    inbox["mails"].append({
        "from": mail.sender, "subject": mail.subject,
        "body": mail.body, "received": time.time(),
    })
    return {"ok": True, "otp_detected": extract_otp(mail.body)}


@app.get("/api/inboxes")
async def list_inboxes():
    prune_expired()
    return [
        {"inboxId": v["inboxId"], "email": v["email"], "mails": len(v["mails"])}
        for v in inboxes.values()
    ]


@app.get("/api/health")
async def health():
    return {"status": "ok", "inboxes": len(inboxes)}


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8001)
