---
idPrefix: WP
---
# Nutzer — Name statt Login

## Ziel

Ohne Login wissen, wer spricht: ein Name je Browser ist die Identität für alles Nutzerbezogene und
erscheint bei Jon, Paco und allen Betrachtern des Chats.

## Regeln

### R-WP-17 — Nutzer ohne Login, aber mit Namen ❌

Der Prototyp authentifiziert nicht. Ein Nutzer gibt einmal einen **Namen** an (Browser-Speicher,
kein Passwort); der Name ist die Identität für alles Nutzerbezogene (Paco, Vorschlags-Autor). Ohne
Namen gibt es keinen Nutzer-bezogenen Bereich.

#### UC-WP-25 — userNameIsTheIdentity ❌
- GIVEN ein frischer Browser WHEN der Nutzer seinen Namen einträgt THEN ist er für diese der
  identische Nutzer (Paco, Vorschläge); ein anderer Browser mit anderem Namen ist ein anderer
  Nutzer.

### R-WP-25 — Jon und Paco wissen, wer spricht ❌

Ist ein Name bekannt (R-WP-17), beginnt jede Nachricht an Jon und an Paco mit `[User <Name>]: `; ohne
Namen gibt es kein Präfix. Der Chat zeigt den Namen statt „YOU" — für **alle** Betrachter, der Jon-Chat
ist geteilt; ohne Namen „YOU". Der Name steht damit im Verlauf und überlebt einen Neustart
([R-WP-11](chat.md)). Der Prompt der Fabrik erklärt das Präfix in einer Zeile (nur Fabrik, nicht
Plugin). **WEIL** Jon im geteilten Chat sonst die Wünsche mehrerer Nutzer vermischt.

#### UC-WP-48 — messageCarriesUserName ❌
- GIVEN Paul und Anna im selben Projekt WHEN Anna „bitte X" schreibt THEN sieht Jon `[User Anna]: bitte X`
  und beide sehen im Chat „Anna" als Absender.
- GIVEN kein Name gesetzt WHEN der Nutzer schreibt THEN sieht Jon den Text ohne Präfix und der Chat
  zeigt „YOU".
- GIVEN ein Neustart WHEN der Verlauf gelesen wird THEN steht an Annas Zeile weiterhin „Anna".
