# Open Points — AI Factory

Status: ❓ offen · ⏳ selbst entschieden (Rückversicherung steht aus) · 🔒 geklärt.
Durchgangslager: jeder Punkt wandert am Zyklusende in ein Feature-Doc oder entfällt.

* **❓ Jons Team in der Web-App** (2026-10-25) — „nur Jon" heißt: kein Agent-Auswahl-UI. Bleiben
  Jons Delegationen trotzdem erhalten (Da Thinka/Da Mek/Da Dok über `talkPlan`/`planWithPlanAgent`/
  `askDev`/`buildWithDev`, Da Dok-Review, Da Sniffa via `searchAgent`)? Das entscheidet, ob die
  Factory die Sklaven-Komposition und deren Model-Slots mitbauen muss. Rückfrage steht aus.
* **❓ Was ist „Workspace" in der Web-App?** — Die Disk-Tools brauchen einen definierten
  Arbeitsordner je Session/Instanz; der Plugin-Begriff (Eclipse-Workspace, gewähltes Projekt) gilt
  hier nicht. Wird mit dem ersten Feature (Session/Arbeitsordner) spezifiziert.
* **❓ Konfiguration & Secrets im Web** — Modell-Provider, API-Keys und `~/.peon`-Config sind heute
  Plugin-Config (`LlmConfigStore`, Preference-Pages). Server-seitige Config und Mehrbenutzer-Frage
  sind offen, bewusst noch nicht entschieden.
