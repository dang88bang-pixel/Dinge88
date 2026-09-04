# SecureGuard Enterprise – Slack-Integration über MCP
# ---------------------------------------------------
# MCP-Client für provectus/slack-mcp-server (Go, https://github.com/provectus/
# slack-mcp-server). Der Slack-MCP-Server spricht JSON-RPC 2.0 über
#   * "Streamable HTTP" (POST {base}/mcp)   – Default, empfohlen
#   * SSE                (GET {base}/sse + POST {message-endpoint})
#
# Die Android-App spricht weder SSE noch Streamable HTTP, sondern REST. Dieses
# Modul macht das Backend daher zum MCP-Client und stellt der App schlanke
# REST-Endpunkte zur Verfügung (siehe main.py):
#   GET  /api/slack/health    Erreichbarkeit, Server-Info, Tool-Anzahl
#   GET  /api/slack/tools     registrierte MCP-Tools
#   GET  /api/slack/channels  Channel-Verzeichnis (channels_list, CSV)
#   POST /api/slack/call      beliebiger Tool-Aufruf        (X-API-Key)
#   POST /api/slack/notify    Meldung in einen Channel      (X-API-Key)
#
# Umgebungsvariablen (siehe .env.example):
#   SLACK_MCP_URL              http://slack-mcp:13080/mcp
#   SLACK_MCP_TRANSPORT        http | sse           (Default: http)
#   SLACK_MCP_API_KEY          Bearer-Token des Slack-MCP-Servers (optional)
#   SLACK_MCP_TIMEOUT          Sekunden pro Aufruf  (Default: 15)
#   SLACK_NOTIFY_ENABLED       true|false           (Default: auto = URL gesetzt)
#   SLACK_NOTIFY_CHANNEL       Ziel-Channel         (Default: #secureguard-alerts)
#   SLACK_NOTIFY_MIN_SEVERITY  INFO|WARNING|CRITICAL (Default: WARNING)
#   SLACK_WEBHOOK_URL          Fallback: Slack Incoming Webhook (optional)

from __future__ import annotations

import asyncio
import csv
import io
import json
import logging
import os
import time
from dataclasses import dataclass
from typing import Any, Dict, List, Optional
from urllib.parse import urljoin, urlsplit, urlunsplit

try:
    import httpx
except ImportError:  # pragma: no cover - requirements.txt pinnt httpx
    httpx = None  # type: ignore[assignment]

logger = logging.getLogger("secureguard.slack")

# MCP-Protokollversion, die der Server (mcp-go) akzeptiert.
PROTOCOL_VERSION = "2024-11-05"
CLIENT_INFO = {"name": "secureguard-backend", "version": "1.0.1"}
SESSION_HEADER = "Mcp-Session-Id"
SEVERITY_ORDER = {"INFO": 0, "WARNING": 1, "CRITICAL": 2}
DEFAULT_CHANNEL = "#secureguard-alerts"
ADD_MESSAGE_TOOL = "conversations_add_message"
CHANNELS_TOOL = "channels_list"


class SlackMCPError(RuntimeError):
    """Fehler bei der Kommunikation mit dem Slack-MCP-Server."""


def _env_bool(name: str, default: Optional[bool] = None) -> Optional[bool]:
    raw = os.environ.get(name, "").strip().lower()
    if not raw:
        return default
    return raw in ("1", "true", "yes", "on")


@dataclass(frozen=True)
class SlackSettings:
    """Aus der Umgebung gelesene Konfiguration (unveränderlich)."""

    url: str = ""
    transport: str = "http"
    api_key: str = ""
    timeout: float = 15.0
    tools_ttl: float = 300.0
    notify_enabled: bool = False
    notify_channel: str = DEFAULT_CHANNEL
    notify_min_severity: str = "WARNING"
    webhook_url: str = ""

    @property
    def base_url(self) -> str:
        return self.url.rstrip("/")

    @property
    def configured(self) -> bool:
        return bool(self.base_url)

    @property
    def http_endpoint(self) -> str:
        """Streamable-HTTP-Endpunkt (mcp-go: `WithEndpointPath("/mcp")`)."""
        base = self.base_url
        if not base:
            return ""
        return base if base.endswith("/mcp") else f"{base}/mcp"

    @property
    def sse_endpoint(self) -> str:
        base = self.base_url
        if not base:
            return ""
        return base if base.endswith("/sse") else f"{base}/sse"


