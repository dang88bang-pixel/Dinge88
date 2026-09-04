# Skills

Skill-Packs nach dem [Agent-Skills-Standard](https://agentskills.io/home).
Der Agent lädt zunächst nur Name und Beschreibung; die vollständige `SKILL.md`
wird erst gelesen, wenn der Skill gebraucht wird (progressive disclosure).

| Skill | Auslöser | Zweck |
|-------|----------|-------|
| [`verification`](verification/SKILL.md) | implizit | Nachweis vor jeder Fertigmeldung |
| [`secureguard-ui-review`](secureguard-ui-review/SKILL.md) | implizit | Prüfliste für sichtbare Änderungen |
| [`compose-design-system`](compose-design-system/SKILL.md) | explizit | Compose-Oberflächen mit `Sg*`-Bausteinen |
| [`three-scene-review`](three-scene-review/SKILL.md) | explizit | Änderungen an der Three.js-Szene |
| [`action-protocol-change`](action-protocol-change/SKILL.md) | implizit | Neue/geänderte Gerätebefehle |
| [`release-apk`](release-apk/SKILL.md) | explizit | Signiertes Release-APK |
| [`systematic-debugging`](systematic-debugging/SKILL.md) | implizit | Ursachensuche statt Symptomkosmetik |
| [`tdd`](tdd/SKILL.md) | explizit | Test zuerst für Geschäftslogik |

„Implizit" bedeutet: Der Agent soll den Skill von sich aus anwenden, sobald die
Situation zutrifft. „Explizit" bedeutet: auf Anforderung oder wenn der
passende Dateibereich berührt wird.

## Aufbau eines Skills

```
.agents/skills/<name>/
├── SKILL.md              # Frontmatter (name, description) + Anleitung
└── agents/openai.yaml    # Routing-Metadaten für OpenAI-kompatible Runtimes
```

## Andere Runtimes anbinden

```bash
# Claude Code
mkdir -p .claude && ln -sfn ../.agents/skills .claude/skills

# OpenClaw / generisch
ln -sfn .agents/skills skills
```

## Neuen Skill anlegen

1. Verzeichnis `.agents/skills/<name>/` erstellen.
2. `SKILL.md` mit Frontmatter `name` und `description` schreiben.
   Die Beschreibung muss sagen, **wann** der Skill anzuwenden ist — daran
   entscheidet der Agent, ob er die Datei überhaupt öffnet.
3. `agents/openai.yaml` mit denselben Feldern plus `version` und `invocation`.
4. Diese Tabelle und `AGENTS.md` Abschnitt 8 ergänzen.

Ein Skill beschreibt einen Ablauf, kein Wissen. Wissen gehört nach
`agent-os/Knowledge/`.
