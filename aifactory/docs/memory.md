# Session-Stand — AI Factory (OAF)

Paul: **ein Thema pro Nachricht**, kurz. Nur Jon schreibt `docs/`. **Paul committet selbst** (Core);
Fabrik-Inkremente committet Dev je Schritt. **Core-Änderungen NUR mit Pauls Approval, per Proposal mit
WARUM** (2026-10-29) — gilt auch für Core-Tests.

## Jetzt — MVP-Zyklus auf `web-factory-mvp` (2026-11-01)

Paul freigegeben (autonom): „alles bekannte umsetzen, kleine Incremente, schönes UI, Jon im Web".
**Branch `web-factory-mvp`** von main (`f06a4d74`), Push läuft. Plan: `peon-plan/overview.md`
(Inc-4-Plan archiviert als `overview-inc4-done-2026-11-01.md`).

* **Inc-4-Review nachgeholt (Review 2026-11-01): CONCERNS, kein Blocker.** Da-Mek-Pinning, R6 und alle
  Inc-4-Tests halten dem Origin-Umbau stand (Fabrik 107/0/0, Core 1452/0/0, UI 25/4, lint 0/0).
  Befunde im Plan `overview.md` (Abschnitt a/c/d): **C-1** Symlink-Listing zeigt Ziel-Metadaten
  (`ProjectFilesController.java:164-168`), **C-2** Paritäts-Pin selbst-referentiell (Core-Test → nur mit
  Pauls Freigabe), **C-3** `chat ↛ project` im UI unmechanisch → oxlint-Regel, **C-4** Schreib-/Lesewurzeln
  an zwei Stellen (`JonToolSet.java:55` vs. `ProjectFileRoot.java:39-41`) → Gleichheits-Assertion. 5 LOW
  (u.a. Kommentar-Widerspruch `ProjectDirectoryDto.java:6-7`, id-Falschzitat). **Plan conflict §4.6**
  (entfallene `delegated`-Zeile) ist die alte Planseite — Code folgt chat.md.
* **Paul 2026-11-01 entschieden:** Kachel-Slide-out **ja** — Blick in den Chat von Dev/Plan/Review
  (Search ist kein Agent), schreibgeschützt, Default Jons Chat, **Stop im UI stoppt alles**. → Regel
  **R-WP-26 / UC-WP-49** in [chat.md](chat.md) (❌); UC-WP-40-Zweig entsprechend geschärft (Mitschnitt
  verboten **im Jon-Chat**, Einsicht nur über den Blick). Kachel-❓ aus open-points gezogen (🔒 V10).
* **Paco:** Prompt liegt im Core (`prompts/paco.md`, 81 Z., braucht `${docs}`-Resolve; Komposition
  `default + paco + doc-method` existiert noch nicht — Core-Doc `system-prompts.md:14-15` ❌). Paco ist
  **web-only** → nach Inc 5/6 prüfen: geht es fabrik-only (Core-Ressource laden + komponieren) ohne
  Core-Code? Sonst Proposal an Paul. Feature-Doc [paco.md](paco.md) R-PC-7/UC-PC-13.

## Backlog dieses Zyklus (kleine Incremente, je grün)

1. **Inc 5a** — Review-Härtung: C-1 (Symlink im Listing), C-3 (oxlint `chat ↛ project`), C-4
   (Schreibwurzeln == Lesewurzeln), LOW-Kommentare. Fabrik/UI-only.
2. **Inc 5b** — Kacheln (UC-WP-11/12/13, R-WP-7 🚧): Name, Kontextzahl, Status, Clear/Compact; Clear auf
   Jon kaskadiert.
3. **Inc 5c** — Blick (UC-WP-49): Slide-out je Kachel, RAM-Stand des Agenten lesen. **Vorab klären:** hat
   der Core schon einen Lese-Zugriff auf das Agenten-Gespräch (`AiAgent`-Memory)? Wenn nein → Core-Touch =
   Blocker, Story parken + Proposal an Paul (Approval-Pflicht 2026-10-29).
4. **Inc 6** — Nutzername (R-WP-17/R-WP-25, UC-WP-25/48): Name im Browser-Speicher, `[User <Name>]:` an
   Jon, Chat zeigt Namen statt „YOU", Prompt-Zeile Fabrik-only.
5. **Paco** — nach 5/6 (siehe oben).

## Code-Stand

| Inc | Inhalt | Stand |
|-----|--------|-------|
| 0–4 + 3f + Origin | Registry/Clone, Auslieferung, Config, Chat, Schreib-Pinning, Datei-Bereich, Herkunft | **auf main**, reviewed (CONCERNS s. o.) |
| 5a · 5b · 5c | Härtung · Kacheln · Blick | nicht gebaut |
| 6 | Nutzername (UC-WP-25/48) | nicht gebaut |
| danach | Paco, Config-Repo, UI-Seiten/Router | nicht gebaut |

## Fallstricke (gemessen)

* Baureihenfolge: Kern `clean test` → `install -DskipTests` → Fabrik `clean verify`; immer `clean`
  (Eclipse-Lombok-Stümpfe in `target/classes`).
* `MockLlmServer`: kein `getRequestCount()`, fängt keine `system`-Nachrichten; `@P(name=…)` für Tool-Namen.
* Guards `OneChatPerProjectTest`/`SharedProjectChatTest` (DTO-Feldliste + Verbotliste) nicht anfassen.
* Port 8080 = IDE → Nachweise auf 8099; `~/.peon` vor/nach vergleichen; `@TempDir` liegt auf macOS unter
  TMP → Test-Homes nach `target/` (nicht TMP).
* `lintDocsAndTests`: `root=/Users/sterlp/dev/workset/peon-ai`, `docRoots:["docs"]` (relativ zum Root!),
  `testRoots` Fabrik+UI, `idPattern UC-WP-\\d+`. Stand 2026-11-01: UC 48/48; ids 79/38; findings 10;
  VERWAIST 0.
* eclipseEditFile: `oldString`-Parameter wird bei langen Blöcken nicht übertragen → lange Edits über
  `eclipseWriteFile` (ganzes File) oder `eclipseReplaceLines` (Zeilennummer).
* `web-prototype.md` existiert nicht mehr (Restructure `703916ce`) — SOLL liegt in `chat.md`/`files.md`.
