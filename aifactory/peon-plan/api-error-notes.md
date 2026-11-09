# Messergebnisse API-Fehler-Inkremente (Übernahme-Vorschläge für `docs/**`)

Doku-Inhalt entscheidet der Doku-Besitzer. Hier stehen nur die gemessenen Zahlen (Plan §9) und die
Formulierungen, die `docs/open-points.md` bzw. `docs/api-errors.md` brauchen.

## Inc 1 — C-8 und „Spring default error view" (gemessen 2026-11-09)

**C-8 — SSE-Kante verliert ihren Status (✅ behoben).**
`GET /api/projects/<unbekannt>/events` mit `Accept: text/event-stream` antwortete mit **500**, weil die
Ablehnung (404) einen JSON-Body bekam und die Aushandlung gegen `text/event-stream` fehlschlug — der
ursprüngliche Status ging verloren. Nach dem Fix antwortet dieselbe Anfrage mit **404 und leerem Body**:
unsere Fehler-Antworten prüfen den `Accept`-Header und bleiben bodylos, wenn er kein JSON zulässt; ohne
Body gibt es keine Aushandlung, die den Status ersetzen könnte. Körperlos heißt für den Client „kein
Feld, kein Wortlaut" — der SSE-Client wertet dort den Status.
Beleg: `ApiErrorContractTest.anSseEdgeKeepsItsRejectedStatus`.

**⏳ „Spring default error view" (R-WP-55) — Messergebnis.**
Eine nicht gemappte `/api/`-Kante (`GET /api/nothing-is-mapped-here`, `Accept: application/json`) liefert
**404** mit:

```json
{"timestamp":"2026-11-09T14:31:13.212Z","status":404,"error":"Not Found","path":"/api/nothing-is-mapped-here"}
```

Kein `message`, kein `fieldErrors`, kein `problem+json`. Am Feld ist diese Antwort unbrauchbar — unser
Fehler-Advice bleibt nötig und ist jetzt gemessen, nicht behauptet.
Beleg: `ApiErrorContractTest.withoutOurAdviceSpringAnswersItsOwnErrorBody`.

**Kontext-Disziplin (M5).** Ganzer `clean test`-Build: `missCount = 1` (hitCount 1462), 267 Tests grün,
43.3 s (Vorher-Messung 44 s). Kein neuer Kontext, kein Eintrag in der Ausnahmliste des Kontext-Guards.

## Inc 4 — generierte API-Typen (gemessen 2026-11-09)

**M1 — UC-WP-91 G1: ein Java-Feldumbruch bricht den UI-Bau.** `maxTokens` → `maxTokensX` in
`SettingsUpdateRequest`, `mvn -o clean process-classes -pl factory-backend` ⇒ `server-api.ts` trägt
`maxTokensX?: string | null` (`SettingsDto.maxTokens` bleibt) ⇒ `npx tsc -b` **rot**:

```
src/config/config.page.tsx(76,5): error TS2561: Object literal may only specify known properties, but
'maxTokens' does not exist in type 'SettingsUpdateRequest'. Did you mean to write 'maxTokensX'?
```

Zurückgesetzt und neu erzeugt: Datei wieder bytegleich (md5 `d5af29a7cf18b3751507103531593781`), `tsc -b`
wieder grün.

**M2 — UC-WP-91 G2: die Datei ist Build-Produkt.** `server-api.ts` um zwei Zeilen von Hand erweitert
(110 Zeilen), `mvn -o clean process-classes -pl factory-backend` ⇒ „Writing declarations to
…/factory-ui/src/shared/server-api.ts", BUILD SUCCESS, wieder 108 Zeilen und md5-identisch
(`d5af29a7cf18b3751507103531593781`). Von-Hand-Änderungen sind per Definition weg.

**Enum-Abgleich (Plan R4).** Die generierten Unions sind wort- und reihenfolgegleich mit den
handgeschriebenen: `KeySource = "FILE" | "ENV" | "NONE"`,
`ThinkForm = "TOGGLE" | "VALUES" | "FREE" | "NONE" | "UNKNOWN"` ⇒ kein Nachziehbedarf.

**Noch offen für die Doku:** `docs/api-errors.md` R-WP-54 / UC-WP-91 auf ✅ — UC-WP-91 hat bewusst **keinen**
Suite-Test (Build-Produkt, kein Laufzeitverhalten); der Nachweis sind M1 und M2.

## Inc 5 — Standard-Error-Widget auf AntD `Form` (gebaut 2026-11-09)

**Was steht jetzt wo.** `shared/api.error.tsx` exportiert die zwei Bauteile: `ApiField` (ein `Form.Item`
**ohne** `name`, reine Anzeige — `validateStatus="error"` + `help=<server text>` mit
`data-testid={'field-error-' + field}`) und `ApiFormErrors` (AntD `Alert` nur für Fehler ohne Feldbezug,
den die Seite selbst zeichnet). `fieldErrorsOf()` sitzt in `shared/api.client.ts` bei `errorMessage()`.
Drei Hüllen: `config.page.tsx` (`onFinish`), `project.page.tsx` (`onFinish`), `config.agents.tsx` je Slot
(`Form` **ohne** Submit, Speichern bleibt `onClick` außerhalb — sonst wäre Speichern ein Enter im Feld).
Ladefehler bleiben bei ihrem bisherigen `Alert` (Config-Seite und Agenten-Sektion je einer).

