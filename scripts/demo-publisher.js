#!/usr/bin/env node
/**
 * SecureGuard Enterprise – Demo-Telemetrie-Simulator
 * ---------------------------------------------------
 * Simuliert ein ESP32-Gateway ("Gateway #1") und publiziert zyklisch
 * Telemetrie, Alarme und Status über MQTT – so bekommen App, Backend
 * (MQTT→WebSocket-Bridge) und Node-RED sofort Live-Daten.
 *
 * Start:
 *   node scripts/demo-publisher.js [intervallSekunden]
 */
const mqtt = require('mqtt');

const BROKER_URL = process.env.MQTT_BROKER_URL || 'mqtt://127.0.0.1:1883';
const INTERVAL_MS = (Number(process.argv[2]) || 15) * 1000;
const DEVICE = 'ESP32_SecureGuard';
const MAC = 'AA:BB:CC:DD:EE:01';

const client = mqtt.connect(BROKER_URL, {
  clientId: `secureguard-demo-${Date.now()}`,
  clean: true,
  reconnectPeriod: 3000,
});

client.on('connect', () => {
  console.log(`[demo] Verbunden mit ${BROKER_URL}`);
  publishTelemetry();
  setInterval(publishTelemetry, INTERVAL_MS);
  setTimeout(publishAlert, 8000);
  setTimeout(publishStatus, 5000);
});

client.on('error', (err) => console.error('[demo] Fehler:', err.message));

function publishTelemetry() {
  const msg = {
    type: 'telemetry',
    battery: Math.round(55 + Math.random() * 40),
    wifi_rssi: -30 - Math.round(Math.random() * 30),
    lora_rssi: -60 - Math.round(Math.random() * 20),
    uptime: Math.round(Date.now() / 1000),
    ip: '192.168.1.50',
    device: DEVICE,
    latitude: 52.5200 + (Math.random() - 0.5) * 0.0005,
    longitude: 13.4050 + (Math.random() - 0.5) * 0.0005,
  };
  client.publish(`secureguard/${MAC}/telemetry`, JSON.stringify(msg), { qos: 1 });
  console.log(`[demo] Telemetrie → secureguard/${MAC}/telemetry (${msg.battery}%)`);
}

function publishAlert() {
  const msg = {
    type: 'alert',
    alert_type: 'SECURITY',
    severity: 'WARNING',
    message: 'Demo: Bewegung am Roller #1 erkannt',
    device: DEVICE,
  };
  client.publish(`secureguard/${MAC}/alert`, JSON.stringify(msg), { qos: 1 });
  console.log(`[demo] Alert → secureguard/${MAC}/alert`);
}

function publishStatus() {
  client.publish('secureguard/broadcast', JSON.stringify({
    type: 'system_status',
    status: 'ONLINE',
    device: DEVICE,
    timestamp: new Date().toISOString(),
  }), { qos: 1 });
  console.log('[demo] Status → secureguard/broadcast');
}
