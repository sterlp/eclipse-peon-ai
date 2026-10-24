# Proposal: Agenten-Herkunft pro Nachricht — `origin` im Message-Modell

**Status:** 🚧 design  
**Ziel:** Jede Nachricht, die das UI anzeigt (AI-Text, Tool-Zeile, Problem), trägt ihre **Herkunft** (Agenten-Name) im Core-Message-Modell. Das UI entscheidet selbst, wo es den Namen anzeigt — bei Nachrichten ohne klarem Agenten-Bezug bleibt er `null`/leer, und das UI blendet die Überschrift aus.

---

## 1. Motivation

Heute zeigt `chat.html` vor jeder `.message.AI` einen generischen grünen Namen:

```css
.message.AI::before {
    content: "Peon";
    color: #1a7f37;
}
```

**Probleme:**
- Der Name ist hartkodiert und sagt nichts über den tatsächlichen Absender.
- Bei Multi-Agent-Szenarien (Jon delegiert an Da Mek/Da Dok) ist nicht erkennbar, **wer** gerade gesprochen hat.
- Tool-Zeilen (`🗜 compacted`, `🔍 grep`, `📜 Reading`) haben gar keine Überschrift — der User sieht nicht, welcher Agent die Aktion ausgelöst hat.

**Ziel:** Der Core liefert die Herkunft, das UI entscheidet über die Darstellung. Einfachheit siegt: ein Feld, ein Fallback-Verhalten, keine Agenten-Logik im CSS/HTML.

---

## 2. Kernentscheidung

**Core liefert `origin` (Agenten-Name) pro Nachricht. Das UI rendert ihn, wo es eine Überschrift hat.**

- **Mit Agenten-Bezug** (AI-Text eines Agenten, Tool-Zeile eines Agenten, Problem eines Agenten): `origin` = Agenten-Name (z. B. `"Da Mek"`, `"Jon"`).
- **Ohne Agenten-Bezug** (reine System-Meldung, Fehler ohne Agent-Kontext): `origin` = `null`/leer.
- **UI-Regel:** `origin == null/leer` → **keine Überschrift** (kein fake Name, kein generisches „Peon“).

**Bewusste Entscheidung:** Kein Fallback-Name wie `"Peon"` oder `"System"` — das wäre ein fake Agenten-Name. Sauberer ist: kein Name = keine Überschrift.

---

## 3. Core-Änderung: `origin` im Message-Modell

### 3.1 `SimpleMessage` erweitern

```java
public record SimpleMessage(
    Type role,
    String message,
    @Nullable String origin   // NEU: Agenten-Name oder null
) {
    // Bestehende Konstruktoren behalten Kompatibilität (origin = null)
    public SimpleMessage(Type role, String message) {
        this(role, message, null);
    }
}
```

- **`origin`** = Agenten-Name (`"Da Mek"`, `"Jon"`) oder `null`.
- **Bestehende Aufrufer** funktionieren weiter (2-arg Konstruktor setzt `origin = null`).
- **Neue Aufrufer** (Agenten, Tools mit Agent-Kontext) setzen den Namen explizit.

### 3.2 Emitter setzen `origin`

| Emitter | `origin` | Beispiel |
|---|---|---|
| `AbstractAgent` (AI-Text) | `getName()` | `"Da Mek"` |
| `AbstractAgent.onTool` (Tool-Zeile) | `getName()` | `"Da Mek"` |
| `CompactFeedback.startLine` / `endLine` | Agenten-Name (bereits vorhanden) | `"Da Mek"` |
| `PoDelegateTool` (Delegations-Zeilen) | Ziel-/Quell-Agent | `"Da Mek"` |
| `SearchAgentTool` (Such-Zeilen) | Such-Agent | `"Da Sniffa"` |
| System-Meldung (z. B. Context-Limit-Warning) | `null` | — |
| Fehler ohne Agent-Kontext | `null` | — |

**Regel:** Der Emitter kennt seinen Agenten-Kontext und setzt `origin` beim Erzeugen der Nachricht. Keine zentrale „wer bin ich“-Auflösung im UI.

---

## 4. Plugin-Änderung: `chat.html` / JS

### 4.1 CSS: dynamischer Name statt hartkodiert

```css
/* Vorher */
.message.AI::before {
    content: "Peon";
    color: #1a7f37;
}

/* Nachher */
.message.AI::before {
    content: attr(data-agent);
    color: #1a7f37;
}

/* Kein Name → keine Überschrift */
.message.AI:not([data-agent])::before,
.message.AI[data-agent=""]::before {
    content: none;
}
```

- **`attr(data-agent)`** liest den Namen aus dem Data-Attribut.
- **`:not([data-agent])` / `[data-agent=""]`** blendet die Überschrift aus, wenn kein Name gesetzt ist.
- **Kein Agenten-Wissen im CSS** — nur die Render-Regel.

### 4.2 JS: `origin` → `data-agent`

Beim Rendern einer Nachricht (AI-Text, Tool-Zeile, Problem):

```javascript
function renderMessage(msg) {
    const div = document.createElement('div');
    div.className = 'message ' + msg.role;  // z. B. 'message AI'
    if (msg.origin) {
        div.setAttribute('data-agent', msg.origin);
    }
    div.textContent = msg.message;
    // … bestehende Render-Logik
}
```