def load_settings() -> SlackSettings:
    """Liest die Slack-Konfiguration aus der Umgebung.

    `notify_enabled` ist "auto": ohne explizite Variable gilt die Integration
    als aktiv, sobald eine MCP-URL **oder** eine Webhook-URL konfiguriert ist.
    """
    url = os.environ.get("SLACK_MCP_URL", "").strip()
    webhook = os.environ.get("SLACK_WEBHOOK_URL", "").strip()
    transport = os.environ.get("SLACK_MCP_TRANSPORT", "http").strip().lower()
    if transport not in ("http", "sse"):
        logger.warning("SLACK_MCP_TRANSPORT=%s unbekannt – nutze 'http'", transport)
        transport = "http"

    notify = _env_bool("SLACK_NOTIFY_ENABLED")
    if notify is None:
        notify = bool(url or webhook)

    def _num(name: str, default: float) -> float:
        try:
            value = float(os.environ.get(name, "").strip())
        except ValueError:
            return default
        return value if value > 0 else default

    return SlackSettings(
        url=url,
        transport=transport,
        api_key=os.environ.get("SLACK_MCP_API_KEY", "").strip(),
        timeout=_num("SLACK_MCP_TIMEOUT", 15.0),
        tools_ttl=_num("SLACK_MCP_TOOLS_TTL", 300.0),
        notify_enabled=notify,
        notify_channel=os.environ.get("SLACK_NOTIFY_CHANNEL", "").strip() or DEFAULT_CHANNEL,
        notify_min_severity=(
            os.environ.get("SLACK_NOTIFY_MIN_SEVERITY", "").strip().upper() or "WARNING"
        ),
        webhook_url=webhook,
    )


# Moduleigene Konfiguration – Tests/Settings-Reload via reload_settings().
settings: SlackSettings = load_settings()


def reload_settings() -> SlackSettings:
    global settings
    settings = load_settings()
    return settings


# ============ JSON-RPC / MCP-Helfer ============

def _parse_sse_jsonrpc(raw: str, expected_id: Any) -> Optional[dict]:
    """Extrahiert die JSON-RPC-Antwort mit `expected_id` aus einem SSE-Body.

    Streamable HTTP darf Antworten als `text/event-stream` liefern; jede
    `data:`-Zeile enthält dann eine JSON-RPC-Message.
    """
    fallback: Optional[dict] = None
    for line in raw.splitlines():
        line = line.strip()
        if not line.startswith("data:"):
            continue
        payload = line[len("data:"):].strip()
        if not payload:
            continue
        try:
            msg = json.loads(payload)
        except json.JSONDecodeError:
            continue
        if not isinstance(msg, dict):
            continue
        if fallback is None:
            fallback = msg
        if msg.get("id") == expected_id:
            return msg
    return fallback


def extract_tool_text(result: Any) -> str:
    """MCP-Tool-Result → Text (content[].text, strukturiert oder Roh-JSON)."""
    if result is None:
        return ""
    if isinstance(result, str):
        return result
    if isinstance(result, dict):
        content = result.get("content")
        if isinstance(content, list):
            parts: List[str] = []
            for block in content:
                if isinstance(block, dict):
                    if block.get("type") == "text" and isinstance(block.get("text"), str):
                        parts.append(block["text"])
                    elif block.get("type") == "resource":
                        parts.append(json.dumps(block, ensure_ascii=False))
                elif isinstance(block, str):
                    parts.append(block)
            if parts:
                return "\n".join(parts)
        structured = result.get("structuredContent")
        if structured is not None:
            return json.dumps(structured, ensure_ascii=False)
        return json.dumps(result, ensure_ascii=False)
    return str(result)


