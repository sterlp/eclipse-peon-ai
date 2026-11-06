# Resolved Points — AI Factory

Durchgangslager-Ablauf: hier bleibt nur, was ein Wiederaufmachen verhindert (Punkt · Entscheidung ·
Begründung · Datum); wertlose Zeilen werden gelöscht.

| Punkt | Entscheidung | Gegen Wieder-Aufmachen | Datum |
|---|---|---|---|
| C-4 Core-Erweiterung: „Anlegen = +Diff" & partzieller Diff statt Skip | **Core-Umbau gebaut** (Paul ließ ihn einbauen, gegen Jons Empfehlung „erst nur Doc schärfen"): `diskWriteFile` feuert `onFileUpdate` auch bei Neuanlage (`oldContent = ""`, reiner `+`-Diff), `SimpleDiff` kappt ab **500 Changed-Zeilen** partziell mit End-Marker statt zu skippen, **binärer Inhalt → Werkzeug-Zeile**; Core-Regeln: `org.sterl.llmpeon.core/docs/file-update-diff.md` (R-SD-1…3), Commit `2cac0d6f` | Der Diff-Umfang ist die Core-Oberfläche — kein zweiter Diff-Algorithmus und kein zweiter Cap in der Fabrik ([R-WP-36](chat.md)); Fabrik-Nachweis `aCreatedFileIsAPurePlusDiffOnTheWire` (`49571dc4`) | 2026-11-05 |
| Tastatur in der Chat-Eingabe (Enter vs. Umbruch) | **Enter sendet**; **`Shift+Enter` UND `Ctrl/Cmd+Enter` setzen eine neue Zeile** (Paul 2026-11-06, Wahl „A", **Umkehrung** seiner eigenen Regel vom 02.11. „Enter = Umbruch") | Begründung ist die Bewegung aus jedem Chat-Tool plus Pauls Lob an der Referenz-App; die alte Fassung steht nicht mehr als Regel im Repo — [R-WP-30/UC-WP-56](chat.md) ist der Maßstab, ein „Rückbau" auf Enter = Umbruch ist eine neue Entscheidung, keine Reparatur | 2026-11-06 |
