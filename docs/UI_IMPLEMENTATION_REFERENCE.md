# SecureGuard Pro – UI Implementation Reference

This document is the binding UI contract for the Android frontend.

## Visual system
- Dark security-first mobile UI.
- Deep navy surfaces with cyan primary accent.
- Green = healthy/online, amber = warning, red = alarm/offline.
- 8dp spacing grid; cards use consistent rounded corners and subtle elevation.
- Primary actions use full-width/high-contrast controls.
- Status information must always be communicated by text/icon as well as color.

## Functional contract
Every visible control must invoke real application logic. No fake success states, placeholder buttons, or hard-coded live telemetry.

Every asynchronous operation exposes Loading, Success/Content, Empty and Error states as appropriate.

## Screens
The reference covers the complete product surface: Dashboard, Assets, Asset creation, QR scanner, Map, Actions, Alerts, Node status, Terminal, Sensor fusion, Security, Agent configuration, ESP32 configuration, System Health, Slack, Temp Mail, automatic port view, Settings, Help/Support, About, Splash/initialisation and System Success/Status.

## Android
- Android 11/API 30 is a first-class supported target.
- Runtime permissions must be requested contextually.
- Android 12+ Bluetooth permissions are version-gated.
- Background location is requested only after foreground location is granted and only when required.
- Camera, notifications, USB, NFC and sensor hardware must degrade gracefully when unavailable.

## Acceptance
The implementation is not complete until all routes are reachable, all visible controls are wired, tests pass, and a release APK can be built and verified.
