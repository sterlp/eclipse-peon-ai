---
idPrefix: WP
---
# API-Fehler — Server-Fehler sichtbar am Feld

## Ziel

Jede gescheiterte Fabrik-Anfrage wird **sichtbar**, und zwar **da, wo sie hingehört**: Feldfehler rot
am Formularfeld, alles andere als sichtbare Meldung mit dem Wortlaut des Servers — in **einem**
wiederverwendbaren Widget, nie je Seite neu erfunden. Server und Client teilen sich dieselben Typen,
damit ein Feldname der Antwort wörtlich auf das UI-Element passt. Technische Heimat:
`factory-backend` (`shared/ApiError`, `ApiExceptionHandler`, Request-DTOs) und `factory-ui`
(`src/shared/server-api.ts`, `src/shared/api.client.ts`, das Standard-Error-Widget).

## Regeln

### R-WP-54 — Die API-Typen des Clients werden generiert ✅ (Paul 2026-11-09: „schlau sein — API-Klassen generieren")

Die TypeScript-Typen der Factory-API werden aus den Java-DTOs **generiert** (Maven-Plugin
`typescript-generator`, Konfiguration nach dem Referenz-Muster
`spring-persistent-tasks-root/core/pom.xml:123-170`): Ausgabe `factory-ui/src/shared/server-api.ts`,
`jackson2`, `outputKind: module`. Die handgeschriebenen `*.client.ts` **importieren** ihre Typen aus
der Generierung statt sie zu wiederholen — ein Feldname existiert genau **einmal** (in Java). Die
generierte Datei ist ein **Build-Produkt**: sie wird committet (der Eclipse-Bau führt `pnpm` nie aus)
und nie von Hand geändert; nicht generierte Hilfstypen (z. B. die Three-State-Konvention) bleiben in
den Client-Dateien.

**Nachweis (bewusst kein Suite-Test — Build-Produkt ohne Laufzeitverhalten):** M1 — ein umbenanntes
Java-Feld bricht `tsc -b` statt stumm nichts zu ändern; M2 — eine von Hand bearbeitete `server-api.ts`
wird beim nächsten Maven-Lauf bytegleich ersetzt. Beide Messungen stehen in `peon-plan/overview.md`
§9 und sind vom Review nachgemessen.

### R-WP-55 — Ein Fehlervertrag mit Feldfehlern, validiert per Bean Validation ✅ (Paul 2026-11-09: „validate() wandert in Annotationen bzw. eigene Validatoren-Klassen")

