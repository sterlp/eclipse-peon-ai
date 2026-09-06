# Eclipse Java Move Type Tool

## Goal

Ein `eclipseJavaMoveType(sourcePath, targetName|targetPackage)` — Move/Rename eines Java-Types
**inkl. aller Referenzen** über die Eclipse-JDT-Refactoring-Engine (LTK `RenameSupport` /
`RenameJavaElementRefactoring`). Das ist der IDE-ATM: der Typ und jede Referenz auf ihn (Imports,
FQN, Typ-Referenzen, JavaDoc) bewegen sich konsistent — kein LLM kann das per Copy+Delete oder
Text-Replace nachbauen, ohne still Referenzen zu verlieren.

**WEIL (User, 2026-09-06):**Rename-Story abgegrenzt (R4: Rename = atomarer Move, Copy bleibt
einfach) — aber für **Java-Types mit Referenzen** fehlt das Gegenstück in der Eclipse-Tool-Familie.
Ergänzt Write/Edit/Rename um die JDT-Refactoring-Ebene. **Aktuell NICHT gebaut — Backlog.**

## Status

🚧 in design — aufgenommen 2026-09-06, Story erst bei Zyklus-Bedarf ausformulieren.

## Offene Design-Fragen (beim Ausformulieren klären)

- **Signatur:** (`sourcePath` + neuer Name) vs. (`sourcePath` + Package-Pfad)? Move über
  Package-Grenze = zwei Refactoring-Operationen?
- **Scope:** nur Klassen/Interfaces/Enums/Records — oder auch Methoden/Felder (dann eher
  `eclipseJavaRenameElement`)?
- **Refactoring-Runner:** `RenameSupport` (UI-bound?) vs. headless `Refactoring` + `CheckConditionsOperation` — im Tool-Kontext ohne Shell?
- **Preconditions:** Compile-Errors vor Rename blockieren (JDT-Standard) — verhalten bei
  kaputtem Workspace?
- **Undo:** Refactoring ist in der Eclipse-Undo-Chain — LLM-Undo-Erwartung klären
