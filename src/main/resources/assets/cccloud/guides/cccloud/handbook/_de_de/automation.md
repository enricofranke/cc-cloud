---
# Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.
navigation:
  title: "Regeln, Benachrichtigungen & Verlauf"
  position: 7
---

# Regeln, Benachrichtigungen und Verlauf

Automatisierung, die auf dem **Server** läuft – ohne Browser und ohne Computerprogramm –, dazu Meldungen aufs Handy und Langzeit-Diagramme.

## Regeln

Öffne **Rules** in den Dashboard-Einstellungen (⚙). Eine Regel liest sich wie ein Satz: **Wenn** etwas passiert, **falls** Bedingungen gelten, **dann** tue etwas.

| Teil | Möglichkeiten |
|---|---|
| Wenn | ein Widget-Wert überschreitet eine Zahl, eine Itemanzahl in einer Lager-Karte, Mainframe-Strom in %, eine Karte geht offline, alle N Minuten, eine Uhrzeit (im Spiel) |
| Falls (optional, alle müssen gelten) | Widget-Wert, Itemanzahl, Strom, eine Karte ist online |
| Dann (1–10, der Reihe nach) | eine Redstone-Seite eines Computers setzen, ein Bedienelement nutzen (Button, Schalter, Regler, Auswahl, Zahl, Text), ein Item craften, eine Meldung senden, das Dashboard ein-/ausschalten |

- Wert-Auslöser feuern **einmal**, wenn die Prüfung wahr wird, nicht erneut, solange sie wahr bleibt. Die **Abklingzeit** (mindestens 5 s) ist der Mindestabstand zwischen zwei Läufen.
- **Run now** führt die Aktionen einmal aus und ignoriert Auslöser, Bedingungen und Abklingzeit – praktisch zum Testen.
- Sicherheit: Eine Regel darf nicht das Widget ändern, das ihr eigener Auslöser beobachtet (das wäre eine Schleife), PIN-geschützte Bedienelemente sind nicht erlaubt, und höchstens 20 Regelläufe pro Sekunde und Mainframe. Jeder Lauf wird protokolliert (**Recent runs**).
- Wie viele Regeln es geben darf, hängt vom **Memory**-Upgrade des Mainframes ab (8 ohne, dann 24 / 64 / 128).
- Rechte: „Edit rules“ zum Anlegen, Ändern und Löschen; „Enable rules“ zum Ein-/Ausschalten und für **Run now** (haben Operatoren).
- Lua: `cloud_mainframe.getRules()` und `setRuleEnabled(id, enabled)`.

**Beispiel:** Wenn *Core temperature (Fission Reactor)* über 900 – dann Redstone hinten am *Reaktor-Computer* auf 15 und Meldung „Reaktor heiß“ (Warnung).

## Push-Benachrichtigungen

Öffne **Notifications** in den Dashboard-Einstellungen. Bis zu 5 Ziele pro Mainframe:

- **Discord:** in den Kanaleinstellungen → Integrationen → Webhooks → Neuer Webhook → URL kopieren.
- **ntfy:** eine Topic-URL wie `https://ntfy.sh/meine-basis-alarme` (langen, schwer zu ratenden Namen wählen oder ein Token nutzen); dasselbe Topic in der ntfy-App abonnieren.
- **Webhook:** jeder https-Endpunkt; er bekommt einen JSON-POST (event, mainframe, title, text, level, time).

Wähle die Mindeststufe (alles, Warnungen und Fehler oder nur Fehler) und die Ereignisse: **alerts** (Skripte, Karten, Crafting, Regeln), **rules** (Läufe von Regeln mit „push every run“), **approvals** (ein Browser wartet auf Freigabe) und **power** (kein Strom / wieder da). **Send test** prüft ein Ziel; das Ergebnis steht daneben.

Links und Tokens liegen nur auf dem Server – das Dashboard zeigt sie maskiert. Der Server sendet nur an öffentliche https-Adressen (kein lokales Netz, keine Weiterleitungen), höchstens 30 Nachrichten pro Minute und Mainframe. Braucht das Recht „Push targets“ (Admins).

## Langzeit-Verlauf

Widgets, deren Skript einen Verlauf anfordert (`history="24h"` in den Widget-Optionen, oder `"1h"`, `"7d"`, `"30d"`), bekommen einen kleinen **Diagramm-Button**. Er öffnet den Verlauf mit Zeitraum-Wahl (1 h, 24 h, 7 Tage, 30 Tage), Min / Durchschnitt / Max und dem Wert unter Finger oder Maus; Lücken bedeuten, dass Mainframe oder Chunk nicht geladen waren.

```lua
cloud.gauge("temp", "Core temperature", { min = 0, max = 1200, unit = "K", history = "7d" })
```

Wie viel der Server aufbewahrt, hängt vom **Memory**-Upgrade ab: 1 h ohne, 24 h / 7 Tage / 30 Tage mit den Stufen. **Export CSV** lädt die Werte (Zeit, Wert) für Tabellen herunter – braucht das Recht „Use controls“.

## Terminal

Der Button **>_** auf der Karte eines Computers öffnet sein **Terminal** im Browser: der Bildschirm live (Farben bei Advanced-Computern) und deine Tastatur, als stündest du davor. Auf den Bildschirm klicken und tippen; **Terminate** ist Strg+T, **Reboot**, **Shut down** und **Turn on** steuern den Computer. Einfügen schickt die erste Zeile der Zwischenablage. Auf dem Handy ins Feld unter dem Bildschirm tippen und **Enter** drücken.

Braucht das Recht „Terminal“ (Admins) und die Mainframe-PIN, falls eine gesetzt ist; das Öffnen wird protokolliert. Der Server-Admin kann es abschalten (`terminalEnabled`) oder Skripte und Terminal für einen Spieler verbieten (`cccloud.feature.scripts`).

## Lua-Editor

Im **\</>**-Fenster eines Computers zeigt **Open the editor** alle Dateien und Ordner (bis 8 Ebenen) neben einem Code-Editor mit Lua-Hervorhebung, Zeilennummern, Suche (Strg/⌘ + F) und Rückgängig. **Save** (Strg/⌘ + S) speichert; **Save & run** startet die Datei zusätzlich in einem neuen Tab auf dem Computer. **+ File**, **+ Folder**, Umbenennen/Verschieben (✎) und Löschen (×) arbeiten im Dateibaum.

Wurde die Datei seit dem Öffnen auf dem Computer geändert, fragt das Speichern vor dem Überschreiben. Dateien sind auf 256 KB begrenzt, `rom` und Disketten werden nie angefasst, und der Editor braucht dieselben Rechte wie das Hochladen von Skripten (plus die PIN, falls gesetzt).

