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
([R-WP-43](ui.md) Aktionen inkl. Composer, Inc 4), [open-points](open-points.md) Runde 4b)
* [Projekte](project.md) — Git-Checkout als Projekt: klonen, eindeutig, entfernen, nie interaktiv, Live-Status. ◐
* [Jon-Chat](chat.md) — ein Jon, ein Gespräch je Projekt: Streaming, Queue, Neustart, Tools, Kacheln. ◐
  (Herkunft je Zeile ✅ UC-WP-39/40; Kacheln ✅ R-WP-7, Blick je Agent ✅ R-WP-26, **Timeline als AntD-
  Widget inkl. Think ✅ R-WP-40/UC-WP-72** gebaut 2026-11-07; **Warte-Rahmen gelöscht, Arbeit am
  Status-Indikator ✅ [R-WP-31/45/UC-WP-65/79](chat.md)** gebaut+reviewed 2026-11-08 (Inc 4,
  Farb-/Maß-Abnahme am Browser steht aus); **Blasen: volle Breite,
  Höhe = Text ✅ [R-WP-29](chat.md)** + **Scroll/FloatButton ✅ [R-WP-35/UC-WP-80](chat.md)** (Inc 4,
  Look-Abnahme am Browser steht aus))
* [Dateien](files.md) — Schreiben nur in Projekt + TMP, Datei-Bereich lesend. ◐ (gebaut, reviewed 2026-11-01)
* [Config](config.md) — eigene Datei der Factory, Config-Seite maskiert, „überschreiben" je Agent. ◐
* [Nutzer](user.md) — Name statt Login, `[User Name]:` an Jon und Paco.
  ◐ (Name, Präfix, Anzeige ✅ R-WP-17/R-WP-25; Paco ❌)
* [Paco](paco.md) — fachlicher PO je Nutzer (grillt Vision/Ziele/SOLL), Stories/Proposals in `stories|proposals/<nutzer>/`,
  konfliktfrei oder mit benanntem Konflikt. ❌ specified (nach dem MVP)
* [Config-Repo](config-repo.md) — Fabrik-Config (Agenten, Skills, `projects.yml`) aus einem Git-Repo beim
  Start, Zugang über HTTPS-Token oder SSH aus der Umgebung; Verschlüsselung der Keys direkt nach dem MVP.
  ❌ specified (MVP; Verschlüsselung/Rotation danach)

## Noch nicht angelegt

[ADRs](adr/index.md): unsere Web-ADRs liegen **hier** im eigenen Raum `AF-ADR-*`; nur eine ADR, die
den geteilten **Core** betrifft, liegt im globalen Raum
[`docs/adr/`](../../docs/adr/index.md). [Open Points](open-points.md) existiert.
