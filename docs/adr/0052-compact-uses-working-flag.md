# ADR-0052 — Compact-Lock: `working`-Flag statt Future-Chain oder eigenem Zustand

**Status:** Accepted (2026-09-22) · **Feature-Doc:** [compact-lock.md](../compact-lock.md)

## Context

User-getriggerter Compact (`AIChatView.doCompressContext` / `doCompressAgent`) lief als Job **ohne**
das `working`-Flag von `AbstractAgent`. Folge: Send während des Compacts startete einen parallelen
`call()` gegen dieselbe Memory, während `compact()` sie leerte (dokumentierter Race,
chat-job-lifecycle.md Backlog), der Roster zeigte den kompaktierenden Agenten als inaktiv, und
gequekte Nachrichten würden erst beim nächsten manuellen Turn verarbeitet. Paul fragte
alternativ eine `CompletableFuture`-Chain (Send wartet auf das Compact-Job-Ende) an.

## Decision

1. **`AbstractAgent.compact()` belegt das `working`-Flag CAS-bedingt** — nur wenn frei (User-Compact),
   Release nur, wenn es es selbst geholt hat (Re-Entrancy: der agent-initiierte Compact läuft
   innerhalb von `call()`, das Flag gehört dem Turn).
2. **Follow-up-Trigger im Job-finally** des Compact-Jobs: Queue non-empty → Follow-up-Turn;
   Unlock + Submit im selben UI-Thread-Runnable.
3. **Kein `CompletableFuture`** im Send/Compact-Pfad. Der Send-Pfad ist Job-basiert (SendDecision
   `Skip`/`Submit`); die Queue-Ordnung (Burst-Join, Drain-on-Abort, Compact-Überlebend,
   ADR-0017/0018) existiert und ist getestet — ein Future würde sie doppelt bauen und brächte nur
   eine zweite Ordnungsquelle.
4. **Kein eigener `compacting`-UI-State.** `working` ist der Aktivitätsindikator (Roster 🟢) für
   „jede Aktivität" — Paul 2026-09-22: „während ein Agent ein Compact macht ist er working!". Ein
   gelber Ball wäre Kosmetik, bewusst nicht gebaut.

## Consequences

- Der Memory-Race ist per CAS **strukturell unmöglich** (zweiter `call()` wird gequeued), nicht per
  Guard abgefangen.
- `resolveOutgoingMessage` bleibt unverändert — der `Skip`/Queue-Pfad greift über `isWorking()`.
- Kompakter Null-Case (`Nothing to compact`, R16-Guard) released das Flag sauber; Follow-up-Trigger
  feuert unabhängig vom Compact-Ergebnis.
- Der In-Loop-Compact bleibt unverändert verifizierbar (UC-CT-2 fängt einen Flag-Klau).