def tool_is_error(result: Any) -> bool:
    if isinstance(result, dict):
        for key in ("isError", "is_error"):
            if key in result:
                return bool(result[key])
    return False


@dataclass
class ToolResult:
    """Ergebnis eines `tools/call` – immer JSON-serialisierbar."""

    ok: bool
    tool: str
    text: str = ""
    is_error: bool = False
    error: Optional[str] = None
    raw: Optional[dict] = None

    def as_dict(self) -> Dict[str, Any]:
        return {
            "ok": self.ok,
            "tool": self.tool,
            "text": self.text,
            "is_error": self.is_error,
            "error": self.error,
            "raw": self.raw,
        }


def parse_channels_csv(text: str) -> List[Dict[str, str]]:
    """`channels_list` liefert CSV – hier als Liste von Dicts für die App."""
    if not text or not text.strip():
        return []
    try:
        reader = csv.DictReader(io.StringIO(text.strip()))
        rows = [
            {(k or "").strip(): (v or "").strip() for k, v in row.items() if k}
            for row in reader
        ]
    except csv.Error:
        return []
    return [r for r in rows if any(r.values())]


# ============ MCP-CLIENT ============

class SlackMCPClient:
    """Minimaler MCP-Client (JSON-RPC 2.0) für den Slack-MCP-Server.

    Unterstützt Streamable HTTP (Default) und den klassischen SSE-Transport.
    Bewusst ohne MCP-SDK: das Backend bleibt bei den bestehenden, gepinnten
    Abhängigkeiten (httpx) und der Code ist testbar.
    """

    def __init__(
        self,
        config: Optional[SlackSettings] = None,
        http_client: Optional["httpx.AsyncClient"] = None,
    ) -> None:
        self.config = config or settings
        self._own_client = http_client is None
        self._client: Optional["httpx.AsyncClient"] = http_client
        self._request_id = 0
        self._session_id = ""
        self._initialized = False
        self._server_info: Dict[str, Any] = {}
        self._tools: List[Dict[str, Any]] = []
        self._tools_loaded_at = 0.0
        self._lock = asyncio.Lock()
        self._last_error: Optional[str] = None
        # SSE-Transport
        self._sse_response: Any = None
        self._sse_task: Optional[asyncio.Task] = None
        self._message_endpoint = ""
        self._endpoint_ready = asyncio.Event()
        self._pending: Dict[Any, asyncio.Future] = {}

    # ---- Lebenszyklus ----

    @property
    def http(self) -> "httpx.AsyncClient":
        if httpx is None:
            raise SlackMCPError("httpx fehlt – pip install -r backend/requirements.txt")
        if self._client is None:
            self._client = httpx.AsyncClient(timeout=self.config.timeout)
        return self._client

    async def aclose(self) -> None:
        if self._sse_task is not None:
            self._sse_task.cancel()
            self._sse_task = None
        if self._sse_response is not None:
            try:
                await self._sse_response.aclose()
            except Exception:  # pragma: no cover - Best effort
                pass
            self._sse_response = None
        if self._client is not None and self._own_client:
            await self._client.aclose()
            self._client = None

    def _next_id(self) -> int:
        self._request_id += 1
        return self._request_id

    def _headers(self, extra: Optional[Dict[str, str]] = None) -> Dict[str, str]:
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json, text/event-stream",
        }
        if self.config.api_key:
            headers["Authorization"] = f"Bearer {self.config.api_key}"
        if self._session_id:
            headers[SESSION_HEADER] = self._session_id
        if extra:
            headers.update(extra)
        return headers

    # ---- Transport: JSON-RPC ----

    async def _rpc(self, method: str, params: Optional[dict] = None) -> dict:
        if not self.config.configured:
            raise SlackMCPError("SLACK_MCP_URL nicht konfiguriert")
        if self.config.transport == "sse":
            return await self._rpc_sse(method, params or {})
        return await self._rpc_http(method, params or {})

    async def _rpc_http(self, method: str, params: dict) -> dict:
        request_id = self._next_id()
        payload = {
            "jsonrpc": "2.0",
            "id": request_id,
            "method": method,
            "params": params,
        }
        endpoint = self.config.http_endpoint
        try:
            response = await self.http.post(
                endpoint, json=payload, headers=self._headers()
            )
        except Exception as exc:
            self._last_error = f"{type(exc).__name__}: {exc}"
            raise SlackMCPError(f"Slack-MCP nicht erreichbar ({endpoint}): {exc}") from exc

        session_id = response.headers.get(SESSION_HEADER)
        if session_id:
            self._session_id = session_id

        if response.status_code == 404 and self._session_id:
            # Session vom Server verworfen → einmal neu initialisieren.
            self._session_id = ""
            self._initialized = False
            raise SlackMCPError("MCP-Session ungültig – bitte erneut versuchen")
        if response.status_code >= 400:
            self._last_error = f"HTTP {response.status_code}"
            raise SlackMCPError(
                f"Slack-MCP antwortete mit HTTP {response.status_code}: "
                f"{response.text[:200]}"
            )
        if not response.content:
            # Notification → 202 Accepted ohne Body.
            return {}
        content_type = response.headers.get("content-type", "")
        if "text/event-stream" in content_type:
            message = _parse_sse_jsonrpc(response.text, request_id)
            if message is None:
                raise SlackMCPError("Keine JSON-RPC-Antwort im SSE-Stream gefunden")
            return message
        try:
            return response.json()
        except json.JSONDecodeError as exc:
            raise SlackMCPError(f"Ungültige JSON-Antwort: {exc}") from exc

    async def _ensure_sse(self) -> str:
        """Öffnet (bei Bedarf) den SSE-Stream und liefert den Message-Endpunkt."""
        if self._sse_task is not None and self._sse_task.done():
            self._sse_response = None
            self._sse_task = None
            self._message_endpoint = ""
            self._endpoint_ready = asyncio.Event()
        if self._message_endpoint and self._sse_task is not None:
            return self._message_endpoint

        sse_url = self.config.sse_endpoint
        request = self.http.build_request("GET", sse_url, headers=self._headers())
        try:
            self._sse_response = await self.http.send(request, stream=True)
        except Exception as exc:
            self._last_error = f"{type(exc).__name__}: {exc}"
            raise SlackMCPError(f"Slack-MCP-SSE nicht erreichbar ({sse_url}): {exc}") from exc
        if self._sse_response.status_code >= 400:
            status = self._sse_response.status_code
            await self._sse_response.aclose()
            raise SlackMCPError(f"Slack-MCP-SSE antwortete mit HTTP {status}")

        self._sse_task = asyncio.create_task(self._read_sse())
        try:
            await asyncio.wait_for(self._endpoint_ready.wait(), timeout=self.config.timeout)
        except asyncio.TimeoutError as exc:
            raise SlackMCPError("Zeitüberschreitung: SSE-Endpunkt nicht erhalten") from exc
        if not self._message_endpoint:
            raise SlackMCPError("SSE-Stream lieferte keinen Message-Endpunkt")
        return self._message_endpoint

    def _resolve_endpoint(self, data: str) -> str:
        """`endpoint`-Event → absolute POST-URL.

        Der Server sendet entweder eine absolute URL oder einen Pfad
        (`/message?sessionId=…`); Pfade werden gegen die Server-Wurzel
        aufgelöst, nicht gegen den SSE-Pfad.
        """
        data = data.strip()
        if data.startswith(("http://", "https://")):
            return data
        parts = urlsplit(self.config.base_url)
        root = urlunsplit((parts.scheme, parts.netloc, "", "", ""))
        return urljoin(root + "/", data.lstrip("/"))

    async def _read_sse(self) -> None:
        """Liest den SSE-Stream und verteilt Antworten an wartende Aufrufer."""
        event = "message"
        try:
            async for raw_line in self._sse_response.aiter_lines():
                line = raw_line.rstrip("\r")
                if line.startswith("event:"):
                    event = line[len("event:"):].strip()
                    continue
                if line.startswith("data:"):
                    data = line[len("data:"):].strip()
                    if event == "endpoint":
                        self._message_endpoint = self._resolve_endpoint(data)
                        self._endpoint_ready.set()
                        continue
                    try:
                        message = json.loads(data)
                    except json.JSONDecodeError:
                        continue
                    future = self._pending.pop(message.get("id"), None)
                    if future is not None and not future.done():
                        future.set_result(message)
                    continue
                if line == "":
                    event = "message"
        except asyncio.CancelledError:
            raise
        except Exception as exc:  # Stream beendet/abgerissen
            self._last_error = f"SSE-Stream beendet: {exc}"
            self._endpoint_ready.set()
            for future in self._pending.values():
                if not future.done():
                    future.set_exception(SlackMCPError(self._last_error))
            self._pending.clear()
            self._message_endpoint = ""

    async def _rpc_sse(self, method: str, params: dict) -> dict:
        endpoint = await self._ensure_sse()
        request_id = self._next_id()
        payload = {
            "jsonrpc": "2.0",
            "id": request_id,
            "method": method,
            "params": params,
        }
        loop = asyncio.get_running_loop()
        future: asyncio.Future = loop.create_future()
        self._pending[request_id] = future
        try:
            response = await self.http.post(endpoint, json=payload, headers=self._headers())
        except Exception as exc:
            self._pending.pop(request_id, None)
            raise SlackMCPError(f"POST an {endpoint} fehlgeschlagen: {exc}") from exc
        if response.status_code >= 400:
            self._pending.pop(request_id, None)
            raise SlackMCPError(
                f"Slack-MCP antwortete mit HTTP {response.status_code}: "
                f"{response.text[:200]}"
            )
        try:
            message = await asyncio.wait_for(future, timeout=self.config.timeout)
        except asyncio.TimeoutError as exc:
            self._pending.pop(request_id, None)
            raise SlackMCPError("Zeitüberschreitung: keine Antwort über SSE") from exc
        return message

    # ---- MCP-Operationen ----

    @staticmethod
    def _unwrap(message: dict, method: str) -> Any:
        if not isinstance(message, dict):
            raise SlackMCPError(f"Ungültige Antwort auf {method}")
        if message.get("error"):
            err = message["error"]
            detail = err.get("message") if isinstance(err, dict) else str(err)
            code = err.get("code") if isinstance(err, dict) else ""
            raise SlackMCPError(f"MCP-Fehler bei {method} ({code}): {detail}")
        return message.get("result")

    async def initialize(self, force: bool = False) -> Dict[str, Any]:
        """MCP-Handshake; Ergebnis (serverInfo) wird gecacht."""
        if self._initialized and not force:
            return self._server_info
        async with self._lock:
            if self._initialized and not force:
                return self._server_info
            result = self._unwrap(
                await self._rpc(
                    "initialize",
                    {
                        "protocolVersion": PROTOCOL_VERSION,
                        "capabilities": {},
                        "clientInfo": CLIENT_INFO,
                    },
                ),
                "initialize",
            )
            info = (result or {}).get("serverInfo") or {}
            self._server_info = info if isinstance(info, dict) else {"name": str(info)}
            self._initialized = True
            # `notifications/initialized` ist eine Notification (keine Antwort).
            try:
                await self._notify("notifications/initialized", {})
            except SlackMCPError as exc:
                logger.debug("initialized-Notification nicht gesendet: %s", exc)
            return self._server_info

    async def _notify(self, method: str, params: dict) -> None:
        payload = {"jsonrpc": "2.0", "method": method, "params": params}
        if self.config.transport == "sse":
            endpoint = await self._ensure_sse()
        else:
            endpoint = self.config.http_endpoint
        await self.http.post(endpoint, json=payload, headers=self._headers())

    async def list_tools(self, refresh: bool = False) -> List[Dict[str, Any]]:
        """Registrierte Tools (gecacht, TTL = SLACK_MCP_TOOLS_TTL)."""
        now = time.monotonic()
        if (
            self._tools
            and not refresh
            and (now - self._tools_loaded_at) < self.config.tools_ttl
        ):
            return self._tools
        await self.initialize()
        result = self._unwrap(await self._rpc("tools/list", {}), "tools/list") or {}
        tools = result.get("tools") if isinstance(result, dict) else None
        self._tools = [t for t in (tools or []) if isinstance(t, dict)]
        self._tools_loaded_at = time.monotonic()
        self._last_error = None
        return self._tools

    async def tool_names(self) -> List[str]:
        return [str(t.get("name", "")) for t in await self.list_tools()]

    async def call_tool(self, name: str, arguments: Optional[dict] = None) -> ToolResult:
        """`tools/call` – Fehler werden als ToolResult(ok=False) zurückgegeben."""
        try:
            await self.initialize()
            message = await self._rpc(
                "tools/call", {"name": name, "arguments": arguments or {}}
            )
            result = self._unwrap(message, f"tools/call:{name}")
        except SlackMCPError as exc:
            self._last_error = str(exc)
            return ToolResult(ok=False, tool=name, error=str(exc))
        text = extract_tool_text(result)
        is_error = tool_is_error(result)
        self._last_error = text if is_error else None
        return ToolResult(
            ok=not is_error,
            tool=name,
            text=text,
            is_error=is_error,
            error=text if is_error else None,
            raw=result if isinstance(result, dict) else None,
        )

    async def health(self, probe: bool = True) -> Dict[str, Any]:
        """Status für /api/slack/health. `probe=False` = ohne Netzwerkaufruf."""
        info: Dict[str, Any] = {
            "configured": self.config.configured,
            "url": self.config.http_endpoint if self.config.transport == "http"
            else self.config.sse_endpoint,
            "transport": self.config.transport,
            "api_key_configured": bool(self.config.api_key),
            "session_id": bool(self._session_id),
            "initialized": self._initialized,
            "server": self._server_info or None,
            "tools": len(self._tools),
            "tool_names": [str(t.get("name", "")) for t in self._tools],
            "reachable": None,
            "error": self._last_error,
        }
        if not probe or not self.config.configured:
            info["reachable"] = False if not self.config.configured else None
            return info
        try:
            tools = await self.list_tools(refresh=not self._tools)
            info["reachable"] = True
            info["tools"] = len(tools)
            info["tool_names"] = [str(t.get("name", "")) for t in tools]
            info["server"] = self._server_info or None
            info["error"] = None
        except SlackMCPError as exc:
            info["reachable"] = False
            info["error"] = str(exc)
        return info


