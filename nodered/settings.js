/**
 * SecureGuard Enterprise – Node-RED Settings
 * ------------------------------------------
 * Admin-Login (echte Berechtigung):
 *   NODE_RED_ADMIN_USER     – Benutzername
 *   NODE_RED_ADMIN_PASS_HASH – bcrypt-Hash des Passworts
 *   (wird von scripts/setup-all.sh generiert; Hash mit
 *    node -e "console.log(require('bcryptjs').hashSync('pass',10))"
 *    erzeugen – bcryptjs ist in Node-RED enthalten)
 *
 * Flow-Datei: per FLOWS-Umgebungsvariable wählbar:
 *   - nodered/flows.json            (lokal: Platzhalter werden durch
 *                                    start-services.sh ersetzt)
 *   - nodered/flows.docker.json     (Docker-Broker mqtt:1883)
 */
const adminUser = process.env.NODE_RED_ADMIN_USER;
const adminPassHash = process.env.NODE_RED_ADMIN_PASS_HASH;

module.exports = {
    uiPort: process.env.PORT || 1880,
    flowFile: process.env.FLOWS || 'flows.json',
    flowFilePretty: true,

    // Admin-Login – nur wenn Zugangsdaten gesetzt sind
    adminAuth: (adminUser && adminPassHash)
        ? {
            type: 'credentials',
            users: [
                { username: adminUser, password: adminPassHash, permissions: '*' }
            ]
        }
        : undefined,

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
