# Delta N1 — API-Fehler: Wortlaut lückenlos, CSS-Stütze, toter Dedupe weg

**Status: gebaut 2026-11-09, N1.1 → N1.3 grün, Mutationen M6/M7 gefahren — Ergebnis §11.**

Ein Inkrement, drei Befunde aus dem Review 2026-11-09 (Verdict **CONCERNS**, kein BUG), Scope `e6ca3573`..`b78e59d1`.
Befundtexte stehen in `peon-plan/review-findings.md` §„API-Fehler (Review 2026-11-09 …)" — hier wird **nicht**
nacherzählt, nur gebaut. Vorgeschichte Inc 1–5 (E1–E10, D1–D23, M1–M5): `git log -- peon-plan/overview.md`.

## 0. Spielregeln (unverändert bindend)

- **Erster Schritt im Dev-Lauf:** `git log --oneline -5` + `git status --porcelain` — dieser Plan schreibt ab dem
  Stand nach `b78e59d1`.
- Commitbar: `aifactory/factory-ui/**`, `aifactory/factory-backend/**`, `aifactory/peon-plan/**`.
  **Niemals** `docs/**` (Jons Seite), nie `aifactory/pom.xml`, nie `main`, kein amend/rebase, keine AI-Namen.
  Diese Delta ändert **kein** `AGENTS-DEV.md` und **keine** Doku/Skill-Datei.
- Gates UI: `npx tsc -b` 0, `npx oxlint` 0/0, `npx antd lint ./src` 0, einzelne Fälle `pnpm test -t '<name>'` —
  **niemals** `pnpm test -- <datei>`. Maven immer mit `clean`.
- Rot-vor-grün und Mutation: Rückweg über `cp`-Backup + `diff` (leer), **nie** `git checkout -- <datei>`.
- Keine neuen UI-Wörter (kein `content.ts`-Eintrag), keine neuen Regel-/UC-IDs (UC-WP-93/92 sind vergeben),
  keine neue `.ant-*`-Assertion außer der bestehenden in `test/shared/api.error.test.tsx`.
- Farb-/Layout-Behauptungen sind verboten; Look nimmt Jon im Browser ab (R1).

## 1. Befunde → Entscheidungen

| # | Befund (Kurzform, Text in review-findings.md) | Entscheidung |
| --- | --- | --- |
| **S1** | `ApiFormErrors` zeigt nur `ApiError.message` (= erste Violation); nennt die Antwort zwei Felder und ist eines nicht umfasst, fehlt dessen Grund (R-WP-56, R8) | **N1.1** — die Alert-Zeile zeigt die Begriffe der **nicht** umfassten Einträge, einer pro `Alert`; `errorMessage` bleibt nur für Antworten ganz ohne Feldbezug |
| **L1** | `.api-field { margin-bottom: 0 }` (`styles.css:130-133`) verliert gegen das zur Laufzeit injizierte `.ant-form-item` ⇒ doppelte Feldabstände in allen drei Formularen | **N1.2** — Klassenverdopplung wie D8: `.api-field.ant-form-item` (+ eine Inline-Scope-Zeile) |
| **L2** | Dedupe `ApiExceptionHandler:64-71` (`LinkedHashMap` + `putIfAbsent`) ist unerreichbar; Mutation `putIfAbsent`→`put` überlebt | **N1.3** — `getAllErrors()` 1:1 abbilden (steht so in §4.2 des Inc-Plans), JavaDoc nachziehen |

**N1.1 — warum pro Eintrag ein `Alert` (statt einer Zeile mit Mischtext).** Ein Satz, eine Ursache; kein erfundenes
Trennzeichen, kein `description`-Prop, kein neuer Wortlaut. Der Top-Level-`message` geht dabei nicht verloren: er
**ist** die erste Violation, und die steht entweder an ihrem Feld oder ist selbst ein nicht umfasster Eintrag.
Verworfen: alle Begriffe in einen `Alert` zusammenziehen (erfände eine Zeilenordnung), `errorMessage` erweitern
(der Server-Draht ist nicht unser Formulierer), Seitentest zusätzlich zum Widget-Test (Duplikat = LOW, AGENTS.md).