Der Server lehnt fehlerhafte Request-Körper per **Spring Bean Validation** ab: die Regeln der
heutigen handgeschriebenen `validate()`-Zweige (Settings, Agenten, Projekt) wandern als
Jakarta-Annotationen an die Request-DTOs — komplexere Regeln als **eigene Validatoren-Klassen**
(`@Valid` am Endpoint). Die Fehlerantwort ist `ApiError` mit zusätzlichen **`fieldErrors`**
(`field` = Java-Property-Pfad des DTO, `message` = der Validationstext) — das Feld ist **benannt**,
nicht nur im Fließtext versteckt. Ob der `@RestControllerAdvice`-Umbau bleiben darf oder Spring
seinen **default error view** liefert, wurde **gemessen** (Paul 2026-11-09: „Spring liefert auch
einen default Fehler view, gerne mal messen"): ohne eigenen Advice antwortet die nicht gemappte
`/api/`-Kante mit Springs Body — **ohne** `message`, **ohne** `fieldErrors`; der Advice bleibt,
die Messung steht als ⏳ in [Open Points](open-points.md).
Abgelehnte **SSE-Kanten** behalten ihren Status (C-8: Negotiation lösen, kein 500-Verlust).

#### UC-WP-92 — aRejectedFieldNamesItself ✅
- GIVEN ein Request-DTO mit Bean-Validation-Regeln WHEN ein Feld sie verletzt (z. B. Max Tokens
  `abc`, Extra-Body kein JSON-Objekt) THEN antwortet die Fabrik 400 mit `fieldErrors`, das den
  Property-Pfad (z. B. `maxTokens`, `extraBody`) und den lesbaren Grund nennt — die Datei bleibt
  unverändert.
- GIVEN dieselbe Regel WHEN sie verletzt wird THEN ist der Fehler **ein** Eintrag je Feld — mehrere
  verletzte Felder nennt die Antwort alle.
- GIVEN ein Feldfehler WHEN die Antwort ankommt THEN steht der Grund **nicht nur** im `message`-Text
  (kein Doppel-Schreiben von Feld und Text in einer Zeile, die das UI wieder parsen müsste).

### R-WP-56 — Das Standard-Error-Widget zeigt Server-Fehler am Feld ✅ (Paul 2026-11-09: „es sollte im System ein standardisierter Error-Widget existieren")

Ein **einziges** wiederverwendbares Bauteil (AntD zuerst, [R-WP-46](ui.md)) nimmt `ApiRequestError`
entgegen und zeichnet Server-Fehler: ein `fieldErrors`-Eintrag landet als **AntD-Form-Feldfehler am
passenden Element** (`validateStatus="error"`, `help=<message>` — das Feld wird rot, der Fehler steht
beim Tipport); ein Fehler ohne Feldbezug (409-Konflikt, git-Output, 500) erscheint als sichtbare
Meldung (AntD `Alert`) mit dem **Wortlaut des Servers** — nie stillschweigend verworfen, nie ein
falsches „erfolgreich". Formulare binden ihre Felder über denselben Namen wie der Draht
(R-WP-54), das Mapping ist damit trivial; Seiten, die es nutzen, bauen **keinen** zweiten
Anzeige-Pfad. Ein Feldfehler gehört immer zum Formular, dessen Anfrage abgelehnt wurde — nie zu einem
anderen Formular derselben Seite (Paul 2026-11-10, B7a-Review: der Slot-Fehler färbt nicht das
Basis-Formular). Muster-Skill: `.agents/skills/api-types-and-error-widget/` (wird beim Bau weiter
geschärft).

#### UC-WP-93 — theFieldErrorShowsAtTheField ✅
- GIVEN ein Formular mit an den Draht gebundenen Feldern WHEN die Antwort `fieldErrors` trägt THEN
  zeigt genau das genannte Element den Fehler (rot + Hilfetext) — die übrigen Felder bleiben
  unangetastet, und die Eingabe des Nutzers bleibt erhalten.
- GIVEN ein Fehler ohne Feldbezug WHEN er ankommt THEN erscheint die Server-Meldung im Error-Panel
  der Seite ([R-WP-62](api-errors.md)) — kein Feld wird rot gemalt, das nicht gemeint war.
- GIVEN zwei Seiten mit Server-Formularen WHEN beide denselben Fehlerfall fahren THEN zeichnen beide
  über **dasselbe** Bauteil (kein zweiter Anzeige-Pfad).

### R-WP-62 — Ein Error-Panel je Seite, prominent oben ✅ (gebaut + reviewed 2026-11-10, `9d8c17bb` +
Delta `ea4330ef` — UI 283 Tests, Doc-Lint 28; Abnahme am Browser steht aus) (Paul 2026-11-10, Browser-Abnahme: „409
Conflict (paralleles Compact) unsichtbar; Error-Widget unscheinbar ganz unten"; Präzisierung:
„eigenes panel prominent oben, immer sichtbar, nicht teil vom scroll")

Jede Seite zeigt Server-Fehler **ohne Feldbezug** in **einem** Panel **prominent oben** — immer
sichtbar, außerhalb des Scroll-Bereichs (Wegscrollen ist unmöglich), auf **jeder** Seite dasselbe
Bauteil (AntD zuerst, [R-WP-46](ui.md)). Der Client liest die `ApiError`-Antwort aus und zeigt den
**Wortlaut des Servers** samt Spring-Details — ein 409 beim parallelen Compact ist damit genauso
sichtbar wie ein 500. Feldfehler bleiben am Feld ([R-WP-56](api-errors.md)) und stehen nicht doppelt
im Panel. Das Panel trägt ein **X**: der Nutzer schließt es, ein nächster Fehler ersetzt den Inhalt. Wie lange es
steht, was es außer dem X noch schließt und was es **nicht** schließt, ist eine eigene Regel
([R-WP-66](api-errors.md)) — hier steht, was das Panel **ist**. Das Panel zeigt die Fehler **dieser Seite**
(gescheiterte Requests), nicht die der Unterhaltung. Das Panel steht auf den **drei
Adressen** (Projects, Chat, Settings); Overlays der Kante — Datei-Drawer und Insight — behalten ihre
Meldung am Ort, wo sie steht (Paul 2026-11-10, Q1): eine abgelehnte Ebene meldet sich dort, nicht oben.

#### UC-WP-99 — pageErrorPanelTopAlwaysVisible ✅ (gebaut + reviewed 2026-11-10, `9d8c17bb` + Delta
`ea4330ef` — GIVEN 1 und 2 mit Tests `api.error.panel.test.tsx` P1–P7, `chat.page.test.tsx`,
`project.page.test.tsx`, `config.page.test.tsx`; Abnahme am Browser steht aus. Lebensdauer des Panels =
[R-WP-66](api-errors.md)/[UC-WP-103](api-errors.md))
- GIVEN eine Seite WHEN ein Server-Fehler ohne Feldbezug ankommt (z. B. 409 beim parallelen Compact)
  THEN steht er als Error-Panel **prominent oben** — immer sichtbar, nicht Teil des Scroll-Bereichs,
  auf jeder Seite dasselbe Bauteil.
