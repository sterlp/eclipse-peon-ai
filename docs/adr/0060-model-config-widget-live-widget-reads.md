# ADR 0060 — Model Config Widget: Ping/Reload lesen Live-Widget-Werte, Reload persistiert nicht

**Status:** Accepted (2026-09-28, Paul) · **Fachdoc:** [model-config-widget.md](../model-config-widget.md) · **Löst ab:** R-ML2-Entscheidung vom 2026-09-12 (Refresh liest Store)

## Context

R-ML2 (2026-09-12) legte fest: der Modell-Liste-Refresh liest die **gespeicherte** Connection
aus dem Preference-Store — getippte Änderungen wirken erst nach Apply/OK. Der „Check Host and
Port"-Button daneben liest dagegen die Live-Widget-Werte (`AiConfigPreferenceView:121`). Diese
Asymmetrie macht die Config-Seite zum Testen unbenutzbar: man tippt eine URL ein und muss erst
die Seite verlassen, bevor man das Modell testen kann. Ursache ist das JFace-`FieldEditorPreferencePage`-Muster:
der Store wird erst bei `performOk`/`doPersist` aktualisiert — Buttons, die `getPreferenceStore().getString(...)`
lesen, bekommen den letzten **gespeicherten** Wert, nicht die aktuelle Eingabe (Stale-Read-Muster,
dokumentiert im Skill „Eclipse Preferences Development").

## Decision

1. **Live-Read statt Store-Read:** Ping & Reload bauen ihre Verbindungs-Identity zur Klick-Zeit
   aus den **Widget-Werten** (Parität zu Check-Host). R-ML1 (Fetch-Identity zur Fetch-Zeit) bleibt
   — nur die Quelle der Identity ändert sich: Widget statt Store-Snapshot.
2. **Reload = nur Fetch, kein Persistieren.** Apply/OK bleibt der einzige Save-Pfad; Cancel
   verwirft die Eingaben unverändert. Eine gecachte Modell-Liste unter einer nie persistierten
   Identität ist harmlos — der Cache hält nur Listen, keine Konfiguration (ADR-0034 unverändert).
3. **Widget-Extraktion `ModelConfigWidget`:** Provider · URL · API Key · Think-Level + Model-Combo
   + Ping + Reload in einem Composite. **Scope des ersten Baus: nur die Basic-Page** (2026-09-28,
   Paul); die Wiederverwendung in der Advanced-Page ist zurückgestellt, aber das Widget wird von
   Anfang an Suppliers/Callbacks statt Store-Zugriff nehmen (ADR-0005-Spirit), damit der spätere
   per-Agent-Reuse ohne Umbau möglich ist. Die vier Felder sind genau die Verbindungs-Identität —
   wer eines ändert, braucht den Reload.
4. **JSON extra body bleibt außerhalb** des Widgets — kein Teil der Identity, eigene UI-Beispiele;
   lieber mehrere fokussierte Widgets kombinieren als einen alles fressenden Block.
5. **Ping bleibt Host+Port-Check** (Semantik unverändert); Key-Validität testet implizit der
   Reload-Fetch (401 → sichtbarer Fehler). Kein dritter Button.

## Consequences

- Die alten R-ML2-BDDs („Liste kommt vom alten URL ohne Apply") gelten nicht mehr — model-loading.md
  verweist auf R-MCW-2/R-MCW-3.
- Widget nimmt Suppliers/Callbacks, kein Store-Zugriff im Widget (ADR-0005-Spirit: Widget owns state).
- Ein User-Smoke mit ungespeicherten Eingaben ersetzt die alte 2026-09-12-Verifikation.
- Wissen über das Stale-Read-Muster lebt als eigener Skill (Eclipse Preferences Development),
  nicht in eclipse-dpe (dorthin gepflanzte Quick-Section wird verlagert).
