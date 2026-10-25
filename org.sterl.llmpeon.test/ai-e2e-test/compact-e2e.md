# Compact-Check (2 Compacts: einmal Jon, einmal Da Mek)

Regeln: siehe [README.md](README.md) — Black Box, nur Fehler melden.

Der Compact-Agent erhält im WHAT: „Hallo von Paul" und soll eine sonst volle Summary erzeugen.

Jeder Agent übergibt seinen State (oder das Delta) im `preserve`-Parameter von `compactSession`.
Jon: compacte die Agenten nicht selbst — sag ihnen, dass sie `compactSession` selbst aufrufen.
RUFE NICHT `compactDev` auf — nur `compactSession`.

## 1. Jon (du):

- Mache genau ein „Browse Projects" (eclipseList).
- Sag im Chat „Hallo".
- Rufe dann `compactSession` auf. Der Compact-Agent soll als State erhalten: „Hallo von Paul" plus
  die Instruktion aus Schritt 2 & 3.
- Nach dem Compact:
  - Mache ein „Browse Projects" (eclipseList).
  - Führe Schritt 2 aus.

## 2. Da Mek (via askDev):

Anweisung für Da Mek:
- Mache ein „Browse Projects" (eclipseList).
- Lies eine beliebige Datei.
- Sage „Hallo Compact Agent".
- Rufe `compactSession` selbst auf und hinterlasse dir dabei als State die Anweisung: „Antworte mit
  ‚Hallo Jon'" + das Random, das Jon dir mitgibt.
- Deine finale Antwort an mich muss exakt sein: „Hallo Jon" + **wortgetreues Echo des
  `compactSession`-Tool-Results** (Stats-Zeile und ggf. `Preserved:`-Block).
  **Erwartung an das Result:** es nennt Zahlen (messages/tokens), die Zusammenfassung selbst steht
  **nicht** darin (R-CC-9, R-CC-18.4) — sie wirkt nur als State und muss im nächsten Turn noch da
  sein. Wer eine Summary im Result erwartet, prüft das falsche.

## 3. Jon (du):

- Prüfe, ob Da Mek exakt mit „Hallo Jon" geantwortet hat, und melde das Ergebnis inkl. deinem Random.

## 4. Sichtbarkeit (R-CT-8, 2.15.0) — beim Compact beobachten

Menschen-Check am UI (du siehst den Chat nicht selbst):
- Während der Compact läuft: die Tokens des Compressor-Streams kommen sichtbar an — kein schwarzer
  Balken „Waiting for AI…" ohne Inhalt.
- Danach: **genau eine** End-Zeile im Format „<Agentname> compacted: N messages, M tokens" — keine
  zweite, keine doppelte.
- Optional: während ein Compact läuft auf Stop drücken — der laufende Compressor-Call muss
  abbrechen, kein Hängen.

---

Nur Probleme melden (dann `compact-e2e-issues.md` anlegen, Form siehe [README.md](README.md)),
sonst kurze Chat-Meldung „Sichtbarkeit ok".
Erfolgskriterium: Beide Compacts laufen durch, der State überlebt jeweils die Compaction, und Da Mek
antwortet mit „Hallo Jon".
