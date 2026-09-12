# Compact-Check (2 Compacts: einmal Jon, einmal Da Mek)

Compact Agent erhalte im WHAT: Hallo von Paul

## 1. Jon (du):
- Mache ein „Browse Projects“ (eclipseList).
- lese eine belibige Datei
- Sag im Chat „Hallo“.
- Rufe dann `compactSession` auf. Der Compact-Agent soll als State erhalten: „Hallo von Paul“ plus die Instruktion aus Schritt 2.

- Nach dem Compact: 
  - Mache ein „Browse Projects“ (eclipseList).
  - lese eine belibige Datei
  - führe Schritt 2 aus.

## 2. Da Mek (via askDev):
Anweisung für da Mek:
- Mache ein „Browse Projects“ (eclipseList).
- lese eine belibige Datei
- Sage „Hallo Compact Agent“.
- Rufe compactDev selbst auf und hinterlasse dir dabei als State die Anweisung: „Antworte mit ‚Hallo Jon‘“.
- Deine finale Antwort an mich muss exakt sein: „Hallo Jon“ + info ob Du vom compactSession tool eine zusammenfassung bekommen hast - echo diese als info.

## 3. Jon (du):
- Prüfe, ob Da Mek exakt mit „Hallo Jon“ geantwortet hat, und melde das Ergebnis.

Erfolgskriterium: Beide Compacts laufen durch, der State überlebt jeweils die Compaction, und Da Mek antwortet mit „Hallo Jon“.