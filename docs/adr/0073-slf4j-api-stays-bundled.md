# ADR-0073: slf4j-api bleibt gebündelt (keine OSGi-Platform-Resolution)

**Status:** Accepted (2026-10-23)

## Context

Nach dem Entfernen von slf4j-simple (2026-10-17, resolved-points „SLF4J Multiple Providers") blieb
als Design-Punkt: slf4j-api ist nicht OSGi-resolved, sondern als `lib/slf4j-api.jar` gebündelt —
Patch-Drift zur Platform-Version, theoretisch unnötiges Schleppen. Paul testete 2026-10-23 den
Platform-Resolve: `Require-Bundle: slf4j.api;bundle-version="[2.0.0,3.0.0)"` + Clean Break
(lib-Jar, Bundle-ClassPath, build.properties, .classpath, copy-deps/copy-sources-Whitelist).

**Ergebnis (belegt):** Kompletter slf4j-Logging-Ausfall — NOP-Logger für langchain4j UND den
eigenen core (Marker-Vergleich Runtime-Log: `HTTP request` 2→0, `Compact result` INFO 1→0,
Compressor DEBUG 2→0). Ursache (aus slf4j-api-Quelle): `LoggerFactory` suchtden Provider via
`ServiceLoader.load(SLF4JServiceProvider.class, classLoaderOfLoggerFactory)` — der Classloader
des API-Bundles. Vorher lagen API-Jar und `META-INF/services`-Registrierung von
`EclipseSlf4jProvider` im selben Bundle (ein Classloader → Fund); danach sieht der
Platform-ClassLoader die Host-Registrierung nie (Require-Bundle-Wiring ist gerichtet).

Gegenprobe ServiceLoader-Mediator: `slf4j.api` 2.0.18 fordert
`osgi.extender=osgi.serviceloader.processor` (Mandatory) und resolved im Runtime ⇒ Extender
ist aktiv; mit formatgleichem `Provide-Capability: osgi.serviceloader;…` im Host-MANIFEST
webt er die Provider-Resource trotzdem NICHT ein (Smoke 2026-10-23 15:53: `No SLF4J providers
were found` unverändert). Der zweite verbleibende OSGi-Weg wäre ein Fragment auf `slf4j.api`
(Provider-Klassen umziehen, Debug-Schalter kanalisieren, Feature/Update-Site) — nicht verfolgt.

## Decision

slf4j-api bleibt **gebündelt** (`lib/slf4j-api.jar` via copy-deps, deterministisch 2.0.18 im
Build — die „2.0.19"-Notiz in open-points war falsch). Der Provider bleibt
`EclipseSlf4jProvider` im Host-Bundle mit `META-INF/services`-Registrierung; slf4j-simple
bleibt draußen (2026-10-17). Paul hat den Require-Bundle-Eintrag wieder entfernt; Working
Tree ist ex-vorher-Stand (Head `4fe02f02`).

## Consequences

- Zwei `org.slf4j`-Kopien im Workbench (gebündelt + Platform) — akzeptiert, seit 2026-10-17
  ohnehin der Stand; Kollision ist die „Multiple Providers"-Warnung, die nur bei zwei
  **Providern** auftritt, nicht bei API+Provider im eigenen Bundle.
- Patch-Drift API-Jar vs. Platform (2.0.18) — harmlos, deterministisch per Build.
- Platform-eigene slf4j-Nutzer (JDT/Equinox) bleiben von unserem Provider getrennt — kein
  Gemeinschafts-Log-Kanal, war auch vorher so.
- Revisit nur mit echtem Anlass (z. B. Versionskonflikt mit einer Platform-Abhängigkeit):
  dann Fragment-Ansatz (ADR-tragend), nicht Require-Bundle/Mediator — beides negativ bewiesen.
