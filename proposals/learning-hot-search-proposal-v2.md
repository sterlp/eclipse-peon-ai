# Proposal: `learning-hot-search` — Heißer Such-Kontext als ContextItem

**Status:** 🚧 design  
**Ziel:** Der Such-Agent hält immer genau **ein** `ContextItem` mit Tag `last-search`: den letzten Such-Prompt + die finale AI-Antwort (nur Text). Bei jeder erfolgreichen Suche wird dieses Item **komplett ersetzt** — nie mehr als ein Slot, kein Memory-Wachstum, kein Compaction-Druck.

---

## 1. Kernidee

Der Such-Agent bekommt bei jeder neuen Suche den Kontext der **unmittelbar vorherigen** Suche — nicht als Memory-Nachricht, nicht als Such-Historie, sondern als **dediziertes ContextItem** mit Tag `last-search`:

```
ContextItems des Such-Agenten (immer genau 1 Eintrag mit Tag "last-search"):
┌─────────────────────────────────────────────────┐
│ //-- Start last-search                          │
│ Letzte Suche: "Wie konfiguriert man async in    │
│   Spring Boot?"                                 │
│                                                 │
│ Die wichtigsten Ansätze sind @Async,            │
│ ThreadPoolTaskExecutor, @EnableAsync …          │
│ //-- End last-search                            │
└─────────────────────────────────────────────────┘
```

**Bei der nächsten Suche:**
1. Neuer Prompt kommt als UserMessage (normaler Dialog).
2. Such-Agent sieht das `last-search`-ContextItem als Arbeitskontext.
3. Nach erfolgreicher Suche wird das Item **komplett ersetzt**: neuer Prompt + neue finale Antwort.
4. Es bleibt immer genau **ein** `last-search`-Item — kein Wachstum, kein Compact nötig.

---

## 2. Was genau gespeichert wird

| Bestandteil | Gespeichert? | Begründung |
|---|---|---|
| Such-Prompt | ✅ ja (im Item-Text) | Der Anker für Anaphern/Kurzfragen („und in Java?“) |
| Finale AI-Antwort (nur Text) | ✅ ja (im Item-Text) | Die Destillation — was der Agent daraus gelernt hat |
| Rohdaten der Suche (Trefferliste, URLs, Snippets) | ❌ nein | Die finale Antwort enthält bereits das Wesentliche |
| Thinking / Reasoning | ❌ nein | Interne Zwischenschritte, kein Wert für die nächste Suche |
| Tool-Result (webSearch-Return) | ❌ nein | Bleibt im Tool-Loop, wandert nicht ins ContextItem |
| Ältere Suchen | ❌ nein | Immer nur das letzte Paar — „hot“, nicht „history“ |

**Kernregel:** Das ContextItem ist ein **Single-Slot-Cache** für den letzten erfolgreichen Such-Zyklus — nicht ein Gedächtnis, nicht eine Historie.

---

## 3. Warum ContextItem statt Memory-Nachrichten

| Kriterium | ContextItem (dieser Vorschlag) | 2 Nachrichten im Memory |
|---|---|---|
| Semantische Trennung | hoch — explizit als „Arbeitskontext“ getaggt | niedrig — vermischt sich mit Dialog |
| Compact-Kontrolle | hoch — bestehendes Tagged-Results-System | niedrig — Sonderfall nötig |
| Memory-Seiteneffekte | keine — Memory-Logik unangetastet | ja — size, tokens, guards betroffen |
| Wiederverwendbarkeit | hoch — jedes Tool kann ContextItems liefern | niedrig — such-spezifisch |
| Implementierungsaufwand | gering-mittel | gering, aber mehr Sonderfälle |

**Entscheidung:** ContextItem gewinnt, weil es die Semantik sauber trennt (Kontext ≠ Konversation), das bestehende Compact-/Tagged-Results-System nutzt, und keine Memory-Sonderfälle erzeugt.

---

## 4. Implementierung

### 4.1 ContextItem-Struktur

```java
// Nach erfolgreicher Suche:
contextItems.put("last-search", new SimpleContextItem(
    "last-search",
    "Letzte Suche: '" + query + "'\n\n" + finalAnswerText
));
```

- **Ein Item** (nicht zwei) — Prompt + Antwort in einem Textblock, Zusammenhang bleibt erhalten.
- **Tag `last-search`** — eindeutige Identifikation für Compact und Agent.
- **Bei jeder neuen Suche ersetzt** — `put` überschreibt den alten Slot.

### 4.2 Integration in `SearchAgentTool`

```java
@Tool("Search the web. The previous search prompt and its final answer are provided as a 'last-search' context item — use it to refine follow-up searches.")
public String webSearch(String query, Integer maxResults) {
    // 1. Suche ausführen (bestehender Pfad)
    var result = engine.search(...);

    // 2. Finale AI-Antwort extrahieren (nur Text, kein Thinking)
    String finalAnswer = extractFinalText(result);

    // 3. ContextItem ersetzen — genau 1 Slot
    contextItems.put("last-search", new SimpleContextItem(
        "last-search",
        "Letzte Suche: '" + query + "'\n\n" + finalAnswer
    ));

    // 4. Return wie bisher (Trefferliste für den aktuellen Loop)
    return renderHits(result);
}
```

