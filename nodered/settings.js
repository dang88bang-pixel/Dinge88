/**
 * SecureGuard Enterprise – Node-RED Settings
 * ------------------------------------------
 * Wird vom offiziellen Node-RED-Image über /data/settings.js geladen und
 * auch für den lokalen Start verwendet (scripts/start-services.sh).
 *
 * Flow-Datei: per FLOWS-Umgebungsvariable wählbar:
 *   - nodered/flows.json        (lokaler Broker: localhost:1883)
 *   - nodered/flows.docker.json (Docker-Broker: mqtt:1883 – in
 *                                docker-compose.yml über FLOWS gesetzt)
 */
module.exports = {
    uiPort: process.env.PORT || 1880,
    flowFile: process.env.FLOWS || 'flows.json',
    flowFilePretty: true,

    // Entwicklungsumgebung – für Produktion unbedingt adminAuth setzen!
    adminAuth: undefined,
    httpAdminRoot: '/',
    httpNodeRoot: '/',
    disableEditor: false,

    credentialSecret: process.env.NODE_RED_CREDENTIAL_SECRET || false,

    functionExternalModules: false,

    logging: {
        console: {
            level: process.env.LOG_LEVEL || 'info',
            metrics: false,
            audit: false
        }
    },

    debugMaxLength: 1000,

    // Erlaubt große MQTT-Nachrichten (Telemetrie-Payloads)
    maxNodeMessageSize: 100000
};
