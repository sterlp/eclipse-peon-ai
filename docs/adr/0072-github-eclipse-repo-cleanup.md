# ADR-0072: Auflösung von github-eclipse-repo.md

## Status
✅ done — 2026-10-20

## Kontext
`github-eclipse-repo.md` (Repo-Root) war ein Mischdokument: Release-/Deploy-Prozedur,
Known Issues, Install-Anleitung und Future Ideas. Die Prozedur war veraltet (maven.yml deployt
nicht nach gh-pages; Version-Bump passiert via Tag-Trigger in release.yml). Die Install-Anleitung
existierte schon auf der Homepage — mit falscher Update-Site-URL.

## Entscheidung
Prozedur → Skill (`.agents/skills/peon-release/SKILL.md`), Install → Homepage, Status/Ideen →
open-points.md bzw. Backlog. Die Root-Datei wird gelöscht.

## Konsequenzen
- Kein Root-Mischdokument mehr; Rezepte leben in Skills (konsistent mit AGENTS-DEV).
- Homepage-URL wird korrigiert (`https://peon-ai-4e.sterl.org/` statt GitHub-Repo-URL).
- JAR-Signing + Auto-Create GitHub-Releases wandern als offene Punkte in `docs/open-points.md`.
- p2-Pfad-Verifikation auf peon-ai-4e.sterl.org als Open Point.
