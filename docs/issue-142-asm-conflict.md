# Issue #142 — ASM-Konflikt: Plugin-Installation bricht Eclipse 2026-03

> **Status:** ✅ Fix umgesetzt (2026-10-06). Extern:
> github.com/sterlp/eclipse-peon-ai/issues/142.
> 🇬🇧 English: [issue-142-asm-conflict-en.md](issue-142-asm-conflict-en.md).

## Ursache (verifiziert 2026-10-06, p2-Metadaten)

Unser **Plugin-Bundle** enthält kein asm (MANIFEST `Bundle-ClassPath`/`Import-Package`, 60
lib-JARs, 0 Treffer `org/objectweb`). Der Konflikt entstand allein im **p2-Update-Site-Repo**:
`tycho-p2-repository-plugin` mit `includeAllDependencies=true` kopierte die komplette
Target-Platform in unser Repo — inkl. `org.objectweb.asm` 9.10.1 + commons/tree/util/analysis
(2026-09). Auf einem 2026-03-IDE (Platform-asm 9.9.1) zog p2 unser 9.10.1 nach → uses-constraint
(spifly → jsvg → swt.svg → workbench) → IDE startete nicht. Wir waren die **einzige Quelle** für
9.10.1. asm je Train: 2025-12 = 9.9.0 · 2026-03/06 = 9.9.1 · 2026-09 = 9.10.1.

## Fix (2026-10-06)

1. `releng/llmpeon-update-site/pom.xml`: `includeAllDependencies=true` entfernt — das Repo liefert
   nur noch Feature + Plugin; Platform-/Third-Party-Bundles kommen nie mehr in unser p2-Repo.
2. `MANIFEST.MF`: `jakarta.annotation [3.0.0,4.0.0)` → `[2.0.0,4.0.0)`. 2026-03 hat nur {1.3.5,
   2.1.1}; der asm-Leak lieferte bisher 3.0.0 mit — ohne ihn wäre das Plugin auf 2026-03 sonst
   nicht mehr auflösbar. Die genutzten Annotationen (@PostConstruct/@PreDestroy/@Nonnull/@Nullable)
   existieren in 2.1.1 unverändert.
3. `ui.console [3.16.0,…)`→`[3.15.0,…)`, `workbench.texteditor [3.20.0,…)`→`[3.19.0,…)` und Target
   `2026-09`→`2025-12` (Homepage-Minimum): Build gegen die *älteste* unterstützte Platform. Die
   zwei Bereiche waren nur auf 2026-03 kalibriert; keine API fehlt (javap: `EvaluationManager`,
   `JDIDebugModel`, `ILaunch.getDebugTargets`, `IJavaThreadGroup` zwischen 2025-12 und 2026-09
   gleich).

## Verifikation

- `mvn clean install` gegen 2025-12: BUILD SUCCESS.
- Generiertes p2-Repo: nur `org.sterl.llmpeon` + `llmpeon-feature`; 0 Treffer auf
  asm/objectweb/spifly/jsvg.
- OSGi-Suite 313: 2 Fehler — beide Windows-Environment (CRLF), target-unabhängig
  (`DebugSessionThreadsTest` Jackson-Pretty-CRLF vs. `\n`; `EclipseSearchFilesToolTest`
  `split("\n")` auf CRLF-Output). Core 1051 mit denselben 8 F/6 E wie auf unverändertem HEAD
  (Windows-Pfad/Timing) — nicht durch diesen Fix verursacht.

## Zweite Manifestation: `jakarta.annotation-api 3.0.0` → AI-Peon-View leer (2026-10-07)

Derselbe `includeAllDependencies`-Leak lieferte neben asm 9.10.1 auch
`jakarta.annotation-api 3.0.0` mit. Nach dem asm-Cleanup blieb es als **Orphan** in der
Installation (Datei + `bundles.info` + `artifacts.xml`, nicht mehr im p2-Profil). Folge:
Eclipse startet, aber die View bleibt leer, ohne Log-Eintrag.

- `org.eclipse.e4.core.di` importiert `jakarta.annotation [2,3)` → bindet **2.1.1**.
- `org.sterl.llmpeon` importiert `[2.0.0,4.0.0)` → bindet das **höhere 3.0.0**.
- e4 matcht `@PostConstruct` über **Annotation-`Class`-Identität**
  (`AnnotationProxy.isPresent` → `isAnnotationPresent(Class)`) → zwei verschiedene
  `PostConstruct`-Klassen → `AIChatView.createPartControl` wird nie aufgerufen (Konstruktor
  läuft, Lifecycle nicht — per Debugger-Stack `ReflectionContributionFactory → make` bestätigt).

Beseitigung + Anleitung für betroffene Nutzer:
[troubleshooting-stale-platform-bundles.md](troubleshooting-stale-platform-bundles.md) +
Skript `docs/clear-stale-platform-bundles.ps1`.

> Hinweis: Der breite Bereich `[2.0.0,4.0.0)` ist nötig (2026-03 nur 2.1.1, 2026-09 nur 3.0.0);
> die Divergenz tritt nur bei **zwei** Providern (Leftover) auf. Absicherung daher über die
> Update-Site/CI, nicht über einen Versionsbereich.

## Follow-ups

- [ ] Öffentlicher Korrektur-Kommentar auf Issue #142 (kann ich nicht posten).
- [x] Homepage-Mindestversion = 2025-12 (bereits korrekt).
- [x] Nutzer-Anleitung + Skript für Stale-Platform-Bundles (`docs/troubleshooting-stale-platform-bundles.md`).
- [x] CI-Assert: Update-Site liefert nur `org.sterl.*`-Plugins.
