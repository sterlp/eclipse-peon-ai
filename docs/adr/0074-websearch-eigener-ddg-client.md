# ADR-0074 — webSearch: eigener dünner DDG-Client statt Community-Modul

**Status:** akzeptiert (2026-10-23, Jon — Nachtzyklus Story 1)

**Context:** R-WEB-10 plante die DuckDuckGo-Suche über das Community-Modul
`langchain4j-community-web-search-engine-duckduckgo` (1.22.0-beta32, passend zu langchain4j 1.22.0).
Kernanforderung des Tools ist Ehrlichkeit (R-WEB-12: 0 Treffer ist eine Meldung, Fehler von
Leerergebnissen unterscheidbar, „showing N of M").

**Decision:** eigener dünner DDG-HTTP-Client im core, der das langchain4j
`WebSearchEngine`-Interface implementiert — kein Community-Modul.

**Consequences:**
- Das Modul erfüllt R-WEB-12 strukturell nicht: erfindet bei 0 Treffern einen Treffer,
  schluckt alle Exceptions (Rate-Limit ununterscheidbar von „0 Treffer"),
  `totalResults = size()` nach Limit → „showing N of M" unmöglich.
- Das Modul zieht jsoup 1.23.2 neu rein (nicht in `~/.m2`, Offline-Build bricht; neues JAR +
  OSGi-Manifest-Frage). Eigener Client nutzt jsoup 1.15.4, die bereits da ist (flexmark) —
  null neue JARs, null OSGi-Änderung.
- Späterer Wechsel auf das Modul bleibt möglich (Interface-Implementierung trennt
  Tool von Transport).

**Fallstricke (im Bau 2026-10-23 verifiziert, gegen Vergessen):**
- JDK `HttpClient.send` wirft `InterruptedException`, wenn das Interrupt-Flag schon **vor** dem
  Call gesetzt ist — der Interrupt-Guard ist kein toter Zweig.
- Redirect-Loop wird vom JDK-Client nach 5 Hops als 302-Response zurückgegeben (keine Exception)
  → Engine braucht einen eigenen „unexpected response (HTTP n)"-Zweig.
- 302 ohne Location verlässt den JDK-Client als `IOException: Invalid redirection` → Netzfehler-Zweig,
  kein eigener Case nötig.
