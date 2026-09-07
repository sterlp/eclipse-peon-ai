# Plan: file-copy-e2e-fixes-2026-09-06 — Branch `release-2026-09-06`

SOLL-Quelle: `docs/file-copy-tool.md` (R5 🔒, R6 🔒) + `docs/user-question-tool-design.md` (R-UI1 🔒).

Verifizierte IST-Lage (2026-09-06, durch Da Mek):
- Befund 1: `diskCopyFile` (DiskFileWriteTool.java:181, void; "Copied" nur via onTool :197) und `eclipseCopyFile` (EclipseWorkspaceWriteFileTool.java:251 void, :282) → LLM sieht langchain4j-Literal "Success" (DefaultToolExecutor.java:160-161). Edit-Tools derselben Familien geben String zurück.
- Befund 2: `eclipseCopyFile`-Ziel-Resolution manuell gegen Workspace-Root (:270-276; getFolder-Crash bei 1 Segment, stummer Falsch-Ort bei tieferen relativen Pfaden). `eclipseRenameResource` gleicher Defekt (:232-234, getFolder :238). Andere eclipse-Tools nutzen `EclipseUtil.resolveInEclipse` (:365-390).

## Inkrement 1 — R6: LLM-sichtbare Erfolgsmeldungen ✅ (inc-1)
- Status: DONE — alle 4 Tools (diskCopy/diskRename/eclipseCopy/eclipseRename) geben `Copied|Renamed <resolved> -> <resolved>` als String zurück (onTool bleibt); Core 667/0, Plugin 187/0 (1 neuer Rename-Result-Test, Nested-Target wegen vorbestehendem R5-Bug).
- `diskCopyFile` + `eclipseCopyFile`: `void` → `String`, Return `Copied <resolvedSource> -> <resolvedTarget>` (ASCII-Pfeil). Disk: absolute Pfade (wie E5), Eclipse: /project/path. onTool-Monitor-Call bleibt (UI-Kanal), Result ist Quelle der Wahrheit.
- `eclipseRenameResource` + `diskRenameResource`: prüfen — falls void → String mit aufgelöster Quelle+Ziel (R6 gilt für Rename ebenso); falls schon String: nichts tun (verifizieren).
- Tests (GIVEN/WHEN/THEN, AssertJ in core / JUnit4 im Plugin): erfolgreicher Copy/Rename → Result enthält aufgelöste Pfade + ` -> `. Bestehende void/Success-Tests anpassen.

## Inkrement 2 — R5: Qualified Paths Only ✅ (inc-2)
- Status: DONE — core `QualifiedPathValidator` (eine Implementierung, beide Familien): disk = absolute; eclipse = `/project/path` (≥ 2 Segmente, Segment 1 = existierendes Projekt, via `EclipseUtil.isExistingProject`). Angewandt in allen 4 Tools VOR jeder Auflösung (sourcePath + targetPath) → Vertrag-Fehler `Copy/Rename paths must be fully qualified — disk: absolute, eclipse: /project/path (got: …)`, keine Datei-Operation. Ziel-Resolution vereinfacht (alter Workspace-Root-Fallback entfällt); getFolder-1-Segment-Crash gefixt via `ensureParentFolders` (Parent = Projekt selbst → kein getFolder). @Tool/@P-Descriptions beider Familien = qualifizierter Pfad-Vertrag.
- Tests: Core 678/0 (+11: 4 Relative-Rejection in DiskFileWriteToolTest, 7 QualifiedPathValidatorTest), Plugin 191/0 (+4: 2 Relative-Rejection, 2 Project-Root-Targets — die letzten red vor dem Fix, getFolder-IAE). Bestehende relative Copy/Rename-Tests → absolute Pfade (SOLL, kein weaken).
- Neuer kleiner Validator im core (eine Implementierung, keine Familie-Divergenz): qualifiziert = Disk `isAbsolute()`; Eclipse mind. 2 Segmente und Segment 0 = existierendes Projekt (`/project/path`).
- Angewandt in diskCopyFile, diskRenameResource, eclipseCopyFile, eclipseRenameResource — sourcePath UND targetPath, VOR jeder Auflösung. Relativ → Fehler `Copy/Rename paths must be fully qualified — disk: absolute, eclipse: /project/path (got: <pfad>)`, KEINE Datei-Operation.
- eclipseCopyFile-Ziel-Resolution reparieren: Ziel ist jetzt /project/path → alter Workspace-Root-Fallback (:270-276) entfällt/trivial; eclipseRenameResource analog. (Sicherstellen: kein getFolder-Crash bei Segment-Anzahl.)
- @Tool-Descriptions beider Familien (Copy + Rename): Pfad-Vertrag qualifiziert — Description-Text = LLM-Sichtbarkeit.
- Tests: relativer Pfad (source, target; je Familie, je Tool) → Fehler mit Vertrags-Text, keine Operation; qualifizierte Pfade → R1–R4 unverändert. Bestehende Tests mit relativen Pfaden auf qualifizierte umstellen (SOLL-Änderung, kein weaken).

