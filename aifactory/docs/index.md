# AI Factory — Docs (Index)

**Ziel:** eine **Multi-User AI Factory** im Browser — sie baut gleichzeitig an mehreren Projekten, je
Projekt **ein** Jon mit seinem Team, und alle Nutzer eines Projekts sehen denselben Chat.
Agent-Logik kommt unverändert aus `llmpeon-core`, kein Eclipse/OSGi.

Dieser Index ist die Karte **dieses** Produkts: Links + Feature-Zeilen + Produktregeln.
Kein Protokoll, kein Code-Bestand.

Status: ✅ done · ◐ teilweise gebaut (Regeln mit ✅/❌ darunter) · 🚧 in design · ❌ specified (nicht
gebaut) · ⏳ geparkt · 📝 Studie.
Backlog = jedes ❌/🚧/◐/❓.

## Wo das Wissen liegt

* **[Architektur](aifactory-architektur.md)** — das WIE: Komponenten, Turn-Besitz, Core-Abbildung,
  Invarianten. **Vor jedem Plan** zu lesen.
* **[Product Vision](product-vision.md)** — was Open AI Fabric (OAF, Jon@Web) ist und was nicht.
* **[Glossary](glossary.md)** — ein Begriff, eine Bedeutung. Definiert hier: **Jon** (bauender,
  technischer PO je Projekt) und **Paco** (fachlicher PO je Nutzer). Begriffe, die über ein einzelnes
  Doc hinausreichen, kommen hierher, bevor sie in einem Doc „erfunden" werden.
* **[Pauls Produktvision](../pauls-product-vision.md)** — Pauls Rohvision (*für Agenten nur zu lesen*).
  **Quelle des SOLL**, soweit keine bessere Idee abgestimmt ist (Paul 2026-10-27); verbindlich wird
  ein Punkt erst als Regel in einem Feature-Doc. Abweichungen stehen als ❓ in [Open Points](open-points.md).
* **[Peon-AI Root-Docs](../../docs/index.md)** — Register des `llmpeon-parent`-Repos: globale ADRs,
  Glossary, Architektur-Regeln, Open/Resolved Points.
* **[Core-Docs](../../org.sterl.llmpeon.core/docs/index.md)** — `llmpeon-core` (plain Maven,
  ohne Eclipse): Agenten · Context & Compact · Tool-Familien · Provider & Config · Skills & Prompts.

Nicht hier: alles Eclipse-/OSGi-spezifische (SWT-Views, Widgets, Preference-Pages, `eclipse*`-Tools).

## Produktregeln (SOLL)

* **PR-1 · Ein Jon, volles Team.** Pro Projekt genau ein Jon mit unverändertem Team (Plan, Dev,
  Review; Search bleibt wie im Plugin das Such-Werkzeug — Namen siehe
  [Agent-Dateien](../../org.sterl.llmpeon.core/docs/builtin-agent-prompt-tracked.md)). ✅
* **PR-2 · Nur Disk-Werkzeuge.** Die Factory betreibt die Core-Familien `disk*`, `web*`, `shell`,
  `docs*`; `eclipse*`-Tools und OSGi gibt es hier nicht. ✅ — [R-WP-12](chat.md).
* **PR-3 · Geteilter Core.** Was beide Consumer brauchen, entsteht im Core und das Plugin profitiert;
  Web-only-Schichten (HTTP/SSE, Server-Session, SPA) bleiben in der Factory. Duplikate der
  Agent-Logik in der Factory sind verboten. ✅ — Begründung:
  [AF-ADR-001](adr/001-factory-shares-core-disk-only.md).
* **PR-4 · Core bleibt Eclipse-frei.** Keine `org.eclipse`-Importe im Core; der Factory-Port ist der
  Beweis, nicht nur die Absicht. ✅ — der Port läuft, `ArchitectureGuardTest` hält die Grenze;
  fortschreitend [ADR-0046](../../docs/adr/0046-core-portability-review.md).
