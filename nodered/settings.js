/**
 * SecureGuard Node-RED Settings (Pilot/Produktions-Basis).
 *
 * Das offizielle Docker-Image lädt automatisch /data/settings.js, wenn die
 * Datei existiert (Volume ./nodered:/data). Wichtige Punkte:
 *  - credentialSecret: MUSS in Produktion gesetzt werden (sonst sind
 *    Flow-Credentials unverschlüsselt/verlierbar). Wird aus der Umgebung
 *    gelesen:  NODE_RED_CREDENTIAL_SECRET  (in docker-compose/.env setzen).
 *  - Alle übrigen Optionen sind bewusst auf Node-RED-Defaults gelassen.
 */
module.exports = {
    flowFile: "flows.json",
    flowFilePretty: true,

    // Verschlüsselung der Flow-Credentials – in Produktion SETZEN:
    credentialSecret: process.env.NODE_RED_CREDENTIAL_SECRET || "secureguard-change-me",

    // Editor (Pilot offen; für Produktion adminAuth ergänzen – siehe
    // https://nodered.org/docs/user-guide/runtime/securing-node-red)
    uiHost: "0.0.0.0",
    uiPort: 1880,

    logging: {
        console: { level: process.env.NODE_RED_LOG_LEVEL || "info", metrics: false, audit: false }
    },

    // Funktion-Nodes: keine externen Module nötig (Flows sind Core-only)
    functionExternalModules: false,

    exportContext: false
};
