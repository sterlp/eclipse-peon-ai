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

### R-WP-54 — Die API-Typen des Clients werden generiert ❌ (Paul 2026-11-09: „schlau sein — API-Klassen generieren")

Die TypeScript-Typen der Factory-API werden aus den Java-DTOs **generiert** (Maven-Plugin
`typescript-generator`, Konfiguration nach dem Referenz-Muster
`spring-persistent-tasks-root/core/pom.xml:123-170`): Ausgabe `factory-ui/src/shared/server-api.ts`,
`jackson2`, `outputKind: module`. Die handgeschriebenen `*.client.ts` **importieren** ihre Typen aus
der Generierung statt sie zu wiederholen — ein Feldname existiert genau **einmal** (in Java). Die
generierte Datei ist ein **Build-Produkt**: sie wird committet (der Eclipse-Bau führt `pnpm` nie aus)
und nie von Hand geändert; nicht generierte Hilfstypen (z. B. die Three-State-Konvention) bleiben in
den Client-Dateien.

#### UC-WP-91 — clientTypesComeFromTheGeneratedServerApi ❌
- GIVEN ein Request-DTO der Factory WHEN die API gebaut wird THEN trägt `server-api.ts` dessen Typ
  mit denselben Feldnamen wie der Draht, und die `*.client.ts` benutzen ihn — ein Feldname, der im
  Java-DTO umbenannt wird, bricht den UI-Bau statt stumm nichts zu ändern.
- GIVEN die generierte Datei WHEN sie geändert wird von Hand THEN zeigt der nächste Maven-Lauf den
  echten Stand wieder — von-Hand-Änderungen sind per Definition weg (die Datei wird nie editiert).

### R-WP-55 — Ein Fehlervertrag mit Feldfehlern, validiert per Bean Validation ❌ (Paul 2026-11-09: „validate() wandert in Annotationen bzw. eigene Validatoren-Klassen")

Der Server lehnt fehlerhafte Request-Körper per **Spring Bean Validation** ab: die Regeln der
heutigen handgeschriebenen `validate()`-Zweige (Settings, Agenten, Projekt) wandern als
Jakarta-Annotationen an die Request-DTOs — komplexere Regeln als **eigene Validatoren-Klassen**
(`@Valid` am Endpoint). Die Fehlerantwort ist `ApiError` mit zusätzlichen **`fieldErrors`**
(`field` = Java-Property-Pfad des DTO, `message` = der Validationstext) — das Feld ist **benannt**,
nicht nur im Fließtext versteckt. Ob der `@RestControllerAdvice`-Umbau bleiben darf oder Spring
seinen **default error view** liefert, wird **gemessen** (Paul 2026-11-09: „Spring liefert auch
einen default Fehler view, gerne mal messen") und als ⏳ in [Open Points](open-points.md)
entschieden — der Vertrag (Status + `fieldErrors` + lesbarer `message`) ist in jedem Fall derselbe.
Abgelehnte **SSE-Kanten** behalten ihren Status (C-8: Negotiation lösen, kein 500-Verlust).

#### UC-WP-92 — aRejectedFieldNamesItself ❌
- GIVEN ein Request-DTO mit Bean-Validation-Regeln WHEN ein Feld sie verletzt (z. B. Max Tokens
  `abc`, Extra-Body kein JSON-Objekt) THEN antwortet die Fabrik 400 mit `fieldErrors`, das den
  Property-Pfad (z. B. `maxTokens`, `jsonBody`) und den lesbaren Grund nennt — die Datei bleibt
  unverändert.
- GIVEN dieselbe Regel WHEN sie verletzt wird THEN ist der Fehler **ein** Eintrag je Feld — mehrere
  verletzte Felder nennt die Antwort alle.
- GIVEN ein Feldfehler WHEN die Antwort ankommt THEN steht der Grund **nicht nur** im `message`-Text
  (kein Doppel-Schreiben von Feld und Text in einer Zeile, die das UI wieder parsen müsste).

### R-WP-56 — Das Standard-Error-Widget zeigt Server-Fehler am Feld ❌ (Paul 2026-11-09: „es sollte im System ein standardisierter Error-Widget existieren")

Ein **einziges** wiederverwendbares Bauteil (AntD zuerst, [R-WP-46](ui.md)) nimmt `ApiRequestError`
entgegen und zeichnet Server-Fehler: ein `fieldErrors`-Eintrag landet als **AntD-Form-Feldfehler am
passenden Element** (`validateStatus="error"`, `help=<message>` — das Feld wird rot, der Fehler steht
beim Tipport); ein Fehler ohne Feldbezug (409-Konflikt, git-Output, 500) erscheint als sichtbare
Meldung (AntD `Alert`) mit dem **Wortlaut des Servers** — nie stillschweigend verworfen, nie ein
falsches „erfolgreich". Formulare binden ihre Felder über denselben Namen wie der Draht
(R-WP-54), das Mapping ist damit trivial; Seiten, die es nutzen, bauen **keinen** zweiten
Anzeige-Pfad. Muster-Skill: `.agents/skills/api-types-and-error-widget/` (wird beim Bau weiter
geschärft).

#### UC-WP-93 — theFieldErrorShowsAtTheField ❌
- GIVEN ein Formular mit an den Draht gebundenen Feldern WHEN die Antwort `fieldErrors` trägt THEN
  zeigt genau das genannte Element den Fehler (rot + Hilfetext) — die übrigen Felder bleiben
  unangetastet, und die Eingabe des Nutzers bleibt erhalten.
- GIVEN ein Fehler ohne Feldbezug WHEN er ankommt THEN erscheint die Server-Meldung als Alert des
  Formulars — kein Feld wird rot gemalt, das nicht gemeint war.
- GIVEN zwei Seiten mit Server-Formularen WHEN beide denselben Fehlerfall fahren THEN zeichnen beide
  über **dasselbe** Bauteil (kein zweiter Anzeige-Pfad).
