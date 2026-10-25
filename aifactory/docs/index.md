# AI Factory — Docs (Index)

**Ziel:** Jon (Peon-PO) als **Web-Anwendung** — Spring Boot Backend + SPA im Browser statt
Eclipse-Plugin. Agent-Logik wird nicht neu erfunden, sie kommt aus `llmpeon-core`.

Dieser Index ist die Karte **dieses** Produkts: Links + Feature-Zeilen + Produktregeln.
Kein Protokoll, kein Code-Bestand.

Status: ✅ done · 🚧 in design · ❌ specified (nicht gebaut) · ⏳ geparkt · 📝 Studie.
Backlog = jedes ❌/🚧.

## Wo das Wissen liegt

* **[Peon-AI Root-Docs](../../docs/index.md)** — Register des `llmpeon-parent`-Repos:
  ADRs (globaler Nummernraum), Glossary, Architektur-Regeln, Open/Resolved Points.
* **[Core-Docs](../../org.sterl.llmpeon.core/docs/index.md)** — `llmpeon-core` (plain Maven,
  ohne Eclipse): Agenten (PO/Plan/Dev/Review) · Context & Compact · Tool-Familien
  (`disk*`/`web*`/`shell`/`docs*`) · Provider & Config · Skills & Prompts · Infra.

Nicht hier: alles Eclipse-/OSGi-spezifische (SWT-Views, Widgets, Preference-Pages, `eclipse*`-Tools).

## Produktregeln (SOLL)

* **PR-1 · Ein Agent, volles Team.** Die Web-App kennt genau einen Agenten: Jon. Keine
  Agent-Auswahl, keine weiteren Built-in- oder Custom-Agenten. Jons Team bleibt unverändert erhalten
  (Da Thinka, Da Mek, Da Dok, Da Sniffa) — ein abgespeckter Jon wäre eine zweite Agent-Definition und
  damit durch PR-3 verboten. Die UI zeigt zuerst nur Jons Chat; Delegationen sind im Tool-Log
  sichtbar, eine eigene Team-Ansicht ist eine spätere Story. ❌
* **PR-2 · Nur Disk-Werkzeuge.** Die Factory betreibt die Disk-Familie (`disk*`, `web*`, `shell`,
  `docs*`); `eclipse*`-Tools und OSGi gibt es hier nicht. ❌
* **PR-3 · Geteilter Core.** Was beide Consumer brauchen, entsteht im Core und das Plugin profitiert;
  Web-only-Schichten (HTTP/SSE, Server-Session, SPA) bleiben in der Factory. Duplikate der
  Agent-Logik in der Factory sind verboten. ❌ — Begründung:
  [AF-ADR-001](adr/001-factory-shares-core-disk-only.md).
* **PR-4 · Core bleibt Eclipse-frei.** Keine `org.eclipse`-Importe im Core; der Factory-Port ist der
  Beweis, nicht nur die Absicht. ❌ — fortschreitend [ADR-0046](../../docs/adr/0046-core-portability-review.md).

## Features

_noch keine — kein Feature ist in diesem Docs-Baum spezifiziert._

## Noch nicht angelegt

[Glossary](glossary.md) — kommt mit dem ersten übergreifenden Begriff.
[ADRs](adr/index.md): unsere Web-ADRs liegen **hier** im eigenen Raum `AF-ADR-*`; nur eine ADR, die
den geteilten **Core** betrifft, liegt im globalen Raum
[`docs/adr/`](../../docs/adr/index.md). [Open Points](open-points.md) existiert.
