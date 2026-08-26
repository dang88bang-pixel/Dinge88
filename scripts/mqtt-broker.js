#!/usr/bin/env node
/**
 * SecureGuard Enterprise – lokaler MQTT-Broker (aedes)
 * -------------------------------------------------------------
 * Vollständiger MQTT-Broker ohne Docker-Abhängigkeit:
 *   - MQTT  : TCP  0.0.0.0:1883  (App, Backend, ESP32-Gateways)
 *   - MQTT  : WebSocket 0.0.0.0:9001 (Browser, Node-RED, Dashboards)
 *
 * Start:
 *   npm install            (im Verzeichnis oder: npm i aedes websocket-stream)
 *   node scripts/mqtt-broker.js
 *
 * Topics wie im README: secureguard/+/telemetry, /alert, /command, /status,
 * secureguard/broadcast
 */
const AedesModule = require('aedes');
// aedes >= 1.0 exportiert die Klasse als .Aedes / .default
const Aedes = AedesModule.Aedes || AedesModule.default || AedesModule;
/**
 * aedes 1.x muss erst initialisiert werden (Persistence/Setup), bevor
 * Verbindungen angenommen werden – daher async main().
 */
const aedes = new Aedes();
const net = require('net');
const http = require('http');
const websocketStream = require('websocket-stream');

const MQTT_PORT = Number(process.env.MQTT_PORT || 1883);
const MQTT_WS_PORT = Number(process.env.MQTT_WS_PORT || 9001);
const HOST = process.env.MQTT_HOST || '0.0.0.0';

// ---- Authentifizierung (echte Broker-Berechtigungen) ----
// Credentials kommen aus SG_MQTT_USERNAME / SG_MQTT_PASSWORD (Umgebung).
// Ohne Konfiguration bleibt der Broker offen (nur lokale Entwicklung).
const SG_USER = process.env.SG_MQTT_USERNAME || '';
const SG_PASS = process.env.SG_MQTT_PASSWORD || '';
if (SG_USER && SG_PASS) {
  aedes.authenticate = (client, username, password, done) => {
    const ok = username === SG_USER && String(password || '') === SG_PASS;
    if (!ok) console.warn(`[broker] Auth abgelehnt für: ${username}`);
    done(null, ok);
  };
  console.log(`[broker] Authentifizierung AKTIV (Benutzer: ${SG_USER})`);
} else {
  console.warn('[broker] Keine SG_MQTT_USERNAME/SG_MQTT_PASSWORD gesetzt – offener Broker (nur lokal)!');
}

aedes.on('client', (client) => {
  console.log(`[broker] Client verbunden: ${client.id}`);
});
aedes.on('clientDisconnect', (client) => {
  console.log(`[broker] Client getrennt: ${client.id}`);
});
aedes.on('subscribe', (subs, client) => {
  console.log(`[broker] ${client.id} abonniert: ${subs.map((s) => s.topic).join(', ')}`);
});
aedes.on('publish', (packet, client) => {
  if (client) {
    console.log(`[broker] ${client.id} → ${packet.topic}: ${packet.payload.toString().slice(0, 120)}`);
  } else {
    console.log(`[broker] (intern) → ${packet.topic}`);
  }
});
aedes.on('error', (err) => {
  console.error('[broker] Fehler:', err.message);
});

// ---- Start (aedes initialisieren, dann Listener öffnen) ----
async function main() {
  await aedes.listen();

  // ---- MQTT über TCP ----
  const tcpServer = net.createServer(aedes.handle);
  tcpServer.listen(MQTT_PORT, HOST, () => {
    console.log(`[broker] MQTT (TCP) bereit auf ${HOST}:${MQTT_PORT}`);
  });

  // ---- MQTT über WebSocket ----
  const httpServer = http.createServer();
  websocketStream.createServer({ server: httpServer }, aedes.handle);
  httpServer.listen(MQTT_WS_PORT, HOST, () => {
    console.log(`[broker] MQTT (WebSocket) bereit auf ${HOST}:${MQTT_WS_PORT}`);
  });

  const shutdown = () => {
    console.log('[broker] Fahre herunter …');
    tcpServer.close();
    httpServer.close();
    aedes.close(() => process.exit(0));
    setTimeout(() => process.exit(0), 1500);
  };
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}

main().catch((err) => {
  console.error('[broker] Startfehler:', err);
  process.exit(1);
});
