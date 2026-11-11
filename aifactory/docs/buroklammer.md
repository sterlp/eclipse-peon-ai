# Büroklammer — Dateien direkt in den Chat 🚧 (in design, Paul 2026-11-10)

Technische Heimat: (noch keine — Fabrik-UI + Chat-Ende; wird mit dem Design gesetzt)

## Ziel

Der Nutzer legt Dateien (Stories, Bilder, sonstige Files) **direkt in den Chat** — Drag & Drop bzw.
Anhang wie die „Büroklammer" aus der AI-Schulung — statt den Umweg über das Datei-Dateisystem zu
gehen. Jon (und sein Team) können die Files lesen und im Turn verwenden.

## Vorgestellt (Paul 2026-11-10, noch nicht entschieden)

* Ablageort der Drops: **`tmp/<projekt>/file-drops`** — außerhalb des Checkouts, damit bleibt die
  Invariante „Runtime-Zustand niemals im Checkout" unberührt ([Architektur](aifactory-architektur.md)).
* Eigene kleine Werkzeug-Familie für die Drops — Idee: **list · clear · read(?)**. ❓ Offen: brauchen
  wir eigene Tools, oder reichen die bestehenden `disk*`-Familien (R-WP-12), wenn die Drops unter
  `tmp/<projekt>/` liegen? Duplikat-Fallen: PR-2/PR-3 — nichts doppeln, was die Core-Familie schon
  kann; V7 („tmp/<projekt-name>") ist als ❓ in [Open Points](open-points.md) verwandt.
* Inline-Variante (Datei-Inhalt **direkt in die Chat-Message**) — **eigner Folgeschritt, nicht Teil
  dieser Story**: bräuchte eine File-Parsing-Lib, das wäre komplett neu. Erst mal nur aufgeschrieben.

## Offene Punkte

| Status | Punkt |
| --- | --- |
| ❓ | Ablageort `tmp/<projekt>/file-drops` — bestätigen (Kollision mit der TMP-Regel [R-WP-13](files.md) prüfen) |
| ❓ | Eigene Tools (list/clear/read) vs. bestehende `disk*`-Familie — Paritäts-/Duplikat-Check (PR-2/PR-3) |
| ❓ | Drag & Drop vs. Datei-Dialog (oder beides); was zeigt der Chat für ein Drop (Zeile? Kachel? Icon?) |
| ❓ | Löschen/Clear der Drops — automatisch nach dem Turn, manuell, oder bleiben sie liegen? |
| ❌ | Inline in die Chat-Message (File-Parsing) — eigener Folgeschritt, nach dieser Story |
