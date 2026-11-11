# Proposal — Update skill `test-honesty` (global, not edited by Jon)

**Aktion:** Update · **Skill:** `test-honesty` (Quelle: config, global — nach `AGENTS-JON.md` nicht
selbst geändert) · **Von:** Jon (AI Factory) · **Datum:** 2026-11-11
**Evidence:** Review #16, Zyklus „Auswahlfeld: Pfeil ja, Clearer nein", Commits `4c1b75fe` + `39ed1e24`
(`aifactory`, Branch `web-factory-5`)

## Was ergänzt werden soll

Zwei wiederkehrende Test-Formen, beide **gemessen**, beide bisher nicht im Skill:

1. **„Abwesenheits-Aussage auf einem Zweig, der einen anderen Elementtyp rendert, ist von selbst grün."**
   `expect(arrowOf(…)).toBeNull()` auf einem Feld, das im getesteten Fall ein `<Input>` statt eines
   `AutoComplete` rendert, kann nicht rot werden: es gibt kein Element, das ein Pfeil sein könnte. Ihr
   Beweis ist ausschließlich die **Mutation** (Zweig temporär als `AutoComplete` **mit** Suffix bauen ⇒
   rot; ohne Suffix ⇒ bleibt grün, und das ist korrekt). Regel im Skill: eine Negativ-Aussage auf einem
   anderen Elementtyp braucht den Mutationsbeleg oder ist ein Scheinnachweis.
   *Evidence:* `factory-ui/test/config/config.agents.test.tsx:334` (FREE-Fixture) — erst der temporäre
   `AutoComplete` + Pfeil machte sie rot.

2. **`.not.toBeNull()` ist für `undefined` wahr.** Eine Aussage „das Element existiert" auf dem Ergebnis
   einer Kette `a?.b?.querySelector(…)` ist grün, wenn die Kette `undefined` liefert — der Test war grün,
   obwohl das Feld keinen Pfeil hatte. Regel im Skill: Existenz-Nachweise immer `toBeTruthy()` am Element,
   und wenn die Identität des Elements behauptet wird, **diese** prüfen (Klasse/Name), nicht nur „irgendein
   Knoten".
   *Evidence:* die vier Pfeil-Aussagen in `config.page.fields.test.tsx`, `config.page.model.test.tsx`,
   `config.agents.test.tsx` waren vor dem Src-Schritt rot **nur** wegen `toBeTruthy()`; die erste Fassung
   mit `.not.toBeNull()` war grün (von Dev per Temp-Log belegt). Der Review fand danach die überlebende
   Mutation „anderes Icon ⇒ grün" (`config.choice.tsx:11`) — deshalb LOW-1: auf `.anticon-down` prüfen.

**Warum generisch:** beide Formen sind Typen, keine Fabrik-Fälle — jede UI-/Unit-Suite kann sie treffen, und
beide werden grün gemeldet, ohne etwas zu belegen.
