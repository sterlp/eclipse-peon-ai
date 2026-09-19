# Issue #142 — ASM-Konflikt: Plugin-Installation bricht Eclipse 2026-03

> **Status:** 🚧 Analyse fertig, Entscheidungen offen (PO). Extern: 
> github.com/sterlp/eclipse-peon-ai/issues/142 (Reporter: User auf Eclipse 2026-03, Win).

## Verifizierter IST (2026-09-19, Code- und Repo-Lesung)

- Unser **Plugin bundled kein asm**: MANIFEST (Require-Bundle/Import-Package), 60 lib-JARs (0 Treffer auf
  `org/objectweb`), p2-Requirements des Plugin-IU (29, kein asm) — die frühere Issue-Analyse war insoweit
  **korrekt**.
- **ABER:** unser p2-Update-Site-Repo **enthält** `org.objectweb.asm 9.10.1` (+ commons/tree/tree.analysis/util,
  je 9.10.1) — Quelle ist die 2026-09-Target-Platform
  (`releng/llmpeon-target/llmpeon.target:6`), kopiert via `includeAllDependencies=true`
  (`releng/llmpeon-update-site/pom.xml:24-26`). Wir sind damit die **einzige Quelle** für asm 9.10.1 auf
  der User-Maschine. Die spätere Fremd-Analyse des Reporters („plugin bundles asm 9.10.1") ist in der
  Beobachtung richtig, in der Kausalität überzeichnet — **mein alter Issue-Kommentar („nicht unser Bug")
  ist unvollständig und braucht eine öffentliche Korrektur**.
- **Rest-Lücke (offen):** warum p2 bei einem reinen Feature-Install asm 9.10.1 aktiv nachzieht (User bekam
  nur asm-core 9.10.1, nicht die Familie). Plausibel: In-place-Downgrade 2026-09 → 2026-03 (Reporter hat
  genau das getan) — p2 räumt das alte asm 9.10.1 nicht aus → uses-constraint-violation (spifly → jsvg →
  swt.svg → workbench tot). Ohne User-`.profile` nicht verifizierbar.

## Fix-Kandidaten (keiner umgesetzt)

1. `includeAllDependencies` prüfen/reduzieren oder asm-Familie aus dem p2-Repo ausschließen — schlägt die
   Brücke zur ganzen Frage: welche Plattform-Bundles wollen wir überhaupt mitliefern?
2. `jdt.core [3.44.0,4.0.0)`-Range (`MANIFEST.MF:25`) absenken, damit ältere Platforms nicht gezwungen sind,
   2026-09er Bundles aus unserem Repo nachzuziehen.
3. **Mindest-Eclipse-Version dokumentieren** — de facto ≈ 2026-06 (getrieben durch jdt.core 3.44); Homepage
   sagt heute „2025-12 oder neuer" (zu niedrig).

## Follow-ups (Paul)

- [ ] Öffentliche Korrektur-Kommentar auf Issue #142 (kann ich nicht posten).
- [ ] Entscheidung Fix 1–3 → dann ADR (WARUM), Umsetzung im normalen Zyklus (kein Code hier).
- [ ] Homepage: Mindest-Eclipse-Version korrigieren.