**N1.3 — warum trotzdem ohne Dedupe sicher bleibt:** „ein Eintrag je Feld" garantieren die Draht-Formen selbst
(`@Target(FIELD)` je Regel — D12; `gitUrl`: `@NotBlank` und `@HttpsGitUrl` schließen sich aus). Beleg ist der
bestehende `SettingsFieldErrorsTest.everyWrongFieldIsNamedOnceInOneAnswer`. Der Fallback für Klassenziele
(`getObjectName()`) bleibt, weil `addPropertyNode`-Regeln (E5) sonst ohne Feld dastünden.

## 2. N1.1 — `ApiFormErrors` (Widget)

`factory-ui/src/shared/api.error.tsx:46-61` (Signatur bleibt, `ApiField` :15-36 bleibt unverändert):

```tsx
if (error === null || error === undefined) return null
const named = fieldErrorsOf(error)
if (named.length === 0) return <Alert type="error" title={errorMessage(error)} />   // 409, git-Output, Transport
const unwrapped = named.filter((entry) => !fields.includes(entry.field))
if (unwrapped.length === 0) return null                                             // kein Feld wird wiederholt
return <>{unwrapped.map((entry) => <Alert key={entry.field} type="error" title={entry.message} />)}</>
```

- JavaDoc `:38-45` umschreiben: ein Name, den die Seite nicht fasst, behält **seinen eigenen** Satz — die erste
  Violation ist nicht die ganze Antwort (R-WP-56, R8). Kein Verweis auf `errorMessage` als „den" Wortlaut.
- Datei exportiert weiter nur Komponenten (oxlint `react(only-export-components)`); `fieldErrorsOf` bleibt in
  `shared/api.client.ts:62-67`.
- Aufrufer bleiben, wie sie sind (nichts an den Seiten): `config.page.tsx:227`, `config.agents.tsx:220`
  (je Slot, `key={slot}` trägt schon die Karte), `project.page.tsx:130`.

**Neuer Test** in `factory-ui/test/shared/api.error.test.tsx` (bestehende Helfer `refusal` `:25-41`,
`Harness` `:47-67`, Konstante `MAX_TOKENS_REASON` `:10` mitbenutzen — nichts umbauen):

```tsx
// UC-WP-93
test('anUnwrappedFieldOfAMultiFieldAnswerStillReachesTheScreen', async () => {
  const THINK_REASON = 'think is not offered by this provider'
  const error = await refusal(400, {
    status: 400, error: 'Bad Request', message: MAX_TOKENS_REASON,
    fieldErrors: [
      { field: 'maxTokens', message: MAX_TOKENS_REASON },   // Harness fasst maxTokens
      { field: 'think', message: THINK_REASON },            // Harness fasst think nicht
    ],
  })
  render(<Harness error={error}
    errors={[{ field: 'maxTokens', message: MAX_TOKENS_REASON }, { field: 'think', message: THINK_REASON }]} />)

  expect(screen.getAllByRole('alert')).toHaveLength(1)                  // der umfasste Grund wird nicht wiederholt
  expect(screen.getByRole('alert')).toHaveTextContent(THINK_REASON)     // der ungefasste Grund erreicht den Schirm
  expect(screen.getByRole('alert').textContent).not.toContain(MAX_TOKENS_REASON)
  expect(screen.getByTestId('field-error-maxTokens')).toHaveTextContent(MAX_TOKENS_REASON)
  expect(document.querySelectorAll('.api-field-error')).toHaveLength(1) // think erfindet keine Feld-Markierung
})
```

Der Fall bleibt im Widget: die Seite kann zwei Begriffe nicht besser zeigen als das Widget, und A23b
(`config.page.test.tsx.anUnwrappedFieldErrorStillReachesTheScreen`) deckt die Seite schon.

