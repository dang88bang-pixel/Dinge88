Alle Anweisungen für diesen Workspace stehen in @AGENTS.md.

Zusätzlich für Claude Code:
- Skills liegen kanonisch unter `.agents/skills/`. Einmalig verlinken:
  `mkdir -p .claude && ln -sfn ../.agents/skills .claude/skills`
- Vor „fertig" immer den Skill `verification` anwenden.
