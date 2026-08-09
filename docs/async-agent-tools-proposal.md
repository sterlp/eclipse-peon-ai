# Async Agent Tools — Proposal

## Status
🚧 in design

## Problem

Aktuell sind alle Agent-Tools synchron/blockierend:
- `askDev`, `buildWithDev`, `talkPlan`, `planWithPlanAgent` warten auf das Tool-Result
- Während eines Tool-Calls kann die Queue nicht verarbeitet werden
- Neue Messages von Paul landen in der Queue → bei Fehler gehen Messages verloren (Bug gefixt, aber Symptom bleibt)
- Lange Tool-Calls (buildWithDev) blockieren die gesamte Interaktion

## IST

```java
@Tool(name = "buildWithDev")
public String buildWithDev(@P String prompt, @P String planPath) {
    // ... synchroner call, wartet auf Antwort ...
    ChatResponse response = slave.call(prompt, this.monitor);
    return response.aiMessage().text();
}
```

**Blockierend.** Jon wartet. UI zeigt "working". Queue pausiert.

## SOLL — Async Proposal

### Erwartung von Jon

Ich (Jon) möchte:
1. **Fire-and-Forget mit Callback** — Ich starte einen Agent, er arbeitet, ich bekomme eine Benachrichtigung wenn er fertig ist
2. **Queue läuft weiter** — Neue Messages von Paul werden parallel verarbeitet
3. **Status-Query** — Ich kann den Status abfragen ("Was macht Da Mek gerade?")

### Option A: Two-Phase Tool (Start + Callback)

```java
@Tool(name = "startBuildAgent")
public String startBuildAgent(@P String prompt, @P String planPath) {
    // Startet im Hintergrund, gibt sofort Job-ID zurück
    // "Da Mek gestartet. Job #42. Siehe onAgentDone für Ergebnis."
}

// Callback (nicht direkt von Jon aufgerufen — wird automatisch ausgelöst)
void onAgentDone(String jobId, String result) {
    // Ergebnis landet in Jons Kontext/Queue
}
```

**Vorteil:** Einfach, klar getrennt Start/Ergebnis
**Nachteil:** Callback-Mechanismus muss neu gebaut werden

### Option B: Non-Blocking + Polling

```java
@Tool(name = "buildWithDev")
public String buildWithDev(@P String prompt, @P String planPath) {
    // Startet async, gibt sofort Status zurück
    return "Job #42 started. Use getStatus(#42) to check progress.";
}

@Tool(name = "getStatus")
public String getStatus(@P String jobId) {
    // "running", "done", "failed" + Ergebnis wenn fertig
}
```

**Vorteil:** Kein Callback nötig, Jon kontrolliert das Timing
**Nachteil:** Mehr Tool-Calls, mehr State Management

### Option C: Queue-basiert (Empfohlen)

```java
@Tool(name = "buildWithDev")
public String buildWithDev(@P String prompt, @P String planPath) {
    // Startet async im Hintergrund
    // Ergebnis wird als "synthetische Queue-Message" injiziert
    return "Da Mek gestartet. Ergebnis folgt als Queue-Message.";
}

// Intern: Bei Fertigstellung
memory.add(UserMessage.from("[Agent-Done] Da Mek: " + result));
// → Queue-Verarbeitung pickt es auf, Jon sieht es als nächste Message
```

**Vorteil:** Nutzt existierende Queue, kein neuer Callback nötig
**Nachteil:** Ergebnis als "User-Message" — muss klar gekennzeichnet sein

## Empfohlene Lösung: Option C

**WEIL:**
- Nutzt existierende Queue-Infrastruktur
- Keine neuen Callback-Mechanismen nötig
- Queue läuft weiter (Paul kann parallel messages senden)
- Jon sieht das Ergebnis als nächste Message im Flow — natürlich

## BDD

```
GIVEN Jon startet buildWithDev
WHEN der Agent arbeitet im Hintergrund
THEN das Tool gibt sofort zurück mit Job-Status
AND die Queue kann neue Messages von Paul verarbeiten
AND das Ergebnis landet später als Queue-Message bei Jon
```

## Offene Fragen

1. **Wie kennt Jon die Job-ID?** (für Status-Query, Cancel)
2. **Wie viele parallele Jobs?** (Da Mek + Da Thinka gleichzeitig?)
3. **Cancel-Support?** (`cancelAgent(jobId)`)
4. **Error-Handling?** (Wie wird ein Fehler an Jon gemeldet?)

## Zusammenhang

- [Queued User Messages](queued-user-messages.md) — bestehende Queue
- Queue Message Loss Bug — war das Symptom des sync-Problems (Bug ist behoben)
- [Peon-PO (Jon)](po-agent-jon.md) — Jons Tools
