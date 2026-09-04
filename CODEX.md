# Codex — SecureGuard Enterprise

Die vollständigen Arbeitsanweisungen stehen in `AGENTS.md`. Diese Datei
existiert nur, damit Codex den Einstiegspunkt zuverlässig findet.

## Kurzfassung

1. `AGENTS.md` lesen — Architektur, Qualitätsschranken, Sicherheitsregeln.
2. `agent-os/GOALS.md` und `agent-os/Tasks/` für die aktuelle Priorität.
3. Skills unter `.agents/skills/<name>/SKILL.md`; Routing-Metadaten in
   `.agents/skills/<name>/agents/openai.yaml`.
4. Nichts ist fertig ohne Nachweis (`.agents/skills/verification/SKILL.md`).

## Wichtigste Befehle

```bash
./gradlew :app:assembleDebug && ./gradlew :app:testDebugUnitTest
cd console3d && npm run build
pytest backend/tests
```