- **`msg.origin`** kommt aus dem Core (via `SimpleMessage.origin`).
- **`data-agent`** wird nur gesetzt, wenn `origin` nicht null/leer ist.
- **CSS** kümmert sich um die Anzeige/Ausblendung.

### 4.3 Tool-Zeilen: neue Überschrift

Bisher hatten Tool-Zeilen keine Überschrift. Neu:

```html
<div class="message TOOL" data-agent="Da Mek">
    🗜 Da Mek compacted: 6 → 2 messages, …
</div>
```

```css
.message.TOOL::before {
    content: attr(data-agent);
    color: #666;  /* oder eigenes Farbschema für Tools */
}
```

- **Gleiche Mechanik** wie AI-Text — nur anderes CSS für Tool-Zeilen.
- **Kein Name** (System-Tool-Zeile) → keine Überschrift (gleiche Regel).

---

## 5. Vergleich zu Alternativen

| Ansatz | Vorzug | Nachteil |
|---|---|---|
| **`origin` im Message-Modell** (dieser Vorschlag) | saubere Trennung Core/UI, kein fake Name, minimal invasiv | Emitter müssen Feld setzen |
| Hartkodiertes `"Peon"` (Status quo) | null Aufwand | kein Mehrwert, falsch bei Multi-Agent |
| Fallback `"System"` | einfach | fake Agenten-Name, verwirrend |
| Agenten-Name im Message-Text selbst | kein Modell-Change | UI kann nicht sauber stylen/trennen |

**Entscheidung:** `origin` als eigenes Feld — sauberste Trennung, kein fake Name, minimal invasiv.

---

## 6. Abgrenzung zu bestehenden Patterns

### Claude Code

- **Message-Metadaten:** Claude Code rendert Agenten-/Tool-Namen aus strukturierten Message-Metadaten, nicht aus hartkodiertem CSS.
- **Kein fake Fallback:** System-Nachrichten ohne Agenten-Bezug bleiben ohne Namen.

### OpenCode

- **Tool-Hooks mit Kontext:** `tool.execute.before/after` tragen den auslösenden Kontext (Agent, Tool) — analog zu unserem `origin` [web:10].
- **Permissions mit Agenten-Bezug:** Regeln sind an Agenten gebunden, nicht global [web:3][web:7].

**Parallele:** Dein `origin`-Feld ist das direkte Pendant — der Core liefert die Herkunft, das UI entscheidet über die Darstellung.

---

## 7. Paketstruktur / Blast Radius

**Kein neues Package nötig** — die Änderung ist minimal und verteilt sich auf bestehende Dateien:

### Geändert (core)

| Datei | Änderung |
|---|---|
| `tool/model/SimpleMessage.java` | Neues Feld `origin` (+ 3-arg Konstruktor, 2-arg bleibt) |
| `agent/AbstractAgent.java` | Setzt `origin = getName()` bei AI-Text und `onTool` |
| `compact/CompactFeedback.java` | Setzt `origin` bei Start-/End-Zeilen (bereits Agenten-Name vorhanden) |
| `poagent/tools/PoDelegateTool.java` | Setzt `origin` bei Delegations-Zeilen |
| `tool/tools/SearchAgentTool.java` | Setzt `origin` bei Such-Zeilen |
| Weitere Emitter (je nach Fund im Code) | Setzen `origin` wo Agenten-Kontext vorhanden |

### Geändert (Plugin)

| Datei | Änderung |
|---|---|
| `resources/chat.html` (oder JS-File) | CSS: `attr(data-agent)` statt `"Peon"`, Ausblend-Regel |
| JS-Render-Logik | `msg.origin` → `data-agent` Attribut |
| CSS für `.message.TOOL` | Neue Überschrift-Regel (gleiche Mechanik) |

### Neu (Tests)

| Datei | Zweck |
|---|---|
| `SimpleMessageOriginTest.java` (core) | `origin` wird korrekt gesetzt/gelesen, 2-arg Konstruktor = null |
| Emitter-Tests (bestehende erweitern) | Je Emitter: `origin` = erwarteter Agenten-Name |

### Unverändert

- `AiMessage` / langchain4j-Modell — bleibt (das ist die LLM-Schicht, nicht die UI-Schicht).
- `ThreadSafeMemory` / Compact-Logik — bleibt (origin ist Anzeige-Metadatum, nicht Memory-Inhalt).
- `AiMonitor`-Interface — bleibt (die Signaturen ändern sich nicht, nur die `SimpleMessage`-Instanzen tragen zusätzlich `origin`).

**Gesamt: ~5–8 geänderte core-Dateien, 2–3 geänderte Plugin-Dateien, 1–2 neue Test-Dateien.** Kein neues Package, keine neuen Interfaces.

---

## 8. Offene Punkte

*(Keine — Design ist nach deiner Revision eindeutig: `origin` = Agenten-Name oder null, UI blendet bei null aus, kein fake Fallback.)*

---

## 9. Nächste Schritte

1. **Freigabe dieses Proposals** (keine offenen Fragen).
2. **Implementierung:** `SimpleMessage.origin` + Emitter-Anpassung + CSS/JS im Plugin.
3. **Doc-Update:** `chat-message-concept.md` / `agenten-status-im-header.md` beschreibt die Herkunfts-Regel.
