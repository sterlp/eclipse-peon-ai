# ADR-0077 — AI Factory: geteilter Core statt Web-only-Schicht

**Status:** Accepted (2026-10-25, User-Entscheid)

## Context

Die AI Factory (`aifactory/`, Module `factory-backend` + `factory-ui`) soll Jon als Web-Anwendung
bringen — Spring Boot statt Eclipse-Plugin. Jons Empfehlung war: Core unangetastet lassen, die
Factory als reine Web-Schicht darüber. Der User entscheidet bewusst anders.

## Decision

1. **`llmpeon-core` wird geteilt.** Was die Web-App an Agent-Verhalten braucht, entsteht so weit
   wie möglich **im Core**, sodass das Eclipse-Plugin davon profitiert. Keine Kopie von
   Core-Kontrakten in der Factory, keine Web-only-Zweitimplementierung der Agent-Logik.
2. **Die Factory nutzt nur die Disk-Familie** (`disk*`, `web*`, `shell`, `docs*`). `eclipse*`-Tools
   und OSGi existieren in der Web-App nicht.
3. **Der Core bleibt Eclipse-frei** (Regel aus [ADR-0046](0046-core-portability-review.md):
   0 `import org.eclipse` in `src/main`) — jetzt mit einem zweiten realen Consumer, der die Regel
   dauerhaft erzwingt statt sie nur theoretisch zu behalten.
4. **Grenze:** in den Core gehört, was *beide* Consumer brauchen (Agenten, Tools, Context/Compact,
   Provider/Config). Was nur ein Consumer braucht — HTTP/SSE und Server-Session auf der einen,
   SWT/OSGi auf der anderen Seite — bleibt im jeweiligen Consumer.

## Consequences

* Core-Änderungen aus Factory-Anlass sind **erwünscht**, aber sie müssen dem Plugin nützen oder
  mindestens verhaltenstreu sein: der Plugin-Bestand ist der Regressionstest für jede Core-Änderung.
* Web-only-Logik im Core ist ein Verstoß gegen diese Entscheidung — der Test ist die Frage
  „braucht das auch das Plugin?".
* Der Factory-Verzicht auf Build/Test-Runner/Java-Navigation/Console (ADR-0046) bleibt bestehen;
  Web-Äquivalente sind eigene Stories, keine Core-Pflicht.
* Nächste echte Baustelle ist der aus ADR-0046 dokumentierte **Composition-Root-Gap**
  (`PeonAiService`, `AgentContextComponent`, `BuildPoAgentComponent` Eclipse-typisiert): die Factory
  braucht eine Eclipse-freie Komposition derselben Komponenten — dieser Port ist Core-Arbeit,
  keine Factory-Schicht.