**⏳ für `docs/api-errors.md` R-WP-56 / UC-WP-93 — zwei Grenzen, die die Doku nicht nennt.**
1. Ein klassenweiter Constraint meldet sein Feld über `addPropertyNode(...)`; die Seite fasst nur die Felder
   ein, für die sie eine Regel kennt (`SETTINGS_ERROR_FIELDS`, `SLOT_ERROR_FIELDS`, `ADD_ERROR_FIELDS`).
   Nennt die Antwort ein Feld, das die Seite **nicht** einfasst, zeigt `ApiFormErrors` den Wortlaut als
   Alert — nichts verschwindet still, aber das Feld wird nicht rot (A23/A23b).
2. „Das Feld wird rot" ist in jsdom ausschließlich über die Basis-Klasse `.ant-form-item-has-error` messbar:
   ein `Input` bekommt den Status nur als Klasse, **kein** `aria-invalid`. Die Suite liest sie deshalb
   ein einziges Mal, in `test/shared/api.error.test.tsx`; Seiten-Tests lesen `data-testid`, die eigenen
   Klassen `api-field`/`api-field-error` und `within(...)`. Höhen und Farbe nimmt der Doku-Besitzer im
   Browser ab (jsdom misst kein Layout).

## Vorschlag: Schärfung `.agents/skills/api-types-and-error-widget/SKILL.md`

`.agents/**` steht nicht auf meiner Commit-Liste — der Text ist ein Übernahme-Vorschlag (gebaut und grün in
Inc 5, AntD 6.7.0).

**§3 Standard-Error-Widget — ersetzen durch:**

> Ein wiederverwendbares Bauteil (`shared/api.error.tsx`) mit zwei Exporten:
>
> - `ApiField({ field, errors, children })` — ein **`Form.Item` ohne `name`** (reine Anzeige, kein
>   Formular-Feldmodell), das die fertige Feld-Markierung der Seite **umschließt**. `validateStatus="error"`
>   und `help=<message>` nur bei Treffer; ohne Treffer rendert es unangetastet.
> - `ApiFormErrors({ error, fields })` — AntD `Alert` mit dem Server-Wortlaut, aber **nur** für Fehler ohne
>   Feldbezug, den die Seite selbst zeichnet (`fields` = die Namen, die die Seite mit `ApiField` einfasst).
>
> AntD-6-Fakten, die den Bau bestimmen (gemessen 2026-11-09):
>
> - `FormProps` kennt **kein** `component={false}` mehr; das DOM-`<form>` kommt aus `@rc-component/form` und
>   wird **immer** gebaut. Ein `Form` **in** einem bestehenden `<form>` wäre verschachteltes Markup ⇒ die
>   native Hülle wird **ersetzt**, nie ergänzt.
> - Ein `Form.Item` **ohne** `Form` als Vorfahr wirft nicht, loggt aber je Mount
>   `Can not find FormContext …` ⇒ jede Server-Formularseite braucht eine `Form`.
> - `className` sitzt an der Wurzel des `Form.Item` (`.ant-form-item`), **fremde Props** gehen an den
>   `…-row` — darauf ist kein Test zu stützen; Marker sind die eigenen Klassen.
> - Der Status erreicht das `Input` **nur als Klasse** (kein `aria-invalid`) ⇒ „rot" ist in jsdom nur über
>   `.ant-form-item-has-error` messbar.
> - `htmlType="submit"` + `onFinish`: die Submit-Kette des `Form` antwortet **einen Microtask später** als ein
>   nativer `onSubmit` — Tests, die den Body synchron aus dem Call-Log lesen, müssen ihn jetzt erwarten
>   (`await waitFor(...)`).
> - Eine Sektion, die ihr Formular **nicht** zum Speichern benutzt (Agenten-Slots: Speichern per Klick je
>   Karte), bekommt eine `Form` **ohne** `onFinish` und den Button **außerhalb** — sonst ist Speichern
>   plötzlich ein Enter im Feld.
> - CSS: `.ant-form` kommt aus dem Basis-Sheet zur Laufzeit und hat dieselbe Spezifizität wie eine eigene
>   Klasse ⇒ Selektoren der Hülle doppelt schreiben (`.settings-form.ant-form`), und der `Form.Item`-Wrapper
>   ist das neue Flex-/Grid-Kind: `min-width: 0`, `margin-bottom: 0`, das Steuer darin `width: 100%`.

**§2 Fehlervertrag — ergänzen:** drei State-Felder (String-DTOs) vertragen keine
`@Positive`/`@Min`; eigene Constraints, deren Default-`message` die bestehende Hint-Konstante ist.
Constraint auf einer Record-Komponente mit `@Target({FIELD, PARAMETER, METHOD})` meldet dieselbe Regel
**dreimal** (Feld, Accessor, Konstruktor) ⇒ nur `@Target(FIELD)`.

**§Fallstricke — ergänzen:** eine Violation-Nachricht darf den eingetippten Wert nicht wiederholen
(`headerParams`, `AGENTS-DEV`); `fieldErrors` wird als `null` serialisiert, wenn kein Feld gemeint ist ⇒
der Client liest `null` wie `[]`.