## 3. N1.2 — CSS-Stütze am Widget-Feld

`factory-ui/src/styles.css:127-133` — die eine `.api-field`-Regel wird auf die Klassenverdopplung gestellt
(Kommentar-Stil folgt `:114-115`):

```css
/* the basis injects `.ant-form-item` (antd/es/form/style/index.js:107-109, margin-bottom = token.marginLG)
   after our sheet at the same specificity, so the widget's item names both classes (D8). */
.api-field.ant-form-item {
  min-width: 0;
  margin-bottom: 0;
}

/* the inline add-project row: the basis zeroes the gap through `.ant-form-inline .ant-form-item-inline`
   (index.js:348-351, token inlineItemMarginBottom = 0) — named here so a token change cannot bring it back. */
.add-project.ant-form-inline .api-field.ant-form-item {
  margin-bottom: 0;
}
```

Unangetastet: `.settings dd .api-field { flex: 1 1 12rem }` (`:135-137`), die Breite-Regel
`.api-field input/textarea/.ant-select` (`:139-144`), `.add-project.ant-form` (`:116-125`), `.settings-form.ant-form`.

**Kein Test** (jsdom misst kein Layout; ein `declares()`-Pin auf den Selektor wäre implanationsgekoppelt —
AGENTS.md: trivial = LOW). Jon nimmt die drei Formulare im Browser ab: einheitlicher Feldabstand in
Config-Zeilen, Agenten-Karte und Add-Zeile; kein Zusatz-Abstand vor dem Knopf.

## 4. N1.3 — Dedupe raus

`factory-backend/src/main/java/org/sterl/open/aifactory/shared/ApiExceptionHandler.java:56-72`:

```java
List<ApiFieldError> fieldErrors = e.getBindingResult().getAllErrors().stream()
        .map(violated -> new ApiFieldError(nameOf(violated), violated.getDefaultMessage()))
        .toList();
return ApiErrorResponses.invalidFields(fieldErrors, request);

private static String nameOf(ObjectError violated) {
    return violated instanceof FieldError rejected ? rejected.getField() : violated.getObjectName();
}
```

- Importe `java.util.LinkedHashMap` und `java.util.Map` sind danach fort — löschen (`List`, `ObjectError`,
  `FieldError` bleiben). `ApiErrorResponses` und `ProjectExceptionHandler` ändern nichts.
- JavaDoc `:56-61`: „first reason of a field wins" **raus** (die Regel trifft nicht mehr zu). Neu in zwei Sätzen:
  ein Eintrag je Violation in der Reihenfolge des Validators; „ein Eintrag je Feld" sichern die Formen selbst
  (`@Target(FIELD)`, D12) — Beleg `everyWrongFieldIsNamedOnceInOneAnswer`. Keine Doku nachschreiben.
- **Kein** neuer Backend-Test, **kein** Test gelöst oder gelockert.

## 5. Abnahmekriterien