# ============ ALERT-BENACHRICHTIGUNG ============

def format_alert_message(
    *,
    asset_id: str,
    alert_type: str = "SECURITY",
    severity: str = "WARNING",
    message: str = "",
    source: str = "backend",
    timestamp: Optional[str] = None,
) -> str:
    """Markdown-Meldung (Slack `rich_text`) für einen SecureGuard-Alert."""
    icon = {"INFO": ":information_source:", "WARNING": ":warning:", "CRITICAL": ":rotating_light:"}
    lines = [
        f"{icon.get(severity.upper(), ':warning:')} *SecureGuard {severity.upper()}* "
        f"– `{alert_type}`",
        f"*Asset:* `{asset_id}`",
    ]
    if message:
        lines.append(f"*Meldung:* {message}")
    lines.append(f"*Quelle:* {source}")
    if timestamp:
        lines.append(f"*Zeit:* {timestamp}")
    return "\n".join(lines)


class SlackNotifier:
    """Versendet Meldungen über den Slack-MCP-Server (Fallback: Webhook)."""

    def __init__(self, client: Optional[SlackMCPClient] = None) -> None:
        self.client = client or SlackMCPClient()
        self.last_result: Dict[str, Any] = {}

    @property
    def config(self) -> SlackSettings:
        return self.client.config

    def severity_allowed(self, severity: str) -> bool:
        threshold = SEVERITY_ORDER.get(self.config.notify_min_severity.upper(), 1)
        return SEVERITY_ORDER.get((severity or "WARNING").upper(), 1) >= threshold

    async def send_text(self, text: str, channel: Optional[str] = None) -> Dict[str, Any]:
        """Meldung in einen Channel – MCP-Tool zuerst, dann Webhook-Fallback."""
        target = (channel or self.config.notify_channel).strip()
        result: Dict[str, Any] = {
            "ok": False,
            "channel": target,
            "transport": None,
            "detail": "",
        }
        if self.config.configured:
            tool_result = await self.client.call_tool(
                ADD_MESSAGE_TOOL,
                {
                    "channel_id": target,
                    "payload": text,
                    "content_type": "text/markdown",
                },
            )
            result["transport"] = "mcp"
            result["ok"] = tool_result.ok
            result["detail"] = (tool_result.text or tool_result.error or "")[:500]
            if tool_result.ok:
                self.last_result = result
                return result
            logger.warning(
                "Slack-MCP-Post nach %s fehlgeschlagen: %s", target, result["detail"]
            )
        if self.config.webhook_url:
            result["transport"] = "webhook"
            result["ok"], result["detail"] = await self._send_webhook(
                self.config.webhook_url, text
            )
        elif not self.config.configured:
            result["detail"] = "Weder SLACK_MCP_URL noch SLACK_WEBHOOK_URL konfiguriert"
        self.last_result = result
        return result

    async def notify_alert(
        self,
        *,
        asset_id: str,
        alert_type: str = "SECURITY",
        severity: str = "WARNING",
        message: str = "",
        source: str = "backend",
        timestamp: Optional[str] = None,
        channel: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Alert → Slack. Unterdrückt Meldungen unterhalb der Mindest-Schwere."""
        if not self.config.notify_enabled:
            return {"ok": False, "skipped": "disabled", "detail": "SLACK_NOTIFY_ENABLED=false"}
        if not self.severity_allowed(severity):
            return {
                "ok": False,
                "skipped": "severity",
                "detail": f"{severity} < {self.config.notify_min_severity}",
            }
        text = format_alert_message(
            asset_id=asset_id,
            alert_type=alert_type,
            severity=severity,
            message=message,
            source=source,
            timestamp=timestamp,
        )
        return await self.send_text(text, channel)

    async def _send_webhook(self, url: str, text: str) -> tuple:
        """Fallback ohne MCP-Server: Slack Incoming Webhook."""
        if httpx is None:
            return False, "httpx fehlt"
        try:
            async with httpx.AsyncClient(timeout=self.config.timeout) as client:
                response = await client.post(url, json={"text": text})
        except Exception as exc:
            return False, f"{type(exc).__name__}: {exc}"
        if response.status_code >= 400:
            return False, f"Webhook HTTP {response.status_code}: {response.text[:200]}"
        return True, f"Webhook HTTP {response.status_code}"

    async def aclose(self) -> None:
        await self.client.aclose()


# ============ MODUL-SINGLETONS ============

_notifier: Optional[SlackNotifier] = None


def get_notifier() -> SlackNotifier:
    """Lazy Singleton – Konfiguration wird beim ersten Zugriff gelesen."""
    global _notifier
    if _notifier is None:
        _notifier = SlackNotifier(SlackMCPClient(load_settings()))
    return _notifier


def set_notifier(notifier: Optional[SlackNotifier]) -> None:
    """Für Tests/Settings-Reload: Singleton ersetzen bzw. zurücksetzen."""
    global _notifier
    _notifier = notifier


def peek_notifier() -> Optional[SlackNotifier]:
    """Aktuelles Singleton **ohne** es anzulegen (für Shutdown/Tests)."""
    return _notifier