* **PR-5 · Je Projekt ein Jon, ein Chat für alle.** Inputs aller Nutzer laufen in die Queue *eines*
  Agenten; verschiedene Projekte laufen parallel. ✅ — Details
  [R-WP-15](chat.md).
* **PR-6 · Zwei POs: Jon technisch, Paco fachlich.** Jon baut (einer je Projekt), **Paco** schärft
  je Nutzer das SOLL und liefert Stories/Proposals, ohne den bauenden Jon zu stören. ❌ — [Paco](paco.md).

## Features

MVP-Features teilen sich das ID-Präfix `WP` (IDs bleiben beim Verschieben stabil).

* [UI](ui.md) — Rahmen: Top-Menü + Projektbaum im Sider (AntD), URL-stabil, Auslieferung unter `/ui/`, Look. ◐ (Auslieferung,
  **Sider + URL ✅ [R-WP-38/UC-WP-70](ui.md)**, **Look-Restart ✅ [R-WP-43/UC-WP-76/77](ui.md)**,
  **Design-Übernahme ✅ [R-WP-39/UC-WP-71](ui.md)** gebaut+reviewed 2026-11-07 (Inkremente 1–2 auf
  `web-factory`); **Sider-Baum ✅ [R-WP-44/UC-WP-78](ui.md)** (Klapp-Gruppen je
  Projekt, Chats als Einträge — Paul 2026-11-08), **AntD zuerst + wiederverwendbare Widgets ✅
  [R-WP-46/UC-WP-81](ui.md)**; offen bleiben Look-Reparaturen aus der
  Browser-Abnahme 2026-11-08 (in Inc 4 gebaut) und der Look am Browser; Chat-Aktionen-Buttons ✅
([R-WP-43](ui.md) Aktionen inkl. Composer, Inc 4); **Icon-Button-Widget ✅
[R-WP-59/UC-WP-96](ui.md)** (B2/B3, 2026-11-10), **Styling gehört zum Widget ❌
[R-WP-64/UC-WP-101](ui.md)** (Grundregel Paul 2026-11-11: AntD zuerst, eigenes CSS nur wo AntD nichts
gibt, gemeinsame Datei = nur Geteiltes; `styles.css` 1286 Zeilen → aufteilen), [open-points](open-points.md) Runde 4b)
* [Projekte](project.md) — Git-Checkout als Projekt: klonen, eindeutig, entfernen, nie interaktiv, Live-Status. ◐
* [Jon-Chat](chat.md) — ein Jon, ein Gespräch je Projekt: Streaming, Queue, Neustart, Tools, Kacheln. ◐
  (Herkunft je Zeile ✅ UC-WP-39/40; Kacheln ✅ R-WP-7, Blick je Agent ✅ R-WP-26, **Timeline als AntD-
  Widget inkl. Think ✅ R-WP-40/UC-WP-72** gebaut 2026-11-07; **Warte-Rahmen gelöscht, Arbeit am
  Status-Indikator ✅ [R-WP-31/45/UC-WP-65/79](chat.md)** gebaut+reviewed 2026-11-08 (Inc 4,
  Farb-/Maß-Abnahme am Browser steht aus); **Blasen: volle Breite,
  Höhe = Text ✅ [R-WP-29](chat.md)** + **Scroll/FloatButton ✅ [R-WP-35/UC-WP-80](chat.md)** (Inc 4,
  Look-Abnahme am Browser steht aus); **Resend am Send-Knopf ✅ [R-WP-30/UC-WP-57](chat.md)** +
  **Send nie deaktiviert ✅ [R-WP-41/UC-WP-74](chat.md)** (Runde 6, 2026-11-08); **Werkzeug-Schritte
  im Restore ✅ [R-WP-47/UC-WP-82](chat.md)** (A7, 2026-11-08); **je Turn genau ein Endereignis ✅
  [R-WP-48/UC-WP-83/84](chat.md)** (Nachtzyklus, 2026-11-09); **Blick im selben Chat-Widget, live ✅
  [R-WP-53/UC-WP-89/90](chat.md)** (C-7, 2026-11-09); **Problem-Meldung rot mit rotem Icon ✅
  [R-WP-61/UC-WP-98](chat.md)**, **Reconnect mit Backoff ✅ [R-WP-58/UC-WP-95](chat.md)** und
  **Kachel-Stand per Push ✅ [R-WP-60/UC-WP-97](chat.md)** (Bugs B6/B1/B5, 2026-11-10));
  **eine Chat-Aktion endet mit dem Neuaufbau des Gesprächs ✅ [R-WP-63/UC-WP-100](chat.md)** (B7b, Compact
  und Clear — kein neues Signal, Auslöser ist die Kachel-Fallkante, 2026-11-11); **Stop-Taste nur bei
  stoppbarem Turn ❌ [R-WP-65/UC-WP-102](chat.md)** (Paul 2026-11-11: Aussage im Kachel-Snapshot, Knopf
  sonst deaktiviert, abgelehnter Stop zeigt im Panel))
