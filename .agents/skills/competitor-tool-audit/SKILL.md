---
name: competitor-tool-audit
description: Systematically compare an external plugin/product tool surface (LLM tools, UI layer) against our own — inventory, coverage matrix, hygiene self-audit, neutral docs, PO decision run.
---

# Competitor-Tool-Audit

**WANN:** Die Tool-Oberfläche eines fremden Plugins/Produkts (LLM-Tools, UI-Schicht) soll systematisch mit der unsrigen verglichen werden.

## Prozedur

1. **Inventar BEIDER Seiten** via searchAgent erheben — je Tool: Name, Pfad, Zweck, Caps/Truncation (+ ob disclosed), UI-Wirkung, Guards; Gesamtzahl. Architektur-Kontext zuerst (wer ruft wen, client vs. server, Blackbox-Anteile benennen).
2. **Abdeckungsgarantie:** JEDES eigene Tool steht genau einmal in der Matrix — auch wenn das Fazit „kein Übernahme-Bedarf“ lautet.
3. **Je Paar:** Was ist besser + **WARUM**, UI-Aspekt, Empfehlung Ja/Nein/Teilweise + Aufwand S/M/L. **KEINE** PO-Entscheidung vorwegnehmen — der Plan liefert Empfehlungen + Begründung.
4. **Selbst-Audit nebenbei:** eigene Caps/Disclosure-Lücken tauchen als Hygiene-Items auf (AGENTS-Regel „a tool must never lie about a limit“). Befunde gegen den Quellcode verifizieren, nicht aus Doku übernehmen.
5. **Output:** ein hoch-leveliger Plan (`overview.md`) + neutrale Feature-Docs + **GENAU EINE** Ursprungs-Datei mit allen externen Bezügen (wird nach dem PO-Run gelöscht) — die Feature-Docs bleiben neutral.
6. **Ende:** PO-Run mit dem Owner (accept/reject je Item), erst danach Build-Pläne.
