/**
 * Fachliche Stammdaten des Operations Centers.
 * Spiegelt 1:1 die Kotlin-Definitionen der App
 * (`presentation/ui/common/ActionCatalog.kt`, `data/model/Enums.kt`),
 * damit 3D-Konsole und Android-App identisch sprechen.
 */

export const STATUS = {
  ONLINE:      { label: 'Online',    color: '#00e676', rank: 0 },
  SEARCHING:   { label: 'Suche',     color: '#448aff', rank: 1 },
  MAINTENANCE: { label: 'Wartung',   color: '#ff9100', rank: 2 },
  OFFLINE:     { label: 'Offline',   color: '#ff4d6a', rank: 3 },
  UNKNOWN:     { label: 'Unbekannt', color: '#78909c', rank: 4 }
}

/** Die 9 Detection-Kanäle + 3 Echtzeit-Kanäle des Agenten. */
export const CHANNELS = [
  { id: 'BLE',       label: 'BLE',       color: '#448aff', realtime: false },
  { id: 'WIFI',      label: 'WiFi',      color: '#00d4ff', realtime: false },
  { id: 'LORA',      label: 'LoRa',      color: '#9c6bff', realtime: false },
  { id: 'TELEMETRY', label: 'Telemetrie',color: '#00e676', realtime: false },
  { id: 'OPTICAL',   label: 'Optisch',   color: '#ff7043', realtime: false },
  { id: 'URBAN',     label: 'Urban',     color: '#ffc400', realtime: false },
  { id: 'CROWD',     label: 'Crowd',     color: '#26c6da', realtime: false },
  { id: 'SATELLITE', label: 'Satellit',  color: '#ec407a', realtime: false },
  { id: 'API',       label: 'API',       color: '#7e57c2', realtime: false },
  { id: 'MQTT',      label: 'MQTT',      color: '#66bb6a', realtime: true },
  { id: 'WEBSOCKET', label: 'WebSocket', color: '#29b6f6', realtime: true },
  { id: 'NFC',       label: 'NFC',       color: '#ffa726', realtime: true }
]

export const CHANNEL_MAP = Object.fromEntries(CHANNELS.map((c) => [c.id, c]))

export const CATEGORIES = [
  { id: 'FAVORITES', label: '★ Favoriten' },
  { id: 'SIGNAL',    label: 'Signalisieren' },
  { id: 'CONTROL',   label: 'Steuern' },
  { id: 'QUERY',     label: 'Abfragen' },
  { id: 'SCENE',     label: 'Lagebild' }
]

/**
 * Aktions-Katalog. `wire` ist der Befehl, den Firmware/MQTT erwarten
 * (siehe firmware/secureguard_esp32.ino und AgentService.sendAction).
 */
export const ACTIONS = [
  {
    id: 'ALARM', wire: 'ALARM', title: 'Alarm auslösen', icon: '🔔',
    desc: 'Akustischer Alarm am Asset – zum Wiederauffinden vor Ort.',
    category: 'SIGNAL', risk: 'caution', color: '#ff4d6a',
    requiresOnline: false, queueable: true, key: '1'
  },
  {
    id: 'LIGHT', wire: 'LIGHT', title: 'Blinken', icon: '💡',
    desc: 'LED-Signal aktivieren – leise Alternative zum Alarm.',
    category: 'SIGNAL', risk: 'safe', color: '#ffc400',
    requiresOnline: false, queueable: true, key: '2'
  },
  {
    id: 'MESSAGE', wire: 'MESSAGE', title: 'Nachricht senden', icon: '✉️',
    desc: 'Freitext an Display bzw. Empfänger des Assets übertragen.',
    category: 'SIGNAL', risk: 'safe', color: '#00d4ff',
    requiresOnline: false, queueable: true, acceptsNote: true, key: '3'
  },
  {
    id: 'MOTOR_OFF', wire: 'MOTOR_OFF', title: 'Motor abschalten', icon: '⛔',
    desc: 'Antrieb sperren. Nur im Stillstand verwenden!',
    category: 'CONTROL', risk: 'critical', color: '#ff4d6a',
    requiresOnline: true, queueable: false, key: '4',
    confirmTitle: 'Motor wirklich abschalten?',
    confirmText: 'Der Antrieb wird sofort gesperrt. Nur ausführen, wenn das Fahrzeug steht – sonst besteht Unfallgefahr.'
  },
  {
    id: 'RESTART', wire: 'RESTART', title: 'Neustart', icon: '🔄',
    desc: 'Controller neu starten – kurzer Verbindungsverlust.',
    category: 'CONTROL', risk: 'critical', color: '#ff9100',
    requiresOnline: true, queueable: false, key: '5',
    confirmTitle: 'Neustart auslösen?',
    confirmText: 'Das Asset ist ca. 10–30 Sekunden nicht erreichbar und meldet sich danach neu am Agenten an.'
  },
  {
    id: 'POSITION', wire: 'POSITION', title: 'Position anfordern', icon: '📍',
    desc: 'Sofortige Standortmeldung über den schnellsten Kanal.',
    category: 'QUERY', risk: 'safe', color: '#00e676',
    requiresOnline: false, queueable: true, key: '6'
  },
  {
    id: 'BATTERY', wire: 'BATTERY', title: 'Akkustand abfragen', icon: '🔋',
    desc: 'Ladezustand und Spannung des Assets auslesen.',
    category: 'QUERY', risk: 'safe', color: '#00e676',
    requiresOnline: false, queueable: true, key: '7'
  },
  {
    id: 'TELEMETRY', wire: 'TELEMETRY', title: 'Telemetrie abrufen', icon: '📊',
    desc: 'Vollständigen Sensor-Datensatz anfordern und speichern.',
    category: 'QUERY', risk: 'safe', color: '#9c6bff',
    requiresOnline: false, queueable: true, key: '8'
  },

  /* --- Lagebild-Aktionen: wirken lokal in der 3D-Szene --- */
  {
    id: 'SWEEP', title: 'Radar-Sweep', icon: '📡', local: true,
    desc: 'Alle Kanäle einmalig gegen alle Ziele prüfen (Suchlauf).',
    category: 'SCENE', risk: 'safe', color: '#00d4ff', key: 'r'
  },
  {
    id: 'FOCUS', title: 'Ziel anfliegen', icon: '🎯', local: true,
    desc: 'Kamera weich auf das primäre Ziel zentrieren.',
    category: 'SCENE', risk: 'safe', color: '#448aff', key: 'f'
  },
  {
    id: 'GEOFENCE', title: 'Geofence umschalten', icon: '🛡️', local: true,
    desc: 'Sicherheitsradius um die ausgewählten Ziele ein-/ausblenden.',
    category: 'SCENE', risk: 'safe', color: '#9c6bff', key: 'g'
  },
  {
    id: 'HEATMAP', title: 'Detection-Heatmap', icon: '🌡️', local: true,
    desc: 'Detektionsdichte der letzten Zyklen auf dem Boden darstellen.',
    category: 'SCENE', risk: 'safe', color: '#ff7043', key: 'm'
  }
]

export const ACTION_MAP = Object.fromEntries(ACTIONS.map((a) => [a.id, a]))

export const RISK_LABEL = {
  safe: 'unkritisch',
  caution: 'mit Bedacht',
  critical: 'kritisch'
}