## Inkrement 3 — R-UI1: Question-Widget-Regression
- IST: beim Question-Widget-Erscheinen wird die Frage-Nachricht erst gerendert, danach die Optionen gebaut; sind diese höher als das Input-Feld, verdecken sie den unteren Teil der Frage.
- SOLL: Frage vollständig sichtbar. Bevorzugt Reihenfolge reparieren (Nachricht rendern → Widget bauen → scroll to bottom); falls nicht sauber machbar: finaler Scroll-to-bottom NACH Widget-Completion (Chat-Widget/chat.html) — nicht nur vor dem Widget-Build.
- Da Mek analysiert Render-Pfad (AIChatView / QuestionOrchestrator / chat.html) und wählt minimalen Weg. BDD-Test wo testbar (Plugin-Test, UI-Logik); sonst explizite manuelle Verifikationsschritte hier nachtragen.
- Status: DONE — Render-Pfad analysiert: `AIChatView.showQuestion` rendert die QUESTION-Nachricht (chat.html-JS scrollt ans Ende im ALTEN, größeren Viewport), baut dann das SWT-Widget, dann verkleinert `inputBlock.layout` + Parent-Grid-Layout den Browser-Viewport — Scroll-Offset bleibt, letzte Zeilen der Frage sinken aus dem sichtbaren Bereich. Fix (Plan-Fallback, da Reihenfolge-Scroll allein asynchron-nicht-deterministisch): finales Scroll-to-bottom NACH Widget-Completion über chat.html. Neu: `ScrollToBottomCommand` (sealed `UiCommand`), `chat.html` `scrollToBottom()`-Fn + `scrollToBottom`-Message-Case (alle 4 inline `window.scrollTo`-Aufrufe dedupliziert), `ChatMarkdownWidget.scrollToBottom()`, `AIChatView.showQuestion` ruft es nach den Layout-Calls auf. KEIN automatisierter Test möglich (SWT-Browser/JS, kein Test-Harness; test-chat.html ist manuell). Plugin 191/0.
- Manuelle Verifikation (PO): 1) Frage mit ≥ 4 Optionen bzw. langem Text so erzeugen, dass das Widget höher ist als das Input-Feld (z. B. Shell-Command-Confirm oder ask-user-Tool) → **THEN** die letzte Zeile der Frage ist über dem Widget vollständig lesbar, kein verdeckter Teil. 2) Nach Answer/Cancel → normales Input-Feld, Chat-Scroll unverändert korrekt.

## Regeln & Constraints
- Log OR throw, nie beides; eine Implementierung für Familien-Logik im core.
- Tool darf nie über Limits lügen; Fehler nennt den Vertrag.
- Tests: GIVEN/WHEN/THEN; Surefire = Ground Truth; vor Plugin-Testlauf eclipseBuildProject (stale bin/ → CNFE).
- Core 667/667, Plugin 186/0 als Ausgang — Suite muss voll grün bleiben.
- Docs: `docs/file-copy-tool.md`, `docs/user-question-tool-design.md`, `docs/index.md` — PO-eigen; Da Mek committet sie mit Inkrement 1/3 nur, falls im Branch-Checkout bereits geändert (nicht zurücksetzen).
- Kein planImplemented durch Plan-Agent (Regel 10).

## Offene Fragen
- Keine.