**Wichtig:** Das ContextItem wird **nach** der erfolgreichen Suche ersetzt, nicht davor — sonst würde ein Fehlschlag den Kontext zerstören.

### 4.3 Was der Agent sieht

Vor der nächsten Suche sieht der Such-Agent im Kontext:

```
//-- Start last-search
Letzte Suche: "Wie konfiguriert man async in Spring Boot?"

Die wichtigsten Ansätze sind @Async, ThreadPoolTaskExecutor, @EnableAsync …
//-- End last-search
```

Darauf aufbauend formuliert er die nächste Suche (z. B. „Spring Boot WebFlux async configuration“) — mit dem Wissen, was er schon gefunden hat.

---

## 4. Compact-Verhalten

Das `last-search`-Item hängt an deinem bestehenden Tagged-Results-System:

- **Tag-Präfix** (`last-search`) ist der Klassifikationsschlüssel — Compact kann es gezielt behandeln.
- **Optionen:**
  - **Priorisieren:** Item bleibt bei Compact erhalten (wichtigster Kontext).
  - **Kürzen:** Bei Platzmangel wird nur der Prompt + erste N Zeichen der Antwort behalten.
  - **Droppen:** Bei extremem Platzmangel wird es komplett entfernt (nächste Suche startet „blind“).
- **Empfehlung:** Erst **priorisieren** (Item bleibt), Kürzen/Droppen nur bei Bedarf.

**Kein Sonderfall im Compact nötig** — das bestehende Tagged-Results-System (`TaggedResult`, `head`-Dedup, Cap-Steuerung) deckt das ab.

---

## 5. Vergleich zu bestehenden Patterns

### Claude Code

- **Plan Mode / Session-Memory:** Persistiert Pläne/Kontext als **Dateien**, nicht als ContextItems [web:17][web:29].
- **Subagent-Handoffs:** Strukturierte Übergabe über Dateien [web:22][web:25].
- **Kein direktes Äquivalent:** Claude Code hat keinen „Single-Slot-Hot-Context“ für Suchen.

### OpenCode

- **Permissions/Tool-Hooks:** Fokus auf Zugriffs kontrolle, nicht auf Such-Kontext [web:3][web:10].
- **Kein Memory-Konzept für Suchen:** Verlässt sich auf Chat-Kontext, der wächst.

**Fazit:** Dein „Hot-Slot“-Ansatz ist **schlanker als beide** — kein Datei-Overhead, kein Historien-Wachstum, ein dedizierter Slot mit klarer Semantik.

---

## 6. Paketstruktur

**Kein neues Package nötig** — die Änderung ist minimal und in `SearchAgentTool` integriert:

```
org.sterl.llmpeon.core/
└── src/main/java/org/sterl/llmpeon/tool/tools/
    └── SearchAgentTool.java   // erweitert um last-search ContextItem
```

**Alternativ (falls ausgelagert):** `search/LastSearchContext.java` als dünne Helper-Klasse — erst bei Bedarf.

**Empfehlung:** Erst schlank (in `SearchAgentTool` integriert), Auslagerung nur wenn ein zweiter Anwendungsfall dazukommt.

---

## 7. Blast Radius — minimal

### Geändert

| Datei | Änderung |
|---|---|
| `SearchAgentTool.java` | Hält/ersetzt `last-search`-ContextItem nach erfolgreicher Suche |
| `web-tools.md` oder `search-agent.md` | Doc: beschreibt das Hot-Slot-Verhalten |

### Neu (optional)

| Datei | Zweck |
|---|---|
| `LastSearchContextTest.java` | Test: Item wird ersetzt, nie mehr als 1 Slot, Tag korrekt |

### Unverändert

- `webSearch`-Return-Format (Trefferliste) — bleibt.
- `webFetchAsMarkdown` / `webGet` — bleiben.
- `ThreadSafeMemory` / Compact-Logik — bleiben (ContextItem ist separater Kanal).
- `TaggedResult` / `SimpleContextItem` — bestehende Klassen werden genutzt, nicht geändert.

**Gesamt: 1–2 geänderte Dateien, 0–1 neue Test-Datei.** Minimaler Blast Radius.

---

## 8. Offene Punkte

*(Keine — Design ist eindeutig: 1 ContextItem mit Tag `last-search`, Prompt + finale AI-Antwort im Item-Text, bei jeder erfolgreichen Suche ersetzt, Compact priorisiert das Item.)*

---

## 9. Nächste Schritte

1. **Freigabe dieses Proposals** (keine offenen Fragen).
2. **Implementierung:** `SearchAgentTool` erweitern um `last-search`-ContextItem + Test.
3. **Doc-Update:** `web-tools.md` / `search-agent.md` beschreibt das Verhalten.
