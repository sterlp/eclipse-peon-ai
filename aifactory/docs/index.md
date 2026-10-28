# AI Factory — Docs (Index)

**Ziel:** eine **Multi-User AI Factory** im Browser — sie baut gleichzeitig an mehreren Projekten, je
Projekt **ein** Jon mit seinem Team, und alle Nutzer eines Projekts sehen denselben Chat.
Agent-Logik kommt unverändert aus `llmpeon-core`, kein Eclipse/OSGi.

Dieser Index ist die Karte **dieses** Produkts: Links + Feature-Zeilen + Produktregeln.
Kein Protokoll, kein Code-Bestand.

Status: ✅ done · ◐ teilweise gebaut (Regeln mit ✅/❌ darunter) · 🚧 in design · ❌ specified (nicht
gebaut) · ⏳ geparkt · 📝 Studie.
Backlog = jedes ❌/🚧/◐.

## Wo das Wissen liegt

* **[Architektur](aifactory-architektur.md)** — das WIE: Komponenten, Turn-Besitz, Core-Abbildung,
  Invarianten. **Vor jedem Plan** zu lesen.
* **[Product Vision](product-vision.md)** — was Open AI Fabric (OAF, Jon@Web) ist und was nicht.
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

MVP-Features teilen sich das ID-Präfix `WP` (IDs bleiben beim Verschieben stabil). **Code nach
Core-Aufräumen 2026-10-27 bewusst defekt.**

* [UI](ui.md) — vier Seiten unter `/ui/`, Reload bleibt auf der Seite, Look. ◐ (Auslieferung ✅, Seiten/Router ❌)
* [Projekte](project.md) — Git-Checkout als Projekt: klonen, eindeutig, entfernen, nie interaktiv, Live-Status. ◐
* [Jon-Chat](chat.md) — ein Jon, ein Gespräch je Projekt: Streaming, Queue, Neustart, Tools, Kacheln. ◐
  (Herkunft je Zeile ❌, [AF-ADR-002](adr/002-origin-in-core-display-in-factory.md); Kacheln 🚧)
* [Dateien](files.md) — Schreiben nur in Projekt + TMP, Datei-Bereich lesend. ❌ (gebaut, nicht reviewt)
* [Config](config.md) — eigene Datei der Factory, Config-Seite maskiert, „überschreiben" je Agent. ◐
* [Nutzer](user.md) — Name statt Login, `[User Name]:` an Jon und Paco. ❌
* [Paco](paco.md) — fachlicher PO je Nutzer (grillt Vision/Ziele/SOLL), Stories/Proposals in `stories|proposals/<nutzer>/`,
  konfliktfrei oder mit benanntem Konflikt. ❌ specified (nach dem MVP)
* [Config-Repo](config-repo.md) — Fabrik-Config (Agenten, Skills, `projects.yml`) aus einem Git-Repo beim
  Start, Zugang über HTTPS-Token oder SSH aus der Umgebung; Verschlüsselung der Keys direkt nach dem MVP.
  ❌ specified (MVP; Verschlüsselung/Rotation danach)

## Noch nicht angelegt

[Glossary](glossary.md) — kommt mit dem ersten übergreifenden Begriff.
[ADRs](adr/index.md): unsere Web-ADRs liegen **hier** im eigenen Raum `AF-ADR-*`; nur eine ADR, die
den geteilten **Core** betrifft, liegt im globalen Raum
[`docs/adr/`](../../docs/adr/index.md). [Open Points](open-points.md) existiert.
