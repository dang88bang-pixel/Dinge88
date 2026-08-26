# SecureGuard Node-RED Image (mit Dashboard)
# -------------------------------------------
FROM nodered/node-red:3.1.9

# Dashboard-Paket (ui_gauge/ui_button/ui_text/ui_group/ui_tab)
RUN npm install --no-audit --no-fund node-red-dashboard@3.6.6

# Flows/Settings kommen über das Volume ./nodered:/data
EXPOSE 1880
