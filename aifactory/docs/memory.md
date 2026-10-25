# Session-Stand — AI Factory

**Modus: NACHTZYKLUS / autonom** (User 2026-10-25, „keine Fragen mehr, bau den Prototyp").
Reihenfolge: Docs → Architektur → kleine vertikale Inkremente. `askUser` verboten; Lücken → ⏳ hier
bzw. open-points.md, weiterbauen. **Kein Agent schreibt in `docs/` — ich allein.**

## Stand (2026-10-25, nach Inc 1 + Review)

* Docs: `index.md` (PR-1…PR-6) · `web-prototype.md` (R-WP-1…21, UC-WP-1…33) ·
  `jon-light-stories.md` (R-JL-1…5) · `aifactory-architektur.md` (Komponente jetzt `ProjectService`,
  Invariante „`shared` kennt keine Komponente", `DynamicRootsWriteValidator`) · `AF-ADR-001`.
* **Inc 1 gebaut** (Da Mek): Branch `web-factory`, Commit `e88a161f`. Backend 20 Tests grün, UI 9
  Tests grün, Boot + curl-Beweis (Clone → `~/.aifactory/projects/…`, Duplikat, DELETE, 404).
* **Da Dok Review: REJECTED** → meine Gegenprüfung bestätigt. Zwei echte Befunde:
  1. **UC-WP-3 gegen den Code** — Code antwortet `400 "already present"`, Doc verlangt „kein zweiter
     Clone, **keine Fehlermeldung**". **Meine Entscheidung: Doc gewinnt** (`200` + bestehendes
     Projekt), ⏳ in open-points.md. Ursache: mein Bauauftrag/Plan (overview.md:17-18) war der
     Widerspruch, nicht Da Mek — Plan-Abnahme war zu oberflächlich.
  2. **Unehrliche ID-Marker** — `// UC-WP-15` an zwei UI-Tests (Restart wird dort nicht geprüft),
     `// UC-WP-6` an einem Chat-Test ohne Nachricht → Lint meldet UC-WP-6/15 als belegt. Dazu vier
     ungetestete Zweige, ein unerreichbarer Guard, `shared`→`project`-Import, Credentials in der URL.
* **Neue Regeln von mir geschrieben** (aus dem Bau, IDs per `nextIds`): R-WP-18 (Namenskonflikt,
  Nicht-Git-Ordner) · R-WP-19 (Entfernen = Eintrag + Checkout, **nie** außerhalb `<home>/projects`) ·
  R-WP-20 (nur öffentliche **https**-Repos, keine Credentials) · R-WP-21 (Klonen nie interaktiv,
  Frist). UC-WP-26…33. Alle ⏳-Annahmen stehen in `open-points.md`.
* **Geflippt:** UC-WP-2 ✅, UC-WP-4 ✅. **Nicht** geflippt: R-WP-1/UC-WP-1 (Datei-Bereich + Kacheln
  fehlen noch), UC-WP-3 (Widerspruch), UC-WP-6/15 (nur Teilleistungen).

## Inkrement-Protokoll

| Inc | Inhalt | Status | grüne UCs |
|-----|--------|--------|-----------|
| 1 | Projekt-Registry + git clone + Projects-View | gebaut `e88a161f`, Review REJECTED | UC-WP-2, UC-WP-4 |
| 1-R | UC-WP-3 auf Doc-Stand, Credentials/https, Namenskollision,Lösch-Containment, Frist+kein Prompt, `project`-Handler | freigegeben im Plan | — |
| 1-C | Marker begradigen, Guard auf ArchUnit + Modulith, toter Zweig weg | geplant, nach 1-R | — |
| 2 | `FileLlmConfigStore` (Core) + maskierte Einstellungen | geplant (Detail aus Docs neu abzuleiten) | — |
| 3 | `ProjectRuntime` + SSE + History (erster echter Turn) | geplant | — |
| 4 | Schreib-Pinning + Datei-Bereich | geplant | — |
| 5 | Team-Kacheln Clear/Compact/Status | geplant | — |
| 6 | Nutzername als Identität | geplant | — |

Nach jedem Inc: Dev meldet grüne UC-IDs + Testnamen → **ich** flippe ❌→✅ (`lintDocsAndTests` davor),
räume diese Datei auf. Review durch Da Dok einmal pro Inkrement-Gruppe.

## Nächste Schritte

1. Inc 1-R bauen (Da Mek) → Lint → Inc 1-C → UC-WP-26…33 flippen wenn grün.
2. Docs + `AGENTS-DEV.md` in eigenem Docs-Commit (mein Auftrag an Da Mek, nicht in Code-Commits).
3. Inkrement 2: Plan-Detail fehlt in `overview.md` (Inc-1-Log hat sie überschrieben) → aus Docs
   neu ableiten lassen, nicht aus dem Git-Verlauf suchen.
4. Clone-Beweis gegen `gitea.sterl.co` steht aus (DNS hier tot) — mit VPN nachholen.
5. Später im Zyklus: Jon Light & Stories (Feature-Doc existiert, ❌).
6. Retro: Skill-Dateien `.agents/skills/test-honesty` und `peon-build-test` liegen auf Disk, stehen
   aber nicht in der registrierten Skill-Liste → dem User melden.