- GIVEN ein Fehler mit `fieldErrors` WHEN er ankommt THEN bleiben die Feldfehler am Feld
  ([R-WP-56](api-errors.md)) und das Panel zeigt nur, was keinem Feld zugeordnet ist.

### R-WP-66 — Das Panel steht, bis der Nutzer selbst wieder etwas tut ❌ (Paul 2026-11-11, Q3:
„jeder Retry von mir bestätigt den Fehler automatisch … THEN wir senden die ganze conversation an das
LLM AND fehler werden ausgeblenden — ist ein retry"; und Q4 zum abgelehnten Stop)

Das Panel von [R-WP-62](api-errors.md) steht von seiner Ursache so lange, bis **eine** von zwei Dingen
passiert: der Nutzer klickt sein **X**, oder der Nutzer löst **selbst** eine neue Anfrage auf dieser
Seite aus — Senden, Formular absenden, und ebenso das **erneute Senden mit leerer Eingabe**
(Resend, [R-WP-30](chat.md)). Ein Retry ist die stärkste Aussage des Nutzers, dass ihn der Fehler nichts
mehr angeht: die Meldung verschwindet mit ihm, ohne dass er sie erst lesen und bestätigen muss.

Was **nicht** schließt: die Ereignisse eines bereits laufenden Turns (`done`, `error`). Ein Turn-Fehler
ist eine Zeile des Chats ([R-WP-34](chat.md)), keine neue Anfrage — die Meldung, die der Nutzer gerade
liest, darf nicht verschwinden, während etwas anderes fehlschlägt.

Und was **nicht** verschwinden darf: eine Ablehnung, die der Server auf einen Klick des Nutzers selbst
ausspricht. Die Ausnahme von 2026-11-10 („ein 409 auf Stop bleibt still") ist **zurückgenommen** — ein
abgelehnter Stop zeigt seinen Wortlaut im Panel; die Taste ist in diesem Fall ohnehin nicht bedienbar
([R-WP-65](chat.md)), einen klickbaren Knopf ohne Reaktion gibt es nicht.

#### UC-WP-103 — panelClosesOnlyOnWhatTheUserAskedFor ❌
- GIVEN das Panel steht WHEN der Nutzer das X klickt THEN ist es weg und die Seite behält ihre Eingaben.
- GIVEN das Panel steht WHEN der Nutzer **selbst** eine neue Anfrage auslöst (Senden, Formular
  absenden, **Resend mit leerer Eingabe**) THEN ist das Panel fort, während die neue Anfrage läuft.
- GIVEN das Panel steht WHEN ein **laufender Turn** endet oder fehlschlägt (`done`, `error`) THEN bleibt
  das Panel unverändert stehen — der Turn-Fehler steht als Zeile im Chat.
- GIVEN ein **Stop** wird vom Server abgelehnt (`409`) WHEN die Antwort ankommt THEN zeigt das Panel den
  Wortlaut des Servers ([R-WP-65](chat.md)).
