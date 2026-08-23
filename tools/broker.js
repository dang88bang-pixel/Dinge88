/**
 * SecureGuard MQTT-Broker (aedes) — Aktiv-Variante für Umgebungen ohne
 * Mosquitto-Binary (gleiche Ports wie mosquitto/config/mosquitto.conf):
 *   - 1883: MQTT (App / Gateways / Backend)
 *   - 9001: MQTT over WebSocket (Browser-Dashboards / Node-RED)
 */
const { Aedes } = require('aedes');
const net = require('net');
const http = require('http');
const ws = require('ws');

const PORT_TCP = 1883;
const PORT_WS = 9001;

async function main() {
  const aedes = await Aedes.createBroker({});

  net.createServer(aedes.handle).listen(PORT_TCP, '0.0.0.0', () => {
    console.log(`[broker] MQTT TCP  auf 0.0.0.0:${PORT_TCP}`);
  });

  const server = http.createServer((req, res) => {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('SecureGuard MQTT-Broker (aedes) – WS-Endpunkt: ws://<host>:' + PORT_WS + '\n');
  });
  const wss = new ws.WebSocketServer({ server });
  wss.on('connection', (conn) => aedes.handle(conn));
  server.listen(PORT_WS, '0.0.0.0', () => {
    console.log(`[broker] MQTT WS   auf 0.0.0.0:${PORT_WS}`);
  });

  aedes.on('client', (client) => console.log(`[broker] Client verbunden: ${client.id}`));
  aedes.on('publish', (packet, client) => {
    if (client) {
      console.log(`[broker] Publish ${packet.topic}: ${packet.payload.toString().slice(0, 120)}`);
    }
  });
}

main().catch((err) => {
  console.error('[broker] Fatal:', err);
  process.exit(1);
});
