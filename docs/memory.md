# Session-Stand (2026-09-10, Zyklus `story/lib-update-2026-09-09` ABGESCHLOSSEN)

## Dependency-Update-Zyklus — ✅ done, Review CONCERNS verarbeitet, Merge = User

Branch `story/lib-update-2026-09-09` (5 Commits): inc-1 `fe761f8` (Housekeeping) · inc-2
`89531af` (Target 2026-09 + jakarta.annotation [3.0.0,4.0.0)) · inc-3 `8706406` (langchain4j
1.20.0 + beta30 + McpService-Fix) · inc-4 `01a056a` (Lib-Bumps gleiche Major-Line,
slf4j-Entdoppelung) · `64135b2` (memory.md). Gates: Core Surefire **699/0** · OSGi **194/0**.
SOLL = [ADR-0044](adr/0044-target-2026-09-dependency-update.md) inkl. Addendum (2026-09-10):
MCP-HTTP/SSE → StreamableHttp (Legacy-2024-11-05-Server verbinden nicht mehr — Release-Note!),
mutiny-zero bewusst unwhitelisted, lib/ gitignored by convention.

**User-Handlungen offen:**
1. Smoke-Test nach Eclipse-Neuinstallation (User macht das jetzt).
2. Merge/Squash → main = User-Entscheidung.
3. Homepage-Release-Notes (User prüft) — Entwurf: „Target Platform 2026-09, langchain4j 1.20.0,
   MCP über Streamable HTTP (Legacy-HTTP/SSE-Server fallen weg), Lib-Updates (jakarta.annotation 3.0)."
   Plus Story/133-Zeile (Skills) falls noch nicht veröffentlicht.

**Neu im Bug-Hunt-Backlog (roter Test zuerst, User-Präferenz Memory #22):**
- **Fixture-Bug (Da Dok, Review 2026-09-10):** `PeonAiServiceTest.java:1444/1501` löscht via
  `deleteRecursively(fixtureSkillsDir.getParent())` das getrackte
  `test_project/.agents/skills/test/SKILL.md` nach JEDEM vollen OSGi-Suite-Lauf (2× im Zyklus
  passiert, per `git restore` behoben). Fix-Vorschlag: Cleanup auf den eigenen tmp-Bereich
  begrenzen statt auf das Fixture-Parent.
- Bestehend: Memory-Leak-Hunt (Eclipse läuft voll) · Triage #5–#15 + ApiRetry-Verdacht +
  AgentOrder-Edge.

## Nächste Zyklen (Reihenfolge offen, User entscheidet)

1. Bug-Fix-Zyklus (siehe Backlog oben).
2. Loop-Bewährung beobachten → Wanderung in Built-in-Prompts = User-Schritt, später.
3. ❓ Glossar eager · ❓ buildWithDev-Compact (open-points.md) · M2 „slot"-Drift (kosmetisch).

## Geparkt / Referenz

- ⏳ IDE-Target-rot-Punkt (open-points.md) → **hinfällig** (Eclipse-Neuinstallation behoben).
- Story/133 gemerged (Squash `e55668b`). Release-Notes-Zeile: Skills `.agents/skills` +
  CRUD-Evolutions-Loop + Usefulness-Footer; Scaffold-Write refresht Skills; Jon liest Skills.
- Geparkt: Query-Caches, Streaming-Präzisierungen, Edit-Tools, copy-tools-e2e
  `diskRenameResource`-Korrektur, Dropdown-Umbau (Klassen gelöscht, aus Git-Historie holbar).
