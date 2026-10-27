# Core Ground Rules — was in `llmpeon-core` darf und was nicht

> Gilt ab 2026-10-27 (Paul). `llmpeon-core` ist eine **shared lib für zwei Produkte**: das
> Eclipse-Plugin (`org.sterl.llmpeon`) und die AI Factory (Web-App). Dieses Doc ist der einzige Ort
> für die Abgrenzung; Produkt-Docs und ADRs verlinken hierher. Review-Regeln — geprüft bei Plan-
> Abnahme und Review, nicht per Unit-Test.

## Ziel

Jeder, der am Core baut, sieht auf einen Blick, ob eine Änderung hineingehört — bevor sie gebaut
wird. Ein Produkt darf den Core nie so verbiegen, dass das andere die Kosten trägt.

## Regeln

### GR-1 — Framework-frei ✅ (ADR-0046, AF-ADR-001)
Kein `import org.eclipse.*`, kein Spring, kein Servlet/HTTP in `src/main`. Abhängigkeiten, die nur ein
Produkt mitbringt (Pfade, Stores, Presenter), werden per Konstruktor/Interface injiziert.

### GR-2 — Kein UI, kein Transport ✅ (AF-ADR-001)
SWT/JFace, OSGi, HTTP/SSE, Server-Session bleiben im jeweiligen Produkt. Der Core liefert Daten und
Events, nie deren Darstellung oder Zustellung.

### GR-3 — Erweiterungen nur, wenn klar generisch ✅ (2026-10-27, Paul)
Core-Erweiterungen sind **vorsichtig** zu machen. Eine Erweiterung — besonders an gemeinsamen
Kontrakten (`AiMonitor`, `AiAgent`, `AbstractTool`, `ToolLoopRequest`) — kommt nur hinein, wenn sie
**klar sinnvoll und generisch für alle** Consumer ist, nicht nur für den, der sie gerade braucht:

- mindestens ein Produkt nutzt sie **im selben Zyklus** wirklich;
- das andere Produkt trägt **keine Kosten**: keine Laufzeit-Last, keine Pflicht-Implementierung,
  kein Verhaltenswechsel;
- eine bis zum Zyklusende ungenutzte Erweiterung fliegt wieder raus.

„Könnte das andere Produkt irgendwann auch brauchen" ist **kein** Grund. Was nur ein Produkt
braucht, löst dieses Produkt selbst — notfalls durch Behebung der Ursache statt eines neuen Kanals.

- GIVEN ein Produkt braucht eine Information, die ein Core-Event nicht trägt WHEN der Plan einen
  neuen Monitor-Callback/Decorator vorschlägt THEN wird zuerst geprüft, ob die Ursache im Core
  generisch behebbar ist; ein produkt-spezifischer Kanal wird abgelehnt.
- GIVEN eine neue Core-API WHEN der Zyklus endet und kein Produkt ruft sie auf THEN wird sie
  gelöscht.
- GIVEN eine Core-Erweiterung WHEN sie im anderen Produkt Laufzeit kostet (z. B. Wrapper/Reflection
  um jeden Turn) ohne dort Nutzen THEN wird sie abgelehnt.

> **WEIL** (2026-10-27): Die Web-App hat für ihr Absender-Problem `OriginMonitor` (Reflection-
> Decorator um jeden Turn), `onAgentCallCompleted`, `onDelegatedAnswer` und `ToolResultAttributes`
> in den Core gebaut; das Plugin nutzte nichts davon und trug die Kosten. Die alte Prüffrage
> „braucht das auch das Plugin?" ließ das durch. Alles wieder gelöscht (AF-ADR-002).