| # | GIVEN | WHEN | THEN | Nachweis |
| --- | --- | --- | --- | --- |
| N1-A1 | Antwort mit `fieldErrors[maxTokens, think]`, `fields` = `['maxTokens','tokenWindow']` | Widget rendern | genau **ein** `alert`, es nennt den `think`-Satz und **nicht** den `maxTokens`-Satz; `field-error-maxTokens` am Feld; genau ein `.api-field-error` | `api.error.test.tsx.anUnwrappedFieldOfAMultiFieldAnswerStillReachesTheScreen` (neu, UC-WP-93) |
| N1-A2 | Antwort **ohne** `fieldErrors` (409/git-Output/Transport) | Widget rendern | `Alert` mit `errorMessage`-Text, kein Feld markiert | bestehend `api.error.test.tsx.anErrorWithoutFieldShowsTheServerTextAsAnAlert` |
| N1-A3 | alle gemeldeten Felder umfasst | Widget rendern | kein `alert` (nichts wird wiederholt) | bestehend `anAlertNeverRepeatsAFieldError` |
| N1-A4 | ein gemeldetes Feld, nicht umfasst | Widget rendern | `alert` mit genau diesem Satz | bestehend `anUnwrappedFieldErrorStillShowsTheServerText` |
| N1-A5 | Config/Projekt/Agenten: abgelehnter Save bzw. 409 | Seiten-Tests | dieselben Wortlaute wie vorher, kein zweiter `alert` | bestehend `config.page.test.tsx.aConflictWithoutFieldStillShowsTheAlert`, `…anUnwrappedFieldErrorStillReachesTheScreen`, `config.agents.test.tsx.aRefusedSaveSaysWhyAndKeepsWhatWasTyped` |
| N1-A6 | Body mit drei falschen Feldern | `PUT /api/settings` | weiter **drei** `fieldErrors`, je einer | bestehend `SettingsFieldErrorsTest.everyWrongFieldIsNamedOnceInOneAnswer` (grün ohne Dedupe) |
| N1-A7 | `gitUrl` abgelehnt (beide Varianten) | `POST /api/projects` | `fieldErrors[0].field == "gitUrl"`, unverändert | bestehend `ProjectControllerTest.nonHttpsUrlRejectedNothingStored`, `…credentialUrlRejectedAndNothingStored`, `…anEmptyUrlNamesItsFieldButAMissingBodyNamesNothing` |
| N1-A8 | unbekannter Slot (Pfad, kein Feld) | `PUT /api/settings/agents/<slot>` | 400 ohne `fieldErrors` | bestehend `AgentSettingsTest.anUnknownSlotIsRefusedAndNothingIsWritten` |
| N1-A9 | gefaltetes/alle drei Formulare | Browser | keine doppelten Feldabstände an `.api-field` | **Jon, R1** — kein Test (§3) |

**Mutationen (beide Pflicht, Protokoll ins Plan-Datei-Ergebnis):**

| # | Zeitpunkt | Mutation | Erwartung |
| --- | --- | --- | --- |
| M6 | **vor** dem Fix, auf HEAD, mit dem neuen Test | `api.error.tsx:57` `.every(` → `.some(` | **rot** — der Zwei-Felder-Fall hat der alten Regel nichts bewiesen; Fehlermeldung protokollieren |
| M7 | **nach** dem Fix | `!fields.includes(entry.field)` → `fields.includes(entry.field)` | **rot** — `alert` zeigt den `maxTokens`-Satz, der `think`-Satz fehlt |

Beide Male: `cp`-Backup vor der Änderung, nach dem Lauf `diff` gegen das Backup **leer**, danach Gates erneut.

## 6. Reihenfolge (rot-vor-grün, jeder Schritt für sich grün)

1. **N1.1 rot:** neuen Test schreiben, `pnpm test -t 'anUnwrappedFieldOfAMultiFieldAnswerStillReachesTheScreen'`
   → rot (Fehlerwort protokollieren). **M6** fahren, zurück, wieder rot laufen lassen.
2. **N1.1 grün:** `api.error.tsx` nach §2 + JavaDoc. Gleicher Fall grün, dann ganzes `pnpm test`
   (**268** Tests / 29 Dateien erwartet; Inc 5: 267), `npx tsc -b`, `npx oxlint`, `npx antd lint ./src`.
   N1-A2–A5 grün gelassen; **wird einer davon rot, ist sein Stub unrealistisch** (`message` ≠ Feld-Begriff) —
   melden, nicht still umschreiben.
3. **N1.3:** Handler nach §4, JavaDoc, Importe. `mvn -o clean test` → **274** Tests, 0 F/0 E, mit
   `-Dlogging.level.org.springframework.test.context.cache=DEBUG` **`missCount = 1`**, Dauer ≈ 45–47 s
   (M5 darf nicht steigen). **M7** fahren, zurück, Gates neu.
