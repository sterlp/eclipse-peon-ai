# Merken, welcher Agent aktiv war

> **Status:** Save/Load **✅ gebaut (2026-10-31, Mini-Zyklus `web-factory-goon`, Inc 2)** · Restore
> **🚧 gebaut, Verifikation manuell** (Paul-Smoke unten) — **Architektur:** `org.sterl.llmpeon`,
> `parts/ai/PeonAiService` (Schreiben, Setzen, Restore) + `parts/config/ActiveAgentPreference` (Persistenz)

## Ziel

Die im Agenten-Dropdown gewählte Agentin überlebt Neustart **und** Workspace-Wechsel: nach dem
Eclipse-Neustart ist der Agent aktiv, mit dem zuletzt gearbeitet wurde — nicht jedes Mal Dev.

**WEIL:** Der Agentenwechsel ist die häufigste Steuerung im Chat, die Auswahl war aber reiner
Sitzungsstand. Sie gehört in den **Workspace**, nicht ins Config-Dir: welcher Agent hier gerade genutzt
wird, ist Arbeitszustand dieses Workspaces und wandert nicht mit dem Config-Verzeichnis auf andere
Rechner.

## Technische Heimat

| Stück | Ort |
|---|---|
| Merken bei echtem Wechsel | `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/ai/PeonAiService.java` → `rememberActiveAgent(AiAgent)` (gerufen von `setActiveAgent` **und** `onHandoff`) |
| Setzen / Restore beim Start | `PeonAiService.applyActiveAgent(AiAgent)` · `PeonAiService.restoreActiveAgent()` (letzter Schritt des Konstruktors) |
| Persistenz | `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/config/ActiveAgentPreference.java` |
| Tests | `org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/ActiveAgentPreferenceTest.java` (Save/Load) |

## Regeln

### R1 — Gemerkt wird nur der echte Namenswechsel 🚧 (Dropdown-Pfad manuell, Schreibregeln getestet)

- GIVEN Agent X ist aktiv, WHEN der User Agent Y wählt — im Dropdown **oder** durch den Handoff —
  THEN wird `Y` für den Workspace gemerkt → manuell (Smoke, Punkte 1 und 5)
- GIVEN Agent X ist aktiv, WHEN X erneut gewählt wird, THEN erfolgt **kein** Schreibvorgang
- GIVEN ein blanker Name soll gemerkt werden, WHEN gespeichert wird, THEN bleibt der bisher gemerkte
  Name stehen — leer ist keine Wahl
  → `ActiveAgentPreferenceTest.saveIgnoresBlankName`

### R2 — Restore beim Start, unbekannte Werte bleiben still 🚧 (manuell)

- GIVEN der Workspace hat „Jon" gemerkt, WHEN ein `PeonAiService` gebaut wird, THEN ist Jon der aktive
  Agent — auch Jon und Scaffold, weil sie zur Build-Zeit schon unter ihren Namen registriert sind
- GIVEN nichts ist gemerkt (frischer Workspace) **oder** der gemerkte Name gehört zu keinem geladenen
  Agenten, WHEN gebaut, THEN bleibt das bisherige Default-Verhalten (Dev) unverändert: kein Fehler,
  keine Warnung, kein Dialog

Kein automatischer Test in diesem Inkrement (Freigabe: Restore bleibt Manuell). Der Fall ist im
Test-Harness **erzeugbar** — ein zweiter `PeonAiService` im selben Workspace restored den gemerkten
Agenten (am Bau dieses Inkrements geprüft) —, ein Test darauf wäre also ehrlich; er steht als
Nachzieher aus. Was der Harness wirklich nicht prüft, ist der Neustart selbst → **Paul-Smoke**,
Punkte 1, 2 und 4.

### R3 — Der Restore schreibt nichts zurück 🚧 (baukonstruktiv, im Smoke mitzulesen)

- GIVEN der gemerkte Agent wird beim Start gesetzt, WHEN derselbe Setz-Pfad wie im Dropdown läuft,
  THEN wird der Wert nicht erneut geschrieben — kein Schreib-Loop, kein Flush beim Start.
  Umgesetzt, indem `restoreActiveAgent()` `applyActiveAgent` **ohne** `rememberActiveAgent` nutzt.

### R4 — Ein verschwundener Agent lässt den Wert verfallen

- GIVEN der gemerkte Agent existiert nach einem Reload nicht mehr (Custom-Agent entfernt oder
  umbenannt), WHEN neu gestartet wird, THEN greift das Default-Verhalten aus R2; der alte Wert bleibt
  stehen und wird beim nächsten echten Wechsel überschrieben — **kein** Aufräum-Job.

### R5 — Scope und Key ✅

`InstanceScope`-Knoten `org.sterl.llmpeon`, Key **`llm.activeAgent`**, Wert = Agent-Name. Kein
Config-Dir, keine Migration, kein Alias: ein leerer Wert bedeutet „nichts gemerkt".
→ `ActiveAgentPreferenceTest.blankOrMissingValueLoadsEmpty`, `…#savedAgentNameSurvivesARoundtrip`

## Bewusst nicht gebaut

* **Kein Flush-Test.** `save` = `put` + `flush` (Muster `EclipseLlmConfigStore`); dass der Wert die Disk
  erreicht, prüft kein Test — der einzige ehrliche Nachweis wäre ein Pfad-Check auf
  `.metadata/.plugins/org.eclipse.core.runtime/.settings/org.sterl.llmpeon.prefs`, der an
  Platform-Interna koppelt. Deshalb Smoke-Punkt 3.
* **Kein Throw beim Flush.** Ein `BackingStoreException` wird geloggt, nicht geworfen: der Wechsel ist
  dann schon passiert — eine unbeschreibbare Pref-Datei darf die UI-Aktion nicht schlagen.
* **Kein Präferenz-UI.** Das Merken ist eine Nebenwirkung des Dropdowns, nichts zum Konfigurieren.
* **Workspace-global, nicht pro View:** bei zwei geöffneten Chat-Views gewinnt der zuletzt gewählte
  Agent, beide Views starten nach einem Neustart mit ihm.

## Manueller Smoke (Paul)

1. **Jon** wählen → Eclipse neu starten → der Chat öffnet mit **Jon**.
2. Anderen Workspace nutzen → dort gilt der für **diesen** Workspace gemerkte Agent.
3. `.metadata/.plugins/org.eclipse.core.runtime/.settings/org.sterl.llmpeon.prefs` enthält
   `llm.activeAgent=<Name>` schon **vor** dem Neustart (der Flush ist der automatisiert ungeprüfte Teil).
4. Gemerkten Agenten entfernen (Custom-Agent löschen/umbenennen) → der nächste Start öffnet still mit
   **Dev**, ohne Fehlermeldung.
5. Ein Handoff merkt den neuen Agenten ebenfalls; ein bloßes erneutes Auswählen des aktiven Agenten
   schreibt nichts (Log: kein Pref-Schreibvorgang).
