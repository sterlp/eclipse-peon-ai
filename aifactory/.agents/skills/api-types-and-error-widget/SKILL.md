# Server-Fehler sichtbar machen: generierte API-Typen + Standard-Error-Widget

Wann benutzen: jede Fabrik-Anfrage, die scheitern kann (400/404/409/500), und jede Form, deren Felder
der Server per Bean Validation ablehnt. Ziel: der Nutzer sieht **am Feld**, was falsch ist — nie einen
stillschweigend verworfenen Fehler.

## 1. API-Typen generieren (Server == Client)

Maven-Plugin `cz.habarta.typescript-generator:typescript-generator-maven-plugin` (Referenz:
`spring-persistent-tasks-root/core/pom.xml:123-170`): `jsonLibrary: jackson2`, `outputKind: module`,
`outputFileType: implementationFile`, Phase `process-classes`, Ausgabe nach
`factory-ui/src/shared/server-api.ts`. `classes`/`classPatterns` auf die Request-/Response-DTOs der
Factory, `optionalAnnotations` für `Nullable`-Arten, `noFileComment`. Die handgeschriebenen
`*.client.ts` importieren ihre Typen aus der Generierung — **Feldnamen existieren genau einmal** (in
Java), so passt die Validierungs-Antwort des Servers (`field`) wörtlich auf das UI-Feld.

## 2. Fehlervertrag: `ApiError` trägt die Felder

`ApiExceptionHandler` (Factory) behandelt zusätzlich `MethodArgumentNotValidException` /
`ConstraintViolationException`: neben `message` eine Liste `fieldErrors: {field, message}[]` —
`field` ist der **Java-Property-Pfad** des DTO (z. B. `extraBody`, `queryParams`). DTOs validieren
per Custom-Constraints am Endpoint (`@Valid`), nicht per Hand-If im Service. **Gemessene Sätze für
Drei-State-String-DTOs:** Standard-Annotationen (`@Positive`, `@Min`) passen nicht — eigene
Constraints, deren Default-`message` die bestehende Hint-Konstante ist; `null`/blank = erfüllt (Feld
nicht gesendet bzw. gelöscht). Und: ein Constraint auf einer Record-Komponente mit
`@Target({FIELD, PARAMETER, METHOD})` meldet dieselbe Regel **dreimal** (Feld, Accessor,
Konstruktor) ⇒ nur `@Target(FIELD)`.

## 3. Standard-Error-Widget (AntD zuerst, R-WP-46)

Ein wiederverwendbares Bauteil (`shared/api.error.tsx`) mit **zwei** Exporten:

- **`ApiField({ field, errors, children })`** — ein **`Form.Item` ohne `name`** (reine Anzeige, kein
  Formular-Feldmodell), das die fertige Feld-Markierung der Seite **umschließt**.
  `validateStatus="error"` und `help=<message>` nur bei Treffer; ohne Treffer rendert es
  unangetastet.
- **`ApiFormErrors({ error, fields })`** — AntD `Alert` mit dem Server-Wortlaut, aber **nur** für
  Fehler ohne Feldbezug, den die Seite selbst zeichnet (`fields` = die Namen, die die Seite mit
  `ApiField` einfasst). Erst ab diesem Stand: nennt eine Antwort **mehrere** Felder, erscheint
  **ein** Alert **pro nicht umfasstem** Eintrag — nichts verschwindet still.

Seiten mit Server-Formularbindung nutzen das Bauteil überall — kein zweiter Anzeige-Pfad für
Server-Fehler.

**AntD-6-Fakten, die den Bau bestimmen (gemessen 2026-11-09, antd 6.7.0):**

- `FormProps` kennt **kein** `component={false}` mehr; das DOM-`<form>` kommt aus
  `@rc-component/form` und wird **immer** gebaut. Ein `Form` **in** einem bestehenden `<form>` wäre
  verschachteltes Markup ⇒ die native Hülle wird **ersetzt**, nie ergänzt.
- Ein `Form.Item` **ohne** `Form` als Vorfahr wirft nicht, loggt aber je Mount
  `Can not find FormContext …` ⇒ jede Server-Formularseite braucht eine `Form`.
- `className` sitzt an der Wurzel des `Form.Item` (`.ant-form-item`), **fremde Props** gehen an den
  `…-row` — darauf ist kein Test zu stützen; Marker sind die eigenen Klassen.
- Der Status erreicht das `Input` **nur als Klasse** (kein `aria-invalid`) ⇒ „rot" ist in jsdom nur
  über `.ant-form-item-has-error` messbar.
- `htmlType="submit"` + `onFinish`: die Submit-Kette des `Form` antwortet **einen Microtask später**
  als ein nativer `onSubmit` — Tests, die den Body synchron aus dem Call-Log lesen, müssen ihn
  erwarten (`await waitFor(...)`).
- Eine Sektion, die ihr Formular **nicht** zum Speichern benutzt (Agenten-Slots: Speichern je Klick),
  bekommt eine `Form` **ohne** `onFinish` und den Button **außerhalb** — sonst ist Speichern plötzlich
  ein Enter im Feld.
- CSS: `.ant-form` kommt aus dem Basis-Sheet zur Laufzeit mit derselben Spezifizität wie eine eigene
  Klasse ⇒ Selektoren der Hülle doppelt schreiben (`.settings-form.ant-form`), und der
  `Form.Item`-Wrapper ist das neue Flex-/Grid-Kind: `min-width: 0`, `margin-bottom: 0`
  (`.api-field.ant-form-item`), das Steuer darin `width: 100%`.

## Fallstricke

- SSE-Kanten verhandeln kein JSON: ein `Accept: text/event-stream`-Header macht aus jeder
  ablehnenden Kante einen 500er (`HttpMediaTypeNotAcceptableException` schlägt den echten Status) —
  der Fehler-Handler muss die Negotiation auflösen (C-8).
- Generierte Datei ist Build-Produkt: nie von Hand ändern, in `.gitignore` nur, wenn der UI-Bau sie
  selbst erzeugt — sonst committen (Eclipse führt `pnpm build` nie aus, Jar-Auslieferung siehe
  AGENTS-DEV).
- Feldname im Draht ist der Property-Pfad **ohne** Verschachtelungs-Präfix, wenn das UI flache
  Draft-Objekte fährt — Server und UI müssen dieselbe Namens-Konvention dokumentiert halten
  (klassenweite Constraints melden ihr Feld über `addPropertyNode(...)`).
- Eine Violation-Nachricht darf den eingetippten Wert nie wiederholen (Log-/Hint-Verbot);
  `fieldErrors` wird als `null` serialisiert, wenn kein Feld gemeint ist ⇒ der Client liest `null`
  wie `[]`.