4. **N1.2:** CSS nach §3. Nur `npx vite build`-Lauf (kein Test); Abnahme Jon (N1-A9).
5. Commits: **(a)** `factory-ui` (Test + Widget + CSS), **(b)** `factory-backend` (Handler). Dieses
   `peon-plan/overview.md` mit den Messergebnissen (M6/M7, Zahlen) in **(b)** oder eigenem Commit.

## 7. Betroffene Dateien

- ändern: `factory-ui/src/shared/api.error.tsx`, `factory-ui/test/shared/api.error.test.tsx`,
  `factory-ui/src/styles.css` (`:127-133`),
  `factory-backend/…/shared/ApiExceptionHandler.java` (`:56-72` + Importe)
- **nicht** geändert: `api.client.ts`, die drei Seiten, `ApiErrorResponses`, `ApiError`/`ApiFieldError`,
  Constraints/DTOs, `server-api.ts`, `AGENTS-DEV.md`, `docs/**`, `.agents/**`, alle bestehenden Tests.

## 8. Benannte Abweichungen / Nicht gebaut

- **D24** — ein `Alert` **pro** nicht umfasstem Eintrag (Review formulierte „Nachrichten zeigen", offen): zwei
  Ursachen ergeben zwei Zeilen statt eines Mischsatzes; kein Trennzeichen, kein neues UI-Wort.
- **D25** — die Inline-Scope-Zeile ist heute wirkungslos: gemessen setzt die Basis im Inline-Formular
  `.ant-form-inline .ant-form-item-inline { margin-bottom: inlineItemMarginBottom }` mit Token **0**
  (`antd/es/form/style/index.js:348-351,470`), und der Selector trägt das Suffix `-inline` (der Review-Text nannte
  `.ant-form-item`). Gebaut wie von Jon verlangt (0,4,0, Wert 0), damit ein Token-Wechsel die Zeile nicht
  zurückholt. Der eigentliche Defekt sitzt allein in `.ant-form-item` (0,1,0, `:107-109`, Token `marginLG`).
- **D26** — Doc-Kleinigkeit aus dem Review (`docs/api-errors.md:51` Beispiel-Property `jsonBody` → real
  `extraBody`) und die Skill-Schärfung (`.agents/skills/api-types-and-error-widget/SKILL.md`) sind **Jons Seite**;
  nicht gebaut, nicht committet. Gleiches gilt für die ❌/❓-Marker in `api-errors.md`/`open-points.md`
  (⏳-/✅-Texte liegen in `peon-plan/api-error-notes.md`).
- Nebenbefund aus dem Review (Memory-Regel 11): die episodische `*EditFile`-Messung in `AGENTS-DEV.md` gehört in
  die Plan-Datei. Nicht Gegenstand dieser Delta (Datei hier nicht commitbar vorgesehen) — Jons Entscheidung.

## 9. Annahmen, Risiken, offene Fragen

**Offene Fragen: none.**

- **A8** — Head nach `b78e59d1` trägt keine weiteren Änderungen an `api.error.tsx`/`styles.css`/
  `ApiExceptionHandler.java`; der Dev prüft das mit `git log`/`git status` (§0), nicht aus diesem Plan.
- **A9** — die drei `ERROR_FIELDS`-Listen (`SETTINGS_ERROR_FIELDS`, `SLOT_ERROR_FIELDS`, `ADD_ERROR_FIELDS`) sind
  die einzigen `fields`-Geber; eine vierte Seite käme mit N1.1 ebenfalls zum Wortlaut.
- **R9 (neue Alert-Häufung)** — nennt ein Body drei nicht umfasste Felder, stehen drei rote Zeilen übereinander.
  Bewusst so (D24); sichtbar erst bei einem echten Mehrfeld-Fall, heute nicht erreichbar.
- **R10 (Basis-Klasse als Selektor-Stütze)** — `.api-field.ant-form-item` hängt an der Klassen-Konstanten der
  Basis (gemessen `index.js:107`, und der bestehende Test liest bereits `ant-form-item-has-error`). Ein
  Basis-Upgrade, das die Item-Klasse umbenennt, träfe alle drei `ant-*`-Lesestellen zugleich (auch D20-Tests) ⇒
  sichtbar, nicht still.
- **R1** bleibt: Layout misst jsdom nicht; N1-A9 ist Jons Browser-Abnahme.

## 10. Hygiene

`git diff` nur für §7; niemals eine Datei aus dem Kontext neu schreiben; Mutationen nie per `git checkout`
zurückholen; Messergebnisse (M6/M7, Testzahlen, `missCount`, Suite-Dauer) **vor** dem nächsten Plan in dieses
Dokument schreiben; Review-Befunde bleiben in `review-findings.md` (Regel 12) — dort nach diesem Bau nur den
Delta-Vermerk ergänzen, wenn Jon das will (Plan-Datei ist commitbar, `review-findings.md` liegt in `peon-plan/**`).

## 11. Ergebnis (gebaut 2026-11-09)

**Neuer Fall (N1-A1)** — `test/shared/api.error.test.tsx` `anUnwrappedFieldOfAMultiFieldAnswerStillReachesTheScreen`
(UC-WP-93). **Rot vor dem Fix**, und der Grund war genau der Befund: erwartet „think is not offered by this
provider", erhalten „maxTokens must be a whole number of tokens, or 0 for the provider default" — die Zeile zeigt
`message`, also nur die erste Violation.

**M6 (vor dem Fix, HEAD, mit dem neuen Test)** — `api.error.tsx:57` `.every(` → `.some(`: der neue Test bleibt
**rot, wortgleich wie ohne Mutation** (keine Form der alten Regel erfüllt den Zwei-Felder-Fall), und die
**bestehenden 267 Tests bleiben grün** ⇒ der Quantor war ungetestet, der Befund ist real. Rückweg `cp` + `diff`
leer, `git diff` an der Datei ebenfalls leer.

**M7 (nach dem Fix)** — `api.error.tsx:61` `!fields.includes(entry.field)` → `fields.includes(entry.field)`:
**drei** Tests rot — der neue (Alert zeigt den `maxTokens`-, nicht den `think`-Satz, wie §5 erwartet) plus
`anAlertNeverRepeatsAFieldError` und `anUnwrappedFieldErrorStillShowsTheServerText`. Die neue Zeile ist von beiden
Seiten gesichert. Rückweg `cp` + `diff` leer.

**Zahlen** — UI: `pnpm test` **268 Tests / 29 Dateien grün** (vorher 267), `npx tsc -b` exit 0, `npx oxlint` **0/0**,
`npx antd lint ./src` **0 findings** (55 Dateien), `pnpm build` grün (Chunk-Size-Warnung = Bestand). Backend:
`mvn -o clean test -pl factory-backend` → **274 Tests, 0 F / 0 E**, BUILD SUCCESS **44,4 s**, mit
`-Dlogging.level.org.springframework.test.context.cache=DEBUG` **`missCount = 1`** (hitCount 1597) — ein Kontext,
Dauer unverändert gegen M5. N1-A2–A5 und N1-A6–A8 blieben grün: kein Stub war unrealistisch, kein bestehender Test
wurde angefasst. Doc-Lint ohne neue Findings (weiter nur `UC-WP-91` ohne Suite-Test, Nachweis M1/M2).

**N1.2** — CSS ohne Test (§3). Basis-Zeilen an der installierten Datei nachgeprüft:
`antd/es/form/style/index.js:107-109` (`marginBottom: itemMarginBottom`) mit Token `marginLG` bei `:467`; Inline-Fall
`:345-352` (`marginBottom: inlineItemMarginBottom`, Token **0** bei `:470`) — der Selektor dort trägt das Suffix
`-inline` (D25). **N1-A9 bleibt Jons Browser-Abnahme**: Feldabstand in den Config-Zeilen, in der Agenten-Karte und
in der Add-Zeile.