* [Dateien](files.md) — Schreiben nur in Projekt + TMP, Datei-Bereich lesend. ◐ (gebaut, reviewed 2026-11-01)
* [API-Fehler](api-errors.md) — Server-Fehler sichtbar: generierte API-Typen (typescript-generator),
  Bean Validation mit `fieldErrors`, ein Standard-Error-Widget (AntD), **Error-Panel je Seite prominent
  oben ✅ [R-WP-62/UC-WP-99](api-errors.md)**. ◐
  gebaut+reviewed 2026-11-09 (R-WP-54/55/56 + N1; UC-WP-92/93 mit Tests, R-WP-54 per M1/M2) und
  2026-11-10 (R-WP-62/UC-WP-99, `9d8c17bb` + Delta `ea4330ef`); offen: **Panel-Lebensdauer ❌
  [R-WP-66/UC-WP-103](api-errors.md)** (Paul 2026-11-11: eigener Retry schließt es, Turn-Fehler nicht,
  abgelehnter Stop zeigt) und die Browser-Abnahme der Feldabstände und des Panels)
* [Config](config.md) — eigene Datei der Factory, Config-Seite maskiert, „überschreiben" je Agent. ◐
  (Plugin-Parität F8 ✅ gebaut+reviewed 2026-11-09: R-WP-49–52 — Think (Default), Modell-AutoComplete
  mit Server-Liste, Max Tokens, Query-/Header-Params, je Agent Extra-Body + Tools-Allowlist)
* [Nutzer](user.md) — Name statt Login, `[User Name]:` an Jon und Paco.
  ◐ (Name, Präfix, Anzeige ✅ R-WP-17/R-WP-25; Paco ❌)
* [Paco](paco.md) — fachlicher PO je Nutzer (grillt Vision/Ziele/SOLL), Stories/Proposals in `stories|proposals/<nutzer>/`,
  konfliktfrei oder mit benanntem Konflikt. ❌ specified (nach dem MVP)
* [Büroklammer](buroklammer.md) — Dateien per Drag & Drop direkt in den Chat (`tmp/<projekt>/file-drops`);
  Inline-Parsing (in die Chat-Message) eigener Folgeschritt. 🚧 in design (Paul 2026-11-10, nur aufgeschrieben)
* [Config-Repo](config-repo.md) — Fabrik-Config (Agenten, Skills, `projects.yml`) aus einem Git-Repo beim
  Start, Zugang über HTTPS-Token oder SSH aus der Umgebung; Verschlüsselung der Keys direkt nach dem MVP.
  ❌ specified (MVP; Verschlüsselung/Rotation danach)

## Noch nicht angelegt

[ADRs](adr/index.md): unsere Web-ADRs liegen **hier** im eigenen Raum `AF-ADR-*`; nur eine ADR, die
den geteilten **Core** betrifft, liegt im globalen Raum
[`docs/adr/`](../../docs/adr/index.md). [Open Points](open-points.md) existiert.